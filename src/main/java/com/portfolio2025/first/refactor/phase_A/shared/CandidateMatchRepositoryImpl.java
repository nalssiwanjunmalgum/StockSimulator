package com.portfolio2025.first.refactor.phase_A.shared;

import com.portfolio2025.first.legacy.domain.stock.StockOrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CandidateMatchRepositoryImpl implements CandidateMatchRepository {
    private final EntityManager em;

    /**
     * 매수 요청 시, 매칭 가능한 매도 후보:
     * - 같은 종목
     * - 상태: PENDING
     * - 잔량 > 0
     * - 매도호가 <= 매수호가(buyPrice)
     * 정렬: 가격 ASC → 생성시각 ASC → id ASC
     * 락: PESSIMISTIC_WRITE
     */
    @Override
    public List<Long> lockSellCandidate(Long stockId, long buyPrice, int limit) {
        // 1) SKIP LOCKED로 집기+잠금
        @SuppressWarnings("unchecked")
        List<Number> raw = em.createNativeQuery("""
            SELECT so.id
            FROM stock_orders so
            WHERE so.stock_id = :sid
              AND so.stock_order_status = :pending
              AND so.remained_quantity > 0
              AND so.requested_price <= :buyPrice
            ORDER BY so.requested_price ASC, so.created_at ASC, so.id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """)
                .setParameter("sid", stockId)
                .setParameter("pending", StockOrderStatus.PENDING.name())
                .setParameter("buyPrice", buyPrice)
                .setParameter("limit", limit)
                .getResultList();

        List<Long> ids = raw.stream().map(Number::longValue).toList();

        if (ids.isEmpty()) return ids;

        // 2) 즉시 상태전환
        em.createNativeQuery("""
            UPDATE stock_orders
            SET stock_order_status = :processing
            WHERE id IN (:ids)
        """)
                .setParameter("processing", StockOrderStatus.PROCESSING.name())
                .setParameter("ids", ids)
                .executeUpdate();

        return ids;
    }

    @Override
    public List<Long> lockSellCandidate_NoSkip(Long stockId, long buyPrice, int limit) {
        @SuppressWarnings("unchecked")
        List<Number> raw = em.createNativeQuery("""
                    SELECT so.id
                      FROM stock_orders so
                     WHERE so.stock_id = :sid
                       AND so.stock_order_status = :pending
                       AND so.remained_quantity > 0
                       AND so.requested_price <= :buyPrice
                     ORDER BY so.requested_price ASC, so.created_at ASC, so.id ASC
                     LIMIT :limit
                     FOR UPDATE
                """)
                .setParameter("sid", stockId)
                .setParameter("pending", StockOrderStatus.PENDING.name())
                .setParameter("buyPrice", buyPrice)
                .setParameter("limit", limit)
                .getResultList();

        // 상태 업데이트는 의도적으로 하지 않음(락 대기 관찰 목적)
        return raw.stream().map(Number::longValue).toList();
    }

    /**
     * 매도 요청 시, 매칭 가능한 매수 후보:
     * - 같은 종목
     * - 상태: PENDING
     * - 잔량 > 0
     * - 매수호가 >= 매도호가(sellPrice)
     * 정렬: 가격 DESC → 생성시각 ASC → id ASC
     * 락: PESSIMISTIC_WRITE
     */
    @Override
    public List<Long> lockBuyCandidate(Long stockId, long sellPrice, int limit) {
        return em.createQuery("""
            select so.id
            from StockOrder so
            where so.stock.id = :stockId
              and so.stockOrderStatus = :pending
              and so.remainedQuantity.quantityValue > 0
              and so.requestedPrice.moneyValue >= :sellPrice
            order by so.requestedPrice.moneyValue desc, so.createdAt asc, so.id asc
            """, Long.class)
                .setParameter("stockId", stockId)
                .setParameter("pending", StockOrderStatus.PENDING)
                .setParameter("sellPrice", sellPrice)
                .setMaxResults(limit)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    // 재큐잉 정책 (임계 시간값을 정해두고, 이를 넘어가는 순간 특정 상태로 다시 변환하는 방식을 말함)
    @Override
    public int rescueStaleProcessingSecondsUsingUpdatedAt(int thresholds) {
        return em.createNativeQuery("""
                    UPDATE stock_orders
                       SET stock_order_status = 'PENDING',
                           updated_at = NOW()  -- 복구 시점 기록(선택)
                     WHERE stock_order_status = 'PROCESSING'
                       AND updated_at IS NOT NULL
                       AND TIMESTAMPDIFF(SECOND, updated_at, NOW()) >= :th
                """)
                .setParameter("th", thresholds)
                .executeUpdate();
    }
}
