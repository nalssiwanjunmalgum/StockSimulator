package com.portfolio2025.first.refactor.phase_A.orders.adapter.out.jpa;

import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.idempotency.IdempotencyPort;
import com.portfolio2025.first.refactor.phase_A.orders.domain.idempotency.IdempotencyKey;
import jakarta.validation.ConstraintViolationException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class IdempotencyJpaAdapter implements IdempotencyPort {

    private final IdempotencyKeyJpaRepository repo;
    private final IdempotencyTx idempotencyTx;

    @Override
    public ClaimResult tryClaim(Long userId, Long portfolioId, String clientOrderId, String payloadHash) {
        // 1) 먼저 존재 여부 조회(선택: 성능 위해 생략 가능, 바로 save 시도 후 예외 캐치)
//        Optional<IdempotencyKey> existing = repo.findByUserIdAndPortfolioIdAndClientOrderId(userId, portfolioId, clientOrderId);
//        if (existing.isPresent()) return ClaimResult.CLAIM_CONFLICT;

        // 2) 새 PENDING 행 삽입 시도
        try {
            idempotencyTx.claimOrThrow(userId, portfolioId, clientOrderId, payloadHash);
            return ClaimResult.CLAIM_SUCCESS;
        } catch (DataIntegrityViolationException | ConstraintViolationException e) {
            // 동시경합 시 UNIQUE 제약 위반 → 충돌로 간주
            return ClaimResult.CLAIM_CONFLICT;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Existing> findExisting(Long userId, Long portfolioId, String clientOrderId) {
        return repo.findByUserIdAndPortfolioIdAndClientOrderId(userId, portfolioId, clientOrderId)
                .map(k -> new Existing(k.getStatus(), k.getOrderId(), k.getStockOrderId(), k.getPayloadHash()));
    }

    @Override
    @Transactional
    public void complete(Long userId, Long portfolioId, String clientOrderId, Long orderId, Long stockOrderId) {
        // 강한 일관성을 원하면 forUpdate 사용
        IdempotencyKey key = repo.findForUpdate(userId, portfolioId, clientOrderId)
                .orElseThrow(() -> new IllegalStateException("Idempotency key not found on complete. key=" + clientOrderId));
        key.complete(orderId, stockOrderId);
    }

    @Override
    @Transactional
    public void fail(Long userId, Long portfolioId, String clientOrderId) {
        repo.findForUpdate(userId, portfolioId, clientOrderId)
                .ifPresent(IdempotencyKey::fail);
    }
}
