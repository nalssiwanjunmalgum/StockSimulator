package com.portfolio2025.first.refactor.phase_A.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
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

    public enum Side { BUY, SELL }
    public enum PriceType { LIMIT, MARKET }
}
