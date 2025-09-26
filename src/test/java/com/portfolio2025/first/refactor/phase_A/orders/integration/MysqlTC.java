package com.portfolio2025.first.refactor.phase_A.orders.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("phaseA")
public abstract class MysqlTC {
    // MySQL 컨테이너 생성 (동적 생성 및 삭제까지 진행함)
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withReuse(true)
            .withDatabaseName("testdb")     // 컨테이너 내부 DB 이름
            .withUsername("testuser")       // 접속 계정
            .withPassword("testpass");      // 접속 비밀번호

    @Test
    void contextLoads() {

    }
}
