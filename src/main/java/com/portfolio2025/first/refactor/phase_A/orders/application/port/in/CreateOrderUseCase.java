package com.portfolio2025.first.refactor.phase_A.orders.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

public interface CreateOrderUseCase {
    CreatedOrderResult createOrder(CreateOrderCommand command);

    // ===== DTOs =====
    @Value // 불변 객체 (getter 만 제공한다), 모든 필드가 알아서 private final을 붙여준다
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    class CreateOrderCommand {
        Long userId;
        Long portfolioId;
        Long stockId;
        OrderSide side;
        OrderType orderType;
        BigDecimal quantity; // 원시적인 값 형태로 받기
        BigDecimal limitPrice;  // MARKET이면 null 가능
        TimeInForce tif;
        String clientOrderId;   // 중복 방지 키 (멱등성 키 idempotence-key)
        String requestId;       // 트레이싱용 (이건 잘 모르겠고) - 외부에서 제공하는 정보 (Logging)

        public enum OrderSide { BUY, SELL }
        public enum OrderType { MARKET, LIMIT }
        public enum TimeInForce { GTC, IOC, FOK }
    }

    @Value
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    class CreatedOrderResult {
        Long orderId;
        Long stockOrderId;
        OrderAcceptStatus status;
        Instant acceptedAt;

        public static CreatedOrderResult duplicateIgnored(Long orderId, Long stockOrderId) {
            return CreatedOrderResult.builder()
                    .orderId(orderId)
                    .stockOrderId(stockOrderId)
                    .status(OrderAcceptStatus.DUPLICATE_IGNORED)
                    .acceptedAt(null)
                    .build();
        }

        public static CreatedOrderResult accepted(Long orderId, Long stockOrderId, Instant now) {
            return CreatedOrderResult.builder()
                    .orderId(orderId)
                    .stockOrderId(stockOrderId)
                    .status(OrderAcceptStatus.ACCEPTED)
                    .acceptedAt(now)
                    .build();
        }

        public enum OrderAcceptStatus { ACCEPTED, DUPLICATE_IGNORED }
    }
}
