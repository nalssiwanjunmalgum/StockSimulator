package com.portfolio2025.first.refactor.phase_A.api;

import com.portfolio2025.first.refactor.phase_A.api.dto.CreateOrderRequest;
import com.portfolio2025.first.refactor.phase_A.api.dto.CreateOrderResponse;
import lombok.AllArgsConstructor;
import lombok.Data;

public interface OrderCommandService {
    // 멱등성 체크 - stockCode 확인 - 주문/주문상세 저장 - 응답(결과) DTO 반환
    CreateResult create(CreateOrderRequest request, String idempotencyKey);

    @Data
    @AllArgsConstructor
    class CreateResult {
        private CreateOrderResponse response; // Response 바디에 포함될 정보들
        private boolean created; // true: 신규 생성(201), false: 멱등 재요청(200)
    }
}
