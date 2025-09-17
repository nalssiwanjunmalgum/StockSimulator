package com.portfolio2025.first.legacy;

import com.portfolio2025.first.legacy.domain.Order;
import com.portfolio2025.first.legacy.domain.stock.StockOrder;
import com.portfolio2025.first.legacy.repository.OrderRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 주문 관련 검증을 관리하는 OrderValidator
 *
 * [07.30]
 *
 *
 * [고민]
 *
 *
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderValidator {
    private static final int MAX_RETRY = 5;
    private static final long RETRY_DELAY_MS = 200; // 200ms x 5 = 최대 1초 대기

    private final OrderRepository orderRepository;

    /**
     * 주문 ID로 조회하며 최대 5회 재시도
     */
    public Order findOrderWithRetry(Long orderId) throws InterruptedException {
        for (int i = 0; i < MAX_RETRY; i++) {
            Optional<Order> optional = orderRepository.findByIdWithStockOrders(orderId);
            if (optional.isPresent()) {
                log.info("✅ Order 조회 성공 (시도 {}): orderId={}", i + 1, orderId);
                return optional.get();
            }
            log.warn("🔁 Order 조회 재시도 {}회차: orderId={}", i + 1, orderId);
            Thread.sleep(RETRY_DELAY_MS);
        }
        throw new IllegalStateException("❌ Order not found after retries: orderId=" + orderId);
    }

    /**
     * StockOrder에 대한 유효성 검증 (null, 수량 등 체크)
     */
    public void validate(StockOrder stockOrder) {
        if (stockOrder == null) {
            throw new IllegalArgumentException("StockOrder is null");
        }

        if (stockOrder.getRequestedQuantity() == null || stockOrder.getRequestedQuantity().isZero()) {
            throw new IllegalStateException("수량이 없거나 0입니다: stockOrderId=" + stockOrder.getId());
        }

        if (stockOrder.getStock() == null) {
            throw new IllegalStateException("종목 정보가 없습니다: stockOrderId=" + stockOrder.getId());
        }
    }
}
