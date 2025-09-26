package com.portfolio2025.first.refactor.phase_A.orders.adapter.out.jpa;

import com.portfolio2025.first.refactor.phase_A.orders.domain.idempotency.IdempotencyKey;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IdempotencyTx {

    @Autowired IdempotencyKeyJpaRepository repo;

    private static final Duration PENDING_TTL = Duration.ofMinutes(5);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claimOrThrow(Long userId, Long portfolioId, String clientOrderId, String payloadHash) {
        IdempotencyKey key = IdempotencyKey.pending(userId, portfolioId, clientOrderId, payloadHash, PENDING_TTL);
        repo.saveAndFlush(key); // 중복이면 여기서 예외 발생 → 밖으로 던짐
    }
}

