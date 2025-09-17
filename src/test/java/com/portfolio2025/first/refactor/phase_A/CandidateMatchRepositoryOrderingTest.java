package com.portfolio2025.first.refactor.phase_A;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio2025.first.legacy.domain.Order;
import com.portfolio2025.first.legacy.domain.Portfolio;
import com.portfolio2025.first.legacy.domain.order.OrderType;
import com.portfolio2025.first.legacy.domain.stock.Stock;
import com.portfolio2025.first.legacy.domain.stock.StockOrder;
import com.portfolio2025.first.legacy.domain.stock.StockOrderStatus;
import com.portfolio2025.first.legacy.domain.vo.Money;
import com.portfolio2025.first.legacy.domain.vo.Quantity;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(CandidateMatchRepositoryImpl.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(Lifecycle.PER_CLASS)
public class CandidateMatchRepositoryOrderingTest {

    @Autowired Flyway flyway;
    @Autowired EntityManager em;
    @Autowired CandidateMatchRepository repo;

    Long stockId; Long buyerPortfolioId;

    private static final LocalDateTime NOW = LocalDateTime.now();


    @BeforeAll
    void setupSchema(@Autowired Flyway flyway) {
        // 테스트 DB는 깨끗하게:
        flyway.clean();
        flyway.migrate();
    }

    @BeforeEach
    void seedDomain() {
        // seed에서:
        // users: (1: buyerA), (2: sellerA)
        // portfolios: (1: user1 STOCK), (2: user2 STOCK)
        // stocks: (1: 005930 삼성전자), (2: 035420 네이버)

        stockId = 1L; // 삼성전자
        buyerPortfolioId = 1L;

        // 매도 후보 4건 중 3건만 유효(가격/상태/잔량 조건 통과)
        // created_at을 일부러 다르게 세팅
        makeSellOrder(stockId, /*price*/ 101_000, /*qty*/ 5, /*status*/ StockOrderStatus.PENDING,
                NOW.minusSeconds(30)); // 후보 #2 (가격 높음)
        makeSellOrder(stockId, 100_000, 5, StockOrderStatus.PENDING,
                NOW.minusSeconds(60)); // 후보 #1 (가장 싸고 가장 오래됨)
        makeSellOrder(stockId, 102_000, 5, StockOrderStatus.PENDING,
                NOW.minusSeconds(10)); // 후보 제외(> buyPrice)
        makeSellOrder(stockId, 100_000, 5, StockOrderStatus.CANCELLED,
                NOW.minusSeconds(5));  // 후보 제외(상태)
    }

    @Test
    void buy요청이면_매도후보를_가격ASC_then_시간ASC_then_idASC로_LOCK_조회한다() {
        long buyPrice = 101_000; // 100_000과 101_000 까지만 통과
        int limit = 10;

        List<Long> ids = repo.lockSellCandidate(stockId, buyPrice, limit);

        // 정렬 기대: (100_000, older) → (101_000)
        assertThat(ids).hasSize(2);

        // 실제로 id를 통해 가격/시간을 확인하고 싶다면 재조회:
        StockOrder so1 = em.find(StockOrder.class, ids.get(0));
        StockOrder so2 = em.find(StockOrder.class, ids.get(1));

        assertThat(so1.getRequestedPrice().getMoneyValue()).isEqualTo(100_000);
        assertThat(so2.getRequestedPrice().getMoneyValue()).isEqualTo(101_000);
    }

    // ---------- helper ----------
    private void makeSellOrder(Long stockId, long price, long qty, StockOrderStatus status, LocalDateTime createdAt) {
        Stock stock = em.find(Stock.class, stockId);
        Portfolio sellerPortfolio = em.find(Portfolio.class, 2L); // seed: sellerA

        StockOrder so = StockOrder.createStockOrder(
                stock, new Quantity(qty), new Money(price), sellerPortfolio);

        Order o = Order.createSingleOrder(sellerPortfolio, so, OrderType.SELL,
                new Money(price * qty));

        // 상태/시간 덮어쓰기
        so.updateStatus(status);
        writeCreatedAt(so, createdAt);


        em.persist(o);
        em.flush();
        em.clear();
    }

    private void writeCreatedAt(StockOrder so, LocalDateTime createdAt) {
        em.flush();

        em.createNativeQuery("UPDATE stock_orders SET created_at = ?, updated_at = ? WHERE id = ?")
                .setParameter(1, createdAt)
                .setParameter(2, createdAt)
                .setParameter(3, so.getId())
                .executeUpdate();
        em.clear();
    }

}
