package com.portfolio2025.first.refactor.phase_A.orders.application.port.out.idempotency;

import com.portfolio2025.first.refactor.phase_A.orders.domain.idempotency.IdempotencyStatus;
import java.util.Optional;

public interface IdempotencyPort {

    enum ClaimResult { CLAIM_SUCCESS, CLAIM_CONFLICT }

    /** 선점 시도: UNIQUE 제약으로 동시 중복을 차단 */
    ClaimResult tryClaim(Long userId, Long portfolioId, String clientOrderId, String payloadHash);

    /** 기존 키 조회 (COMPLETED/PENDING/FAILED 등 상태와 결과 참조 반환) */
    Optional<Existing> findExisting(Long userId, Long portfolioId, String clientOrderId);

    /** 처리 완료 기록 (PENDING -> COMPLETED) */
    void complete(Long userId, Long portfolioId, String clientOrderId, Long orderId, Long stockOrderId);

    /** 실패/만료 표시(선택) */
    void fail(Long userId, Long portfolioId, String clientOrderId);

    /** 서비스에서 재사용할 얇은 DTO */
    record Existing(IdempotencyStatus status, Long orderId, Long stockOrderId, String payloadHash) {}
}
