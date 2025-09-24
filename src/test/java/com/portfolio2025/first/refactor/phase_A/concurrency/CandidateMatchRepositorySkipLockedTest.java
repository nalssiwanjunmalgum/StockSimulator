package com.portfolio2025.first.refactor.phase_A.concurrency;

import com.portfolio2025.first.legacy.domain.Order;
import com.portfolio2025.first.legacy.domain.Portfolio;
import com.portfolio2025.first.legacy.domain.order.OrderType;
import com.portfolio2025.first.legacy.domain.stock.Stock;
import com.portfolio2025.first.legacy.domain.stock.StockOrder;
import com.portfolio2025.first.legacy.domain.stock.StockOrderStatus;
import com.portfolio2025.first.legacy.domain.vo.Money;
import com.portfolio2025.first.legacy.domain.vo.Quantity;
import com.portfolio2025.first.refactor.phase_A.shared.CandidateMatchRepository;
import com.portfolio2025.first.refactor.phase_A.shared.CandidateMatchRepositoryImpl;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@DataJpaTest
@Import(CandidateMatchRepositoryImpl.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(Lifecycle.PER_CLASS)
class CandidateMatchRepositorySkipLockedTest {

    @Autowired Flyway flyway;
    @Autowired EntityManager em;
    @Autowired
    CandidateMatchRepository repo;
    @Autowired PlatformTransactionManager txm;

    Long stockId = 1L;
    TransactionTemplate txReqNew;

    @BeforeEach
    void resetSchemaAndSeed() {
        // 각 테스트 간 데이터 오염 방지: 스키마 리셋
        flyway.clean();
        flyway.migrate();

        // REQUIRES_NEW 템플릿 설정 (A/B/시드 모두 독립 트랜잭션)
        txReqNew = new TransactionTemplate(txm);
        txReqNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 시드 데이터는 반드시 커밋된 상태로 준비
        txReqNew.execute(st -> {
            for (int i = 0; i < 3; i++) {
                makeSellOrder(stockId, 100_000, 5);
            }
            em.flush();
            em.clear();
            return null;
        });
    }

    @Test
    void 선점후_상태전환으로_순차실행시_남은_Pending만_집는다() {
        // A: 2건 선점 + PROCESSING 전환
        List<Long> aIds = txReqNew.execute(st -> repo.lockSellCandidate(stockId, 100_000, 2));
        assertThat(aIds).hasSize(2);

        // 상태 전환 확인
        List<StockOrderStatus> aStatuses = em.createQuery(
                        "select so.stockOrderStatus from StockOrder so where so.id in :ids", StockOrderStatus.class)
                .setParameter("ids", aIds).getResultList();
        assertThat(aStatuses).allMatch(s -> s.name().equals("PROCESSING"));

        // B: 이제 PENDING만 대상이므로 1건만 잡힘
        List<Long> bIds = txReqNew.execute(st -> repo.lockSellCandidate(stockId, 100_000, 2));
        assertThat(bIds).hasSize(1);
        assertThat(bIds).doesNotContainAnyElementsOf(aIds); // 포함하고 있지 않음을 확인할 떄 사용할 수 있는 테스트 코드
    }

    @Test
    void A가_선점후_예외롤백되면_B가_다시_2건_선점한다() {
        // When: A 트랜잭션에서 선점 + 상태전환 직후 예외 → 전체 롤백
        assertThatThrownBy(() ->
                txReqNew.execute(st -> {
                    repo.lockSellCandidate(stockId, 100_000, 2); // 내부에서 FOR UPDATE SKIP LOCKED + PROCESSING 전환
                    throw new RuntimeException("boom");          // 롤백 유도(언체크 예외)
                })
        ).isInstanceOf(RuntimeException.class);

        // Then: B 트랜잭션에서 다시 동일 2건 선점 가능
        List<Long> picked = txReqNew.execute(st -> repo.lockSellCandidate(stockId, 100_000, 2));
        assertThat(picked).hasSize(2);
    }

    @Test
    void 정렬_가격_생성시각_id_ASC_우선() {
        // given: 동일 가격/동일 created_at/서로 다른 id 3건 시드
        List<Long> seededIds = txReqNew.execute(st -> {
            // 1) 먼저 일반 방식으로 3건 생성
            Long id1 = makeSellOrderReturnId(stockId, 100_000, 5);
            Long id2 = makeSellOrderReturnId(stockId, 100_000, 5);
            Long id3 = makeSellOrderReturnId(stockId, 100_000, 5);

            // 2) created_at을 동일 타임스탬프로 맞춤 (엔티티 세터가 없다면 네이티브 UPDATE로 통제)
            LocalDateTime sameTs = LocalDateTime.of(2025, 9, 13, 12, 0, 0); // 예시 고정값
            em.createNativeQuery("""
                            UPDATE stock_orders
                               SET created_at = :ts
                             WHERE id IN (:ids)
                        """)
                    .setParameter("ts", sameTs)
                    .setParameter("ids", List.of(id1, id2, id3))
                    .executeUpdate();

            em.flush();
            em.clear();

            return List.of(id1, id2, id3);
        });

        // when: 동일 가격/시간에서 3건 선점
        List<Long> picked = txReqNew.execute(st -> repo.lockSellCandidate(stockId, 100_000, 3));

        // then: id ASC로만 선점되어야 함
        List<Long> expected = seededIds.stream().sorted().toList();
        assertThat(picked).containsExactlyElementsOf(expected);
    }

    @Test
    void 동일_트랜잭션_재호출시_중복선점_없음() {
        txReqNew.execute(st -> {
            // 첫 호출: 2건 선점 + PROCESSING 전환
            List<Long> first = repo.lockSellCandidate(stockId, 100_000, 2);
            assertThat(first).hasSize(2);

            // 같은 트랜잭션에서 두 번째 호출: 이미 PROCESSING으로 바뀌었으므로 더 이상 잡히지 않음
            List<Long> second = repo.lockSellCandidate(stockId, 100_000, 2);
            assertThat(second).hasSize(1);
            assertThat(second).doesNotContainAnyElementsOf(first);

            // 상태 검증: 첫 호출로 잡힌 id들이 모두 PROCESSING인지 확인
            Number processingCnt = (Number) em.createNativeQuery("""
                        SELECT COUNT(*) FROM stock_orders
                         WHERE id IN (:ids) AND stock_order_status = :st
                    """)
                    .setParameter("ids", first)
                    .setParameter("st", StockOrderStatus.PROCESSING.name())
                    .getSingleResult();

            assertThat(processingCnt.longValue()).isEqualTo(first.size());

            // 최종적으로 PENDING이 0인지, PROCESSING=3인지 체크
            Number pendingCnt = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM stock_orders WHERE stock_order_status = 'PENDING'
            """).getSingleResult();

            Number totalProcessingCnt = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM stock_orders WHERE stock_order_status = 'PROCESSING'
            """).getSingleResult();

            assertThat(pendingCnt.longValue()).isZero();
            assertThat(totalProcessingCnt.longValue()).isEqualTo(3L);

            return null;
        });
    }

    @Test
    void limit0이면_0건() {
        List<Long> picked = txReqNew.execute(st -> repo.lockSellCandidate(stockId, 100_000, 0));
        assertThat(picked).isEmpty();
    }

    // 5-1) buyPrice: 요청가(매도호가) <= buyPrice 만 집는다 (동일가는 포함, 초과는 제외)
    // Filtering 관련 조건 확인
    @Test
    void buyPrice_이하만_집는다_초과는_제외() {
        // 두 건을 buyPrice 초과로 올려 제외시킴
        txReqNew.execute(st -> {
            em.createNativeQuery("""
                UPDATE stock_orders
                   SET requested_price = 120000
                 WHERE stock_order_status='PENDING'
                 ORDER BY id ASC
                 LIMIT 2
            """).executeUpdate();
            return null;
        });

        // buyPrice=100000 → 남아있는 1건만 매칭
        List<Long> picked = txReqNew.execute(st -> repo.lockSellCandidate(stockId, 100_000, 3));
        assertThat(picked).hasSize(1);
    }

    // 5-2) remained_quantity > 0: 0인 행은 제외
    @Test
    void remainedQuantity0이면_제외() {
        // 한 건을 remained_quantity=0으로 만들어 제외
        Long zeroId = txReqNew.execute(st -> {
            Number id = (Number) em.createNativeQuery("""
                    SELECT id FROM stock_orders
                     WHERE stock_order_status='PENDING'
                     ORDER BY id ASC LIMIT 1
                """).getSingleResult();

            em.createNativeQuery("""
            UPDATE stock_orders
               SET remained_quantity = 0
             WHERE id = :id
        """).setParameter("id", id.longValue()).executeUpdate();
            return id.longValue();
        });

        List<Long> picked = txReqNew.execute(st -> repo.lockSellCandidate(stockId, 100_000, 3));
        assertThat(picked).hasSize(2);                       // 3중 1건(잔량0) 제외
        assertThat(picked).doesNotContain(zeroId);           // 그 id는 포함 안 됨
    }

    @Test
    void SKIP_LOCKED_없으면_B는_블로킹된다() throws Exception {
        // 준비: 스레드풀 + 래치
        ExecutorService es = Executors.newFixedThreadPool(2);
        CountDownLatch aLocked = new CountDownLatch(1);     // A가 잠금 완료했음을 신호
        CountDownLatch allowACommit = new CountDownLatch(1);// A 커밋 허용 신호

        // A: 두 건 잠그고 커밋 대기
        Future<List<Long>> fa = es.submit(() -> txReqNew.execute(st -> {
            List<Long> a = repo.lockSellCandidate_NoSkip(stockId, 100_000, 2); // FOR UPDATE (no skip)
            aLocked.countDown();     // 잠금 완료 → B 시작해도 됨
            // 커밋 허용 신호 올 때까지 대기(락 유지)
            try {
                allowACommit.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return a;                // 커밋
        }));

        // A가 잠금할 때까지 기다림
        assertThat(aLocked.await(1, TimeUnit.SECONDS)).isTrue();

        // B: 같은 조건으로 진입 → A가 잡은 레코드 락 때문에 대기해야 함
        Future<List<Long>> fb = es.submit(() -> txReqNew.execute(st ->
                repo.lockSellCandidate_NoSkip(stockId, 100_000, 2)
        ));

        // B는 아직 커밋 신호 전이므로 get에 타임아웃이 발생해야 함
        assertThatThrownBy(() -> fb.get(300, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

        // 이제 A 커밋 허용 → 락 해제
        allowACommit.countDown();

        // A/B 모두 정상 완료 확인
        List<Long> aRes = fa.get(2, TimeUnit.SECONDS);
        List<Long> bRes = fb.get(2, TimeUnit.SECONDS);

        assertThat(aRes).hasSize(2);
        assertThat(bRes).hasSize(2); // A 커밋 후 B가 동일 조건으로 잠금 성공

        es.shutdown();
    }

    /** Helper 메서드 **/
    private Long makeSellOrderReturnId(Long stockId, long price, long qty) {
        Stock stock = em.find(Stock.class, stockId);
        Portfolio sellerPortfolio = em.find(Portfolio.class, 2L);

        StockOrder so = StockOrder.createStockOrder(
                stock, new Quantity(qty), new Money(price), sellerPortfolio);
        Order o = Order.createSingleOrder(
                sellerPortfolio, so, OrderType.SELL, new Money(price * qty));

        em.persist(o);
        em.flush();     // PK 할당 보장
        em.clear();

        return so.getId(); // 또는 o/so에서 PK 꺼내는 방식에 맞춰 조정
    }

    @Test
    void 오래된_PROCESSING만_updatedAt기준_재큐잉된다() {
        // given: 3건을 PROCESSING으로 만들고 updated_at을 서로 다르게 세팅
        List<Long> processingIds = txReqNew.execute(st -> {
            // lockSellCandidate()가 상태를 PROCESSING으로 바꿈
            List<Long> ids = repo.lockSellCandidate(stockId, 100_000, 3);
            assertThat(ids).hasSize(3);

            // 주의: native UPDATE로 상태만 바꿨다면 updated_at이 자동 갱신되지 않을 수 있음
            // -> 테스트에서 의도적으로 updated_at을 '과거/최근'으로 맞춰준다.
            List<Long> oldTwo = ids.subList(0, 2);
            Long fresh = ids.get(2);

            // 오래된 2건: updated_at = NOW() - 10분
            em.createNativeQuery("""
                UPDATE stock_orders
                   SET updated_at = DATE_SUB(NOW(), INTERVAL 10 MINUTE)
                 WHERE id IN (:ids)
            """).setParameter("ids", oldTwo).executeUpdate();

            // 신선한 1건: updated_at = NOW() - 1분
            em.createNativeQuery("""
                UPDATE stock_orders
                   SET updated_at = DATE_SUB(NOW(), INTERVAL 1 MINUTE)
                 WHERE id = :id
            """).setParameter("id", fresh).executeUpdate();

            em.flush();
            em.clear();
            return ids;
        });

        // when: 5분 이상 지난 PROCESSING만 PENDING으로 복구
        int rescued = txReqNew.execute(st -> repo.rescueStaleProcessingSecondsUsingUpdatedAt(5 * 60));
        assertThat(rescued).isEqualTo(2);

        // then: 상태 확인 - 오래된 2건만 PENDING으로 돌아와야 함
        @SuppressWarnings("unchecked") // Generic 관련 경고를 컴파일 상에서 제외하고자 함.
        List<Number> pendingAfter = (List<Number>) txReqNew.execute(st ->
                em.createNativeQuery("""
                    SELECT id FROM stock_orders
                     WHERE stock_order_status = 'PENDING'
                     ORDER BY id ASC
                """).getResultList()
                );
        List<Long> pendingIds = pendingAfter.stream().map(Number::longValue).toList();

        assertThat(pendingIds).hasSize(2);
        assertThat(processingIds).containsAll(pendingIds); // 복구된 건은 원래 3건 중 일부여야 함

        // and: 복구된 2건만 다시 선점 가능, 최근 1건은 여전히 PROCESSING 이라 제외
        List<Long> picked = txReqNew.execute(st -> repo.lockSellCandidate(stockId, 100_000, 3));
        assertThat(picked).hasSize(2);
        assertThat(picked).containsExactlyInAnyOrderElementsOf(pendingIds);
    }

    private void makeSellOrder(Long stockId, long price, long qty) {
        Stock stock = em.find(Stock.class, stockId);
        Portfolio sellerPortfolio = em.find(Portfolio.class, 2L); // seed: sellerA

        StockOrder so = StockOrder.createStockOrder(
                stock, new Quantity(qty), new Money(price), sellerPortfolio);
        Order o = Order.createSingleOrder(sellerPortfolio, so, OrderType.SELL, new Money(price * qty));

        em.persist(o);
        em.flush(); // txReqNew에서 일괄적으로 처리하기도 함
        em.clear(); // txReqNew에서 일괄적으로 처리
    }

    @Test
    void 선점_쿼리용_복합인덱스가_존재한다() {
        // INFORMATION_SCHEMA에서 인덱스 컬럼 순서를 읽어온다
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
            SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME
              FROM INFORMATION_SCHEMA.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'stock_orders'
             ORDER BY INDEX_NAME, SEQ_IN_INDEX
        """).getResultList();

        Map<String, List<String>> indexToCols = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String idx = (String) r[0];
            String col = (String) r[2];
            indexToCols.computeIfAbsent(idx, k -> new ArrayList<>()).add(col);
        }

        // 선점 쿼리 WHERE+ORDER BY의 핵심 프리픽스
        List<String> neededPrefix = List.of(
                "stock_id", "stock_order_status", "requested_price", "created_at", "id"
        );

        boolean found = indexToCols.values().stream()
                .anyMatch(cols -> startsWith(cols, neededPrefix));

        assertThat(found)
                .withFailMessage(() -> "선점용 인덱스 프리픽스가 필요합니다. 기대: "
                        + neededPrefix + " / 실제: " + indexToCols)
                .isTrue();
    }

    @Test
    void 리스큐_쿼리용_인덱스가_존재한다() {
        // rescue: WHERE stock_order_status='PROCESSING' AND TIMESTAMPDIFF(SECOND, updated_at, NOW()) >= :th
        // -> status + updated_at 프리픽스가 효율적
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
            SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME
              FROM INFORMATION_SCHEMA.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'stock_orders'
             ORDER BY INDEX_NAME, SEQ_IN_INDEX
        """).getResultList();

        Map<String, List<String>> indexToCols = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String idx = (String) r[0];
            String col = (String) r[2];
            indexToCols.computeIfAbsent(idx, k -> new ArrayList<>()).add(col);
        }

        List<String> neededPrefix = List.of("stock_order_status", "updated_at");

        boolean found = indexToCols.values().stream()
                .anyMatch(cols -> startsWith(cols, neededPrefix));

        assertThat(found)
                .withFailMessage(() -> "리스큐용 인덱스 프리픽스가 필요합니다. 기대: "
                        + neededPrefix + " / 실제: " + indexToCols)
                .isTrue();
    }

    // ---------- helper ----------
    private boolean startsWith(List<String> cols, List<String> prefix) {
        if (cols.size() < prefix.size()) return false;
        for (int i = 0; i < prefix.size(); i++) {
            if (!prefix.get(i).equalsIgnoreCase(cols.get(i))) return false;
        }
        return true;
    }


}
