package com.portfolio2025.first.legacy.service;

import com.portfolio2025.first.legacy.OrderValidator;
import com.portfolio2025.first.legacy.domain.Order;
import com.portfolio2025.first.legacy.domain.stock.StockOrder;
import com.portfolio2025.first.legacy.dto.event.OrderCreatedEvent;
import com.portfolio2025.first.legacy.exception.NonRetryableException;
import com.portfolio2025.first.legacy.exception.RetryableException;
import com.portfolio2025.first.legacy.repository.OrderRepository;
import jakarta.persistence.QueryTimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderPrepareService {

    private final OrderRepository orderRepository;
    private final OrderValidator orderValidator; // 도메인 규칙 검증기 (있다면)

    public Order fetchAndValidateOrThrow(OrderCreatedEvent event) {

        try {
            // 1) 조회 — 필요 필드만 묶어 한 번에 (지연로딩/가시성 이슈 최소화)
            Long orderId = event.getOrderId();
            Order order = orderRepository.findByIdWithStockOrders(orderId)
                    .orElseThrow(() -> new NonRetryableException("Order not found: " + orderId));

            // 2) 도메인 검증 — 규칙 위반은 NonRetryable로
            for(StockOrder stockOrder : order.getStockOrders()){
                orderValidator.validate(stockOrder);
            }
            return order;

        } catch (TransientDataAccessException | QueryTimeoutException e) {
            // DB 일시 장애/락 경합/타임아웃 → 재시도
            throw new RetryableException("DB transient issue", e);

        } catch (IllegalStateException | IllegalArgumentException e) {
            // 비즈니스 규칙 위반 → 재시도 무의미
            throw new NonRetryableException("Business rule violation: " + e.getMessage(), e);
        }
    }
}
