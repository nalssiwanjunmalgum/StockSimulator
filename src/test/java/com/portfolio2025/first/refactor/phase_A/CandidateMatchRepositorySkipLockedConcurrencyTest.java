package com.portfolio2025.first.refactor.phase_A;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio2025.first.domain.Order;
import com.portfolio2025.first.domain.Portfolio;
import com.portfolio2025.first.domain.order.OrderType;
import com.portfolio2025.first.domain.stock.Stock;
import com.portfolio2025.first.domain.stock.StockOrder;
import com.portfolio2025.first.domain.vo.Money;
import com.portfolio2025.first.domain.vo.Quantity;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(CandidateMatchRepositoryImpl.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CandidateMatchRepositorySkipLockedConcurrencyTest {

    @Autowired
    Flyway flyway;
    @Autowired
    EntityManager em;
    @Autowired CandidateMatchRepository repo;
    @Autowired
    PlatformTransactionManager txm;

    Long stockId = 1L;

    @BeforeAll
    void initSchema() {
        flyway.clean();
        flyway.migrate();
    }

    @BeforeEach
    void seed() {
        TransactionTemplate t = new TransactionTemplate(txm);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW); // ★ 별도 트랜잭션
        t.execute(st -> {
            for (int i = 0; i < 3; i++) {
                makeSellOrder(stockId, 100_000, 5); // 내부에서 em.persist(...)
            }

            return null; // REQUIRES_NEW 트랜잭션이 여기서 COMMIT됨
        });

        em.clear(); // 캐시 정리(권장)
    }

    @Test
    void 동시에_집으면_B는_A가_잠근_행을_SKIP한다() throws Exception {

        CountDownLatch aPicked = new CountDownLatch(1); // A가 집었음을 알리는 신호
        CountDownLatch bDone   = new CountDownLatch(1); // B 실행이 끝났음을 알리는 신호

        var aIdsRef = new java.util.concurrent.atomic.AtomicReference<List<Long>>();
        var bIdsRef = new java.util.concurrent.atomic.AtomicReference<List<Long>>();

        Thread ta = new Thread(() -> {
            new TransactionTemplate(txm).execute(st -> {
                var rows = em.createNativeQuery("""
                  SELECT id, stock_order_status, remained_quantity, requested_price
                    FROM stock_orders
                   WHERE stock_id = :sid
                   ORDER BY created_at
                """).setParameter("sid", stockId).getResultList(); // 비어있는 걸 확인할 수 있었다.


                System.out.println("rows = " + rows);

                // A: 2건 선점 (SELECT … FOR UPDATE SKIP LOCKED) + 즉시 PROCESSING 전환 (동일 트랜잭션)
                List<Long> aIds = repo.lockSellCandidate(stockId, 100_000, 2);

                aIdsRef.set(aIds);
                aPicked.countDown(); // B 시작 허용

                // 커밋을 지연시켜 락을 유지 → B가 A 커밋 전 실행되도록 보장
                try {
                    // 타임아웃은 데드락 방지를 위한 안전장치
                    bDone.await(3, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
                return null; // 여기서 커밋
            });
        }, "A");

        Thread tb = new Thread(() -> {
            try {
                aPicked.await(3, java.util.concurrent.TimeUnit.SECONDS); // A가 집을 때까지 대기
            } catch (InterruptedException ignored) {}

            new TransactionTemplate(txm).execute(st -> {
                // B: A가 잠근 2건은 SKIP되고, 남은 1건만 즉시 선점해야 함 (대기 X)
                List<Long> bIds = repo.lockSellCandidate(stockId, 100_000, 2);
                bIdsRef.set(bIds);
                return null; // 커밋
            });

            bDone.countDown(); // A에게 커밋해도 된다고 알림
        }, "B");

        ta.start(); tb.start();
        ta.join(); tb.join();

        List<Long> lockedByA = aIdsRef.get();
        List<Long> lockedByB = bIdsRef.get();

        // A는 2건, B는 1건만
        assertThat(lockedByA).hasSize(2);
        assertThat(lockedByB).hasSize(1);
        // 교집합 없어야 함 (A가 잠근 행은 SKIP됨)
        assertThat(lockedByB)
                .doesNotContainAnyElementsOf(lockedByA);
    }

    // --- helper ---
    private void makeSellOrder(Long stockId, long price, long qty) {
        Stock stock = em.find(Stock.class, stockId);
        Portfolio sellerPortfolio = em.find(Portfolio.class, 2L); // seed에 존재하는 포트폴리오
        StockOrder so = StockOrder.createStockOrder(
                stock, new Quantity(qty), new Money(price), sellerPortfolio);
        Order o = Order.createSingleOrder(sellerPortfolio, so, OrderType.SELL, new Money(price * qty));

        em.persist(o);
        em.flush();
        em.clear();
    }
}
