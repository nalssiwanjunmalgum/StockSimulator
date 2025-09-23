package com.portfolio2025.first.refactor.phase_A.orders.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.*;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {

    private Long orderId;              // orders.id
    private String status;             // "CREATED" or "PENDING"
    private OffsetDateTime acceptedAt;
    private Summary summary;
    private Links links;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private Long portfolioId;
        private String stockCode;
        private CreateOrderRequest.Side side;
        private CreateOrderRequest.PriceType priceType;
        private Long quantity;

        @JsonInclude(Include.NON_NULL)
        private Long limitPrice;

        @JsonInclude(Include.NON_NULL)
        private Long estimatedTotalPrice; // LIMIT이면 limitPrice*quantity
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Links {
        private String self;
        private String cancel;
    }
}
