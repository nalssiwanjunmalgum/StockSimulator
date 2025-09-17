package com.portfolio2025.first.legacy;

import com.portfolio2025.first.legacy.exception.AlreadyProcessedException;
import com.portfolio2025.first.legacy.exception.RetryableException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis에서 중복 처리가 된 것인지 확인하는 RedisRegister
 * 중복으로 처리되지는 않는지를 확인한다.
 * [07.30]
 * (추가)
 *
 * [고민]
 * Redis Register 이라는 클래스명 수정해야 하지 않을까??
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRegister {

    private final RedisTemplate<String, String> redis;

    private static final String ORDER_PROCESSED_PREFIX = "order:processed:";
    private static final Duration TTL = Duration.ofHours(1); // TTL로 중복 방지 유지 시간 설정

    private String key(Long orderId) {
        return ORDER_PROCESSED_PREFIX + orderId;
    }

    /** 이미 처리된 주문인지 확인 (키 존재 여부) */
    public boolean isAlreadyProcessed(Long orderId) {
        String k = key(orderId);
        Boolean exists = redis.hasKey(k);
        log.debug("🔍 Idempotency check: {} = {}", k, exists);
        return exists;
    }

    /**
     * 멱등 마킹: 처음만 성공해야 함
     * - 첫 시도: SET NX EX → true  (성공)
     * - 두 번째 이후: SET NX → false → AlreadyProcessedException
     * - 커넥션/일시 장애: RetryableException
     */
    public void tryMarkProcessedOrThrow(Long orderId) {
        String k = key(orderId);
        try {
            Boolean set = redis.opsForValue()
                    .setIfAbsent(k, "1", TTL); // SET k "1" NX EX TTL
            if (Boolean.FALSE.equals(set)) {
                // 이미 마킹되어 있음 → 중복 소비를 멱등으로 흡수
                throw new AlreadyProcessedException("Already marked processed: " + orderId);
            }

            if (set == null) {
                throw new RetryableException("Redis returned null on SETNX for " + k);
            }
            log.debug("✅ Marked processed: {}", k);

        } catch (DataAccessResourceFailureException | RedisSystemException e) {
            // 네트워크 끊김/리소스 문제 등 → 재시도 가치 有
            throw new RetryableException("Redis idempotency mark transient issue", e);
        }
    }
}
