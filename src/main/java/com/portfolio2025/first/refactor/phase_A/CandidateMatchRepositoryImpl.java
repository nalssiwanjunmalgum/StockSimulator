package com.portfolio2025.first.refactor.phase_A;

import com.portfolio2025.first.domain.stock.StockOrderStatus;
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
        return em.createQuery("""
            select so.id
            from StockOrder so
            where so.stock.id = :stockId
              and so.stockOrderStatus = :pending
              and so.remainedQuantity.quantityValue > 0
              and so.requestedPrice.moneyValue <= :buyPrice
            order by so.requestedPrice.moneyValue asc, so.createdAt asc, so.id asc
            """, Long.class)
                .setParameter("stockId", stockId)
                .setParameter("pending", StockOrderStatus.PENDING)
                .setParameter("buyPrice", buyPrice)
                .setMaxResults(limit)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
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
}
