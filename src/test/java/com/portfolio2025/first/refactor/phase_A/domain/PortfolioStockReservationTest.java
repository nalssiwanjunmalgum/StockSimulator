package com.portfolio2025.first.refactor.phase_A.domain;

import com.portfolio2025.first.legacy.domain.Portfolio;
import com.portfolio2025.first.legacy.domain.PortfolioStock;
import com.portfolio2025.first.legacy.domain.PortfolioType;
import com.portfolio2025.first.legacy.domain.User;
import com.portfolio2025.first.legacy.domain.stock.Stock;
import com.portfolio2025.first.legacy.domain.vo.Money;
import com.portfolio2025.first.legacy.domain.vo.Quantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.*;

class PortfolioStockReservationTest {

    @Mock User dummyUser;
    // ---- 테스트 헬퍼: 실제 엔티티 생성 방식에 맞게 조정하세요 ----
    private Portfolio dummyPortfolio() {

        return Portfolio.builder()
                .user(dummyUser)
                .portfolioType(PortfolioType.STOCK) // 실제 Enum 사용
                .build();
    }

    private Stock dummyStock() {
        return new Stock(); // @NoArgsConstructor 가정 (필요 시 빌더/팩토리로 교체)
    }

    private PortfolioStock ps(long portfolioQty) {
        return PortfolioStock.createPortfolioStock(
                dummyPortfolio(),
                dummyStock(),
                new Quantity(portfolioQty),
                new Money(1_000L) // 평균단가 임의값
        );
    }

    private Quantity q(long v) { return new Quantity(v); }
    // ----------------------------------------------------

    @Nested
    @DisplayName("정상 예약")
    class ReserveSuccess {

        @Test
        @DisplayName("가용 수량 내에서 예약 성공 → reserved 증가, 가용(=보유-예약) 감소")
        void reserve_ok() {
            PortfolioStock stock = ps(100);      // reserved=0 초기화됨
            stock.reserve(q(30));                // reserved=30

            assertThat(stock.getReservedQuantity()).isEqualTo(q(30));
            Quantity available = stock.getPortfolioQuantity().minus(stock.getReservedQuantity());
            assertThat(available).isEqualTo(q(70));
        }

        @Test
        @DisplayName("정확히 동일 수량(경계값) 예약 → 가용 0")
        void reserve_exact_equals() {
            PortfolioStock stock = ps(100);
            stock.reserve(q(100));

            assertThat(stock.getReservedQuantity()).isEqualTo(q(100));
            Quantity available = stock.getPortfolioQuantity().minus(stock.getReservedQuantity());
            assertThat(available).isEqualTo(q(0));
        }

        @Test
        @DisplayName("연속 예약 누적")
        void reserve_multiple() {
            PortfolioStock stock = ps(100);
            stock.reserve(q(30)); // reserved=30
            stock.reserve(q(20)); // reserved=50

            assertThat(stock.getReservedQuantity()).isEqualTo(q(50));
            Quantity available = stock.getPortfolioQuantity().minus(stock.getReservedQuantity());
            assertThat(available).isEqualTo(q(50));
        }
    }

    @Nested
    @DisplayName("예외 및 상태 불변")
    class ReserveFailures {

        @Test
        @DisplayName("가용 수량 부족 시 예외 & 상태 불변")
        void insufficient_throws_and_state_unchanged() {
            PortfolioStock stock = ps(50);
            stock.reserve(q(10)); // reserved=10, 가용=40

            Quantity beforeReserved = stock.getReservedQuantity();

            assertThatThrownBy(() -> stock.reserve(q(41)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("예약할 수 있는 수량이 부족");

            // 상태 불변
            assertThat(stock.getReservedQuantity()).isEqualTo(beforeReserved);
            Quantity available = stock.getPortfolioQuantity().minus(stock.getReservedQuantity());
            assertThat(available).isEqualTo(q(40));
        }

        @Test
        @DisplayName("요청 수량이 null이면 NPE (현재 구현)")
        void null_qty_throws_npe_current_impl() {
            PortfolioStock stock = ps(10);

            assertThatThrownBy(() -> stock.reserve(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("요청 수량이 0이면 상태 변화 없음(정책 고정)")
        void zero_qty_noop() {
            PortfolioStock stock = ps(10);
            stock.reserve(q(3));      // reserved=3

            stock.reserve(q(0));      // no-op 정책

            assertThat(stock.getReservedQuantity()).isEqualTo(q(3));
        }
    }

    @Nested
    @DisplayName("시간 갱신")
    class Timestamp {

        @Test
        @DisplayName("reserve 호출 시 lastUpdatedAt 갱신된다(이전보다 이후 또는 동일 이상)")
        void updates_timestamp() {
            PortfolioStock stock = ps(100);
            LocalDateTime before = stock.getLastUpdatedAt(); // createPortfolioStock에서 now() 세팅

            stock.reserve(q(10));

            LocalDateTime after = stock.getLastUpdatedAt();
            assertThat(after).isNotNull();
            // 환경에 따라 정밀도 이슈가 있을 수 있어 '이후 또는 동일'로 검증
            assertThat(after).isAfterOrEqualTo(before);
        }
    }
}
