package com.portfolio2025.first.refactor.phase_A.orders.application.service;

import static com.portfolio2025.first.refactor.phase_A.orders.application.port.in.CreateOrderUseCase.CreateOrderCommand.OrderSide.BUY;
import static com.portfolio2025.first.refactor.phase_A.orders.application.port.in.CreateOrderUseCase.CreateOrderCommand.OrderType.LIMIT;

import com.portfolio2025.first.legacy.domain.Order;
import com.portfolio2025.first.legacy.domain.Portfolio;
import com.portfolio2025.first.legacy.domain.PortfolioStock;
import com.portfolio2025.first.legacy.domain.order.OrderType;
import com.portfolio2025.first.legacy.domain.stock.Stock;
import com.portfolio2025.first.legacy.domain.stock.StockOrder;
import com.portfolio2025.first.legacy.domain.vo.Money;
import com.portfolio2025.first.legacy.domain.vo.Quantity;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.in.CreateOrderUseCase;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.event.PublishOrderEventPort;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.idempotency.CheckIdempotencyPort;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.idempotency.CheckIdempotencyPort.Existing;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.idempotency.IdempotencyPort;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.idempotency.IdempotencyPort.ClaimResult;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.persistence.LoadPortfolioPort;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.persistence.LoadStockPort;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.persistence.SaveOrderPort;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.persistence.SaveStockOrderPort;
import jakarta.transaction.Transactional;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateOrderService implements CreateOrderUseCase {

    private final LoadPortfolioPort loadPortfolioPort;
    private final LoadStockPort loadStockPort;
    private final SaveOrderPort saveOrderPort;
    private final SaveStockOrderPort saveStockOrderPort;
    private final IdempotencyPort idempotencyPort;
    private final PublishOrderEventPort publishOrderEventPort;


    @Override
    @Transactional
    public CreatedOrderResult createOrder(CreateOrderCommand cmd) {
        // 이중으로 검증할지 생각하기
        validate(cmd);
        // 값 뽑아두고
        final Long userId = cmd.getUserId();
        final Long portfolioId = cmd.getPortfolioId();
        final String clientOrderId = cmd.getClientOrderId();
        final String payloadHash = buildPayloadHash(cmd); // or cmd.getPayloadHash()

        // 0) 멱등 선점(UNIQUE로 동시성 제어)
        if (clientOrderId != null) {
            IdempotencyPort.ClaimResult claim = idempotencyPort.tryClaim(
                    userId, portfolioId, clientOrderId, payloadHash);

            if (claim == IdempotencyPort.ClaimResult.CLAIM_CONFLICT) {
                // 이미 처리(or 진행중)된 동일 요청 → 기존 결과 조회 후 중복 무시로 반환
                return idempotencyPort.findExisting(userId, portfolioId, clientOrderId)
                        .map(ex -> CreatedOrderResult.duplicateIgnored(ex.orderId(), ex.stockOrderId()))
                        .orElseGet(() -> CreatedOrderResult.duplicateIgnored(null, null));
            }
        }

        // 1) 포트폴리오/종목 로딩
        Portfolio portfolio = loadPortfolioPort.get(portfolioId, userId);
        Stock stock = loadStockPort.getFromStockCode(cmd.getStockCode());

        // 1) 가격/수량 매핑 (네 도메인: Money/Quantity는 Long 기반)
        Quantity q = new Quantity(cmd.getQuantity().longValueExact());

        Money price = (cmd.getOrderType() == LIMIT)
                ? new Money(cmd.getLimitPrice().longValueExact())
                : stock.getStockPrice(); // MARKET이면 시세 사용(또는 추정가 정책)

        // 2) 예약/검증
        if (cmd.getSide() == BUY) {
            Money total = price.multiply(q);
            portfolio.reserveAndDeductCash(total);
        } else { // SELL
            // 보유종목 찾아 예약
            PortfolioStock ps = portfolio.getPortfolioStocks().stream()
                    .filter(s -> s.getStock().getId().equals(stock.getId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("보유 종목 없음"));
            ps.reserve(q);
        }

        // 3) StockOrder/Order 생성 (양방향 편의 메서드 활용)
        StockOrder so = StockOrder.createStockOrder(stock, q, price, portfolio);
        Order order = Order.createSingleOrder(portfolio, so,
                cmd.getSide() == BUY ? OrderType.BUY : OrderType.SELL,
                price.multiply(q));

        long savedOrderId = saveOrderPort.saveNewOrder(order);
        long savedSoId = saveStockOrderPort.saveNewStockOrder(so);

        // event 처리 (추후 비동기 예측)
        if (clientOrderId != null) {
            idempotencyPort.complete(userId, portfolioId, clientOrderId, savedOrderId, savedSoId);
        }
        publishOrderEventPort.publishAccepted(savedOrderId, savedSoId, Instant.now(), cmd.getRequestId());

        return CreatedOrderResult.accepted(savedOrderId, savedSoId, Instant.now());
    }

    /** idempotency payload hash 생성 규칙(고정 규칙을 추천) */
    private String buildPayloadHash(CreateOrderCommand cmd) {
        // 원래는 팀 표준에 맞게 진행하라고 나와있긴 했다.
        // 예시: side|stockCode|orderType|qty|limitPrice(옵션)

        String base = String.join("|",
                String.valueOf(cmd.getSide()),
                cmd.getStockCode(),
                String.valueOf(cmd.getOrderType()),
                cmd.getQuantity().toPlainString(),
                cmd.getLimitPrice() != null ? cmd.getLimitPrice().toPlainString() : "");
        return Integer.toHexString(base.hashCode()); // 실제로는 SHA-256 권장
    }

    // NPE를 방지하는 검증 메서드
    private void validate(CreateOrderCommand cmd) {
        if (cmd == null) throw new IllegalArgumentException("요청이 null입니다.");
        if (cmd.getPortfolioId() == null) throw new IllegalArgumentException("portfolioId가 필요합니다.");
        if (cmd.getUserId() == null) throw new IllegalArgumentException("userId가 필요합니다.");
        if (cmd.getStockCode() == null || cmd.getStockCode().isBlank()) throw new IllegalArgumentException("stockCode가 필요합니다.");
        if (cmd.getSide() == null) throw new IllegalArgumentException("side가 필요합니다.");
        if (cmd.getOrderType() == null) throw new IllegalArgumentException("orderType이 필요합니다.");

        // 수량 검증
        if (cmd.getQuantity() == null) throw new IllegalArgumentException("quantity가 필요합니다.");
        if (cmd.getQuantity().signum() <= 0) throw new IllegalArgumentException("quantity는 양수여야 합니다.");

        // 지정가일 경우 가격 검증
        if (cmd.getOrderType() == CreateOrderCommand.OrderType.LIMIT) {
            if (cmd.getLimitPrice() == null) throw new IllegalArgumentException("지정가 주문은 limitPrice가 필요합니다.");
            if (cmd.getLimitPrice().signum() <= 0) throw new IllegalArgumentException("limitPrice는 양수여야 합니다.");
        }
    }
}
