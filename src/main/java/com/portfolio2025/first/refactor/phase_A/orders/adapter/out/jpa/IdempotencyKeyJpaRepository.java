package com.portfolio2025.first.refactor.phase_A.orders.adapter.out.jpa;

import com.portfolio2025.first.refactor.phase_A.orders.domain.idempotency.IdempotencyKey;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByUserIdAndPortfolioIdAndClientOrderId(Long userId, Long portfolioId, String clientOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from IdempotencyKey k where k.userId=:userId and k.portfolioId=:portfolioId and k.clientOrderId=:clientOrderId")
    Optional<IdempotencyKey> findForUpdate(@Param("userId") Long userId,
                                           @Param("portfolioId") Long portfolioId,
                                           @Param("clientOrderId") String clientOrderId);
}

