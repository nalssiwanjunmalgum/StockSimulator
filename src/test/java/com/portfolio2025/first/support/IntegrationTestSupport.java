package com.portfolio2025.first.support;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class IntegrationTestSupport {

    @Autowired private org.flywaydb.core.Flyway flyway;
    @Autowired private StringRedisTemplate redis;

    @BeforeAll
    void resetDBAndRedis() {
        flyway.clean();   // 모든 테이블 삭제
        flyway.migrate(); // db/migration + db/testdata 실행

        cleanRedis();
    }

    void cleanRedis() {
        // 프로젝트에서 실제 사용하는 prefix를 모두 정리하세요.
        deleteByPattern("order:processed:*"); // 주문 멱등 키
        deleteByPattern("book:*");            // 오더북(BUY/SELL) 키
        deleteByPattern("match:*");  // (있다면) 중복 트리거 제어 키
    }

    private void deleteByPattern(String pattern) {
        var keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
