package com.portfolio2025.first.refactor.phase_A.orders.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.in.CreateOrderUseCase.CreateOrderCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull @Positive
    private Long portfolioId;

    @NotNull
    private String stockCode;

    @NotNull
    private Side side;          // BUY | SELL

    @NotNull
    private PriceType priceType; // LIMIT | MARKET

    @NotNull @Positive
    private Long quantity;

    // LIMIT일 때만 사용. MARKET이면 null이어야 함.
    @JsonInclude(Include.NON_NULL)
    private Long limitPrice;

    // 선택: 클라이언트 추적/멱등 보조용
    private String clientOrderId;

    @AssertTrue(message = "For LIMIT orders, limitPrice is required and > 0; for MARKET, limitPrice must be null")
    public boolean isPriceTypeConsistent() {
        // priceType/limitPrice가 null일 수 있으므로 널-세이프하게
        if (priceType == null) return true; // @NotNull이 별도로 잡아줌
        if (priceType == PriceType.LIMIT) {
            return limitPrice != null && limitPrice > 0;
        }
        // MARKET
        return limitPrice == null;
    }

    public enum Side { BUY, SELL }
    public enum PriceType { LIMIT, MARKET }

    // 내부 유스케이스 커맨드로 매핑(필요 시 userId, tif, requestId 등 추가)
    public CreateOrderCommand toCommand(Long userId, CreateOrderCommand.TimeInForce tif, String requestId) {

        // stockCode -> stockId
        return CreateOrderCommand.builder()
                .userId(userId)
                .portfolioId(portfolioId)
                .stockCode(stockCode)          // stockCode→stockId는 애플리케이션/어댑터에서 해석
                .side(side == Side.BUY ? CreateOrderCommand.OrderSide.BUY : CreateOrderCommand.OrderSide.SELL)
                .orderType(priceType == PriceType.LIMIT ? CreateOrderCommand.OrderType.LIMIT : CreateOrderCommand.OrderType.MARKET)
                .quantity(java.math.BigDecimal.valueOf(quantity))
                .limitPrice(limitPrice == null ? null : java.math.BigDecimal.valueOf(limitPrice))
                .tif(tif)
                .clientOrderId(clientOrderId)
                .requestId(requestId)
                .build();
    }

    // --- 분기 검증: LIMIT ↔ limitPrice ---
    @AssertTrue(message = "limitPrice is required for LIMIT orders")
    public boolean isLimitHasPrice() {
        if (priceType == PriceType.LIMIT) {
            return limitPrice != null && limitPrice > 0;
        }
        return true;
    }

    @AssertTrue(message = "limitPrice must be null for MARKET orders")
    public boolean isMarketHasNoPrice() {
        if (priceType == PriceType.MARKET) {
            return limitPrice == null;
        }
        return true;
    }
}
