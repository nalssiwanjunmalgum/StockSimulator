package com.portfolio2025.first;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FlywaySchemaTest {

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    static void beforeAll(@Autowired Flyway flyway) {
        // 매 테스트 세트 시작마다 완전 초기화
//        flyway.repair();
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void accounts_availableCash_컬럼이_존재하고_NOT_NULL이다() {
        Integer notNull = jdbc.queryForObject(
                "select is_nullable = 'NO' from information_schema.columns " +
                        "where table_name='accounts' and column_name='available_cash'", Integer.class);
        assertThat(notNull).isEqualTo(1);
    }

    @Test
    void portfolio_stocks_유니크인덱스_존재한다() {
        Integer exists = jdbc.queryForObject(
                "select count(*) from information_schema.statistics " +
                        "where table_name='portfolio_stocks' and index_name='ux_portfolio_stock' and non_unique=0",
                Integer.class);
        assertThat(exists).isGreaterThan(0);
    }

    @Test
    void stock_orders_매칭인덱스_존재한다() {
        Integer exists = jdbc.queryForObject(
                "select count(*) from information_schema.statistics " +
                        "where table_name='stock_orders' and index_name='ix_stock_orders_match'",
                Integer.class);
        assertThat(exists).isGreaterThan(0);
    }
}
