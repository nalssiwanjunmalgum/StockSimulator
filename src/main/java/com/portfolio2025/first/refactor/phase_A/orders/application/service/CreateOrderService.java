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
    private final CheckIdempotencyPort checkIdempotencyPort;
    private final PublishOrderEventPort publishOrderEventPort;


    @Override
    @Transactional
    public CreatedOrderResult createOrder(CreateOrderCommand cmd) {
        // 0) 멱등 체크
        if (cmd.getClientOrderId() != null) {
            Existing ex = checkIdempotencyPort.findExistingByClientOrderId(cmd.getClientOrderId());
            if (ex != null) return CreatedOrderResult.duplicateIgnored(ex.orderId(), ex.stockOrderId());
        }

        Portfolio portfolio = loadPortfolioPort.get(cmd.getPortfolioId(), cmd.getUserId());
        Stock stock = loadStockPort.get(cmd.getStockId());

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

        long savedOrder = saveOrderPort.saveNewOrder(order);
        long savedSo = saveStockOrderPort.saveNewStockOrder(so);

        if (cmd.getClientOrderId() != null) {
            checkIdempotencyPort.record(cmd.getClientOrderId(), savedOrder, savedSo);
        }
        publishOrderEventPort.publishAccepted(savedOrder, savedSo, Instant.now(), cmd.getRequestId());

        return CreatedOrderResult.accepted(savedOrder, savedSo, Instant.now());
    }
}
