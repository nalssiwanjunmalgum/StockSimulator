package com.portfolio2025.first.refactor.phase_A.orders.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio2025.first.refactor.phase_A.orders.adapter.out.jpa.IdempotencyJpaAdapter;
import com.portfolio2025.first.refactor.phase_A.orders.adapter.out.jpa.IdempotencyKeyJpaRepository;
import com.portfolio2025.first.refactor.phase_A.orders.adapter.out.jpa.IdempotencyTx;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.idempotency.IdempotencyPort.ClaimResult;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.idempotency.IdempotencyPort.Existing;
import com.portfolio2025.first.refactor.phase_A.orders.domain.idempotency.IdempotencyKey;
import com.portfolio2025.first.refactor.phase_A.orders.domain.idempotency.IdempotencyStatus;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({IdempotencyJpaAdapter.class, IdempotencyTx.class}) // 어댑터 직접 주입
@ActiveProfiles("phaseA") // yaml << MysqlTC TestContainers의 설정이 더 우선된다
class IdempotencyJpaAdapterIT extends MysqlTC {

    @Autowired IdempotencyJpaAdapter adapter;
    @Autowired IdempotencyKeyJpaRepository repo;
    @Autowired PlatformTransactionManager txm;
    @Autowired TransactionTemplate txTemplate;

    @BeforeEach
    void clean() {
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        txTemplate.executeWithoutResult(s -> repo.deleteAll());
    }

    final Long userId = 1L;
    final Long portfolioId = 10L;
    final String clientOrderId = "CO-123";
    final String payloadHash = "hash-abc";

    @Test
    @DisplayName("tryClaim: 최초 시도는 성공, 동일 키 재시도는 충돌")
    void tryClaim_success_then_conflict() {
        // first
        ClaimResult r1 = adapter.tryClaim(userId, portfolioId, clientOrderId, payloadHash);
        assertThat(r1).isEqualTo(ClaimResult.CLAIM_SUCCESS);

        // second with same key
        ClaimResult r2 = adapter.tryClaim(userId, portfolioId, clientOrderId, payloadHash);
        assertThat(r2).isEqualTo(ClaimResult.CLAIM_CONFLICT);

        // DB에 PENDING 1건
        List<IdempotencyKey> all = repo.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatus()).isEqualTo(IdempotencyStatus.PENDING);
    }

    @Test
    @DisplayName("동시경합: 동일 키로 두 쓰레드가 시도하면 1개만 성공, 1개는 UNIQUE 위반으로 충돌 (Future/Callable)")
    void tryClaim_concurrent_withFuture() throws Exception {
        int threads = 2;
        ExecutorService es = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);

        try {
            Callable<ClaimResult> work = () -> {
                try {
                    // 두 스레드 동시 출발 유도
                    ready.countDown();
                    go.await();
                    // 실제 호출 (예외 발생 시 Future#get에서 터짐)
                    return adapter.tryClaim(userId, portfolioId, clientOrderId, payloadHash);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    // 테스트 용도: 인터럽트 시에도 null 반환하지 않도록 안전값
                    return ClaimResult.CLAIM_CONFLICT;
                }
            };

            Future<ClaimResult> fa = es.submit(work);
            Future<ClaimResult> fb = es.submit(work);

            // 두 스레드 준비될 때까지 대기 후, 동시에 시작
            ready.await();
            go.countDown();

            // 예외가 있으면 여기서 throw → 원인 파악 쉬움
            ClaimResult ra = fa.get(5, TimeUnit.SECONDS);
            ClaimResult rb = fb.get(5, TimeUnit.SECONDS);

            // 한쪽만 성공
            assertThat(Arrays.asList(ra, rb))
                    .containsExactlyInAnyOrder(ClaimResult.CLAIM_SUCCESS, ClaimResult.CLAIM_CONFLICT);

            // 실제 DB에는 단 1건만
            assertThat(repo.count()).isEqualTo(1);

        } finally {
            es.shutdown();
            if (!es.awaitTermination(5, TimeUnit.SECONDS)) {
                es.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("동시경합: CompletableFuture 버전")
    void tryClaim_concurrent_withCompletableFuture() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Supplier<ClaimResult> work = () -> {
            try {
                ready.countDown();
                go.await();
                return adapter.tryClaim(userId, portfolioId, clientOrderId, payloadHash);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ClaimResult.CLAIM_CONFLICT;
            }
        };

        CompletableFuture<ClaimResult> f1 = CompletableFuture.supplyAsync(work);
        CompletableFuture<ClaimResult> f2 = CompletableFuture.supplyAsync(work);

        ready.await();
        go.countDown();

        ClaimResult r1 = f1.get(5, TimeUnit.SECONDS);
        ClaimResult r2 = f2.get(5, TimeUnit.SECONDS);

        assertThat(Arrays.asList(r1, r2))
                .containsExactlyInAnyOrder(ClaimResult.CLAIM_SUCCESS, ClaimResult.CLAIM_CONFLICT);
        assertThat(repo.count()).isEqualTo(1);
    }


    @Test
    @DisplayName("findExisting: 존재 시 DTO 매핑")
    void findExisting_returnsMapping() {
        // 사전 삽입: adapter 경로 그대로 사용
        assertThat(adapter.tryClaim(userId, portfolioId, clientOrderId, payloadHash))
                .isEqualTo(ClaimResult.CLAIM_SUCCESS);

        Optional<Existing> ex = adapter.findExisting(userId, portfolioId, clientOrderId);
        assertThat(ex).isPresent();
        assertThat(ex.get().payloadHash()).isEqualTo(payloadHash);
        assertThat(ex.get().orderId()).isNull();
        assertThat(ex.get().stockOrderId()).isNull();
    }

    @Test
    @DisplayName("complete: 비관잠금(forUpdate)로 상태/ID 세팅")
    void complete_updatesStatusAndIds() {
        // given
        adapter.tryClaim(userId, portfolioId, clientOrderId, payloadHash);

        // when
        adapter.complete(userId, portfolioId, clientOrderId, 111L, 222L);

        // then
        IdempotencyKey key = repo.findByUserIdAndPortfolioIdAndClientOrderId(userId, portfolioId, clientOrderId)
                .orElseThrow();
        assertThat(key.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(key.getOrderId()).isEqualTo(111L);
        assertThat(key.getStockOrderId()).isEqualTo(222L);
    }

    @Test
    @DisplayName("fail: 비관잠금(forUpdate)로 상태 FAIL 세팅")
    void fail_setsFail() {
        adapter.tryClaim(userId, portfolioId, clientOrderId, payloadHash);
        adapter.fail(userId, portfolioId, clientOrderId);

        IdempotencyKey key = repo.findByUserIdAndPortfolioIdAndClientOrderId(userId, portfolioId, clientOrderId)
                .orElseThrow();
        assertThat(key.getStatus()).isEqualTo(IdempotencyStatus.FAILED);
    }

    @Test
    @DisplayName("TTL: pending() 호출 시 expireAt 설정(모델 규칙 검증)")
    void ttl_isSetOnPending() {
        // 직접 엔티티를 확인하고 싶다면 트랜잭션 경계 안에서 조회
        new TransactionTemplate(txm).executeWithoutResult(tx -> {
            ClaimResult r = adapter.tryClaim(userId, portfolioId, clientOrderId, payloadHash);
            assertThat(r).isEqualTo(ClaimResult.CLAIM_SUCCESS);

            IdempotencyKey key = repo.findByUserIdAndPortfolioIdAndClientOrderId(userId, portfolioId, clientOrderId)
                    .orElseThrow();
            assertThat(key.getExpiresAt()).isNotNull(); // 엔티티에 필드가 있다고 가정
            // expiresAt - now ~= PENDING_TTL(5분) 근사치
            assertThat(Duration.between(key.getCreatedAt(), key.getExpiresAt()).toMinutes())
                    .isBetween(4L, 6L);
        });
    }
}
