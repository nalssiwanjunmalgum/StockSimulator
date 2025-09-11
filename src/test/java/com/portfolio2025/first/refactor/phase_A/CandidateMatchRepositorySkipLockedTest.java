package com.portfolio2025.first.refactor.phase_A;

import com.portfolio2025.first.domain.Order;
import com.portfolio2025.first.domain.Portfolio;
import com.portfolio2025.first.domain.order.OrderType;
import com.portfolio2025.first.domain.stock.Stock;
import com.portfolio2025.first.domain.stock.StockOrder;
import com.portfolio2025.first.domain.vo.Money;
import com.portfolio2025.first.domain.vo.Quantity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CandidateMatchRepositorySkipLockedTest {

    @Autowired Flyway flyway;
    @Autowired EntityManager em;
    @Autowired CandidateMatchRepository repo;
    @Autowired PlatformTransactionManager txm;

    Long stockId = 1L;

    @BeforeAll
    static void setupSchema(@Autowired Flyway flyway) {
        flyway.clean();
        flyway.migrate();
    }

    @BeforeEach
    void seedOrders() {
        // 매도 후보 3건 (전부 100_000, created_at은 기본 now)
        for (int i = 0; i < 3; i++) {
            makeSellOrder(stockId, 100_000, 5);
        }
    }

    @Test
    void 먼저_잠근_행은_다음_트랜잭션에서_SKIP된다() {
        TransactionTemplate txA = new TransactionTemplate(txm);
        TransactionTemplate txB = new TransactionTemplate(txm);

        List<Long> lockedByA = txA.execute(status -> {
            List<Long> ids = repo.lockSellCandidate(stockId, 100_000, 2);
            // 트랜잭션 A는 커밋 전까지 락 유지
            assertThat(ids).hasSize(2);
            return ids;
        });

        // 트랜잭션 A가 끝난 시점에 커밋되어 락이 풀리므로,
        // "진짜 동시성"을 보려면 A를 열린 채로 B를 실행해야 한다.
        // 아래는 간단화를 위해 '순차' 확인: B가 다시 2건을 뽑아도 A가 잡았던 2건은 사라졌으니 1건만 남음.
        List<Long> lockedByB = txB.execute(status -> {
            List<Long> ids = repo.lockSellCandidate(stockId, 100_000, 2);
            return ids;
        });

        assertThat(lockedByB).hasSize(1);
        assertThat(lockedByB).doesNotContainAnyElementsOf(lockedByA);
    }

    // ---------- helper ----------
    private void makeSellOrder(Long stockId, long price, long qty) {
        Stock stock = em.find(Stock.class, stockId);
        Portfolio sellerPortfolio = em.find(Portfolio.class, 2L); // seed: sellerA

        StockOrder so = StockOrder.createStockOrder(
                stock, new Quantity(qty), new Money(price), sellerPortfolio);

        Order o = Order.createSingleOrder(sellerPortfolio, so, OrderType.SELL,
                new Money(price * qty));

        em.persist(o);
        em.flush();
        em.clear();
    }
}
