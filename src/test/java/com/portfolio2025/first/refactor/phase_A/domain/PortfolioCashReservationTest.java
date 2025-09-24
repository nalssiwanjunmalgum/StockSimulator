package com.portfolio2025.first.refactor.phase_A.domain;

import com.portfolio2025.first.legacy.domain.Portfolio;
import com.portfolio2025.first.legacy.domain.PortfolioType;
import com.portfolio2025.first.legacy.domain.User;
import com.portfolio2025.first.legacy.domain.vo.Money;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * BUY 시 현금 예약/차감 로직 검증
 * - Portfolio는 builder(User, PortfolioType)로 생성되고, 현금은 0으로 초기화되므로
 *   테스트에서 deposit()으로 가용현금을 세팅한다.
 */
class PortfolioCashReservationTest {

    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        // User/PortfolioType은 최소 생성만. (createPortfolio 사용 X: 양방향 연계 불필요)
        User dummyUser = User.createUser(
                "DD",
                "LL", "111-xxx-xxxx", "dfdfdf@gmail.com", "scott211"
        );

        portfolio = Portfolio.builder()
                .user(dummyUser)
                .portfolioType(PortfolioType.STOCK) // 실제 enum 상수로 교체
                .build();

        // 초기 가용현금 세팅 (reservedCash=0, portfolioTotalValue=0은 생성 시 기본값)
        portfolio.deposit(new Money(100_000L));
    }

    @Nested
    @DisplayName("현금 예약 성공 시")
    class ReserveSuccess {

        @Test
        @DisplayName("예약 금액만큼 availableCash 감소 & reservedCash 증가")
        void reserve_cash_success() {
            Money total = new Money(40_000L);

            portfolio.reserveAndDeductCash(total);

            assertThat(portfolio.getAvailableCash()).isEqualTo(new Money(60_000L));
            assertThat(portfolio.getReservedCash()).isEqualTo(new Money(40_000L));
        }

        @Test
        @DisplayName("예약 전후 총합(available+reserved)은 동일(불변식)")
        void invariant_total_cash_constant() {
            Money beforeTotal = portfolio.getAvailableCash().plus(portfolio.getReservedCash());

            portfolio.reserveAndDeductCash(new Money(25_000L));

            Money afterTotal = portfolio.getAvailableCash().plus(portfolio.getReservedCash());
            assertThat(afterTotal).isEqualTo(beforeTotal);
        }

        @Test
        @DisplayName("정확히 동일 금액 예약 → available=0, reserved=total")
        void reserve_exact_equal_amount() {
            portfolio.reserveAndDeductCash(new Money(100_000L));

            assertThat(portfolio.getAvailableCash()).isEqualTo(new Money(0L));
            assertThat(portfolio.getReservedCash()).isEqualTo(new Money(100_000L));
        }

        @Test
        @DisplayName("연속 예약 시 누적 반영")
        void reserve_multiple_times() {
            portfolio.reserveAndDeductCash(new Money(30_000L));
            portfolio.reserveAndDeductCash(new Money(20_000L));

            assertThat(portfolio.getAvailableCash()).isEqualTo(new Money(50_000L));
            assertThat(portfolio.getReservedCash()).isEqualTo(new Money(50_000L));
        }
    }

    @Nested
    @DisplayName("현금 부족 시")
    class ReserveFail {

        @Test
        @DisplayName("예외 발생 & 상태 불변")
        void insufficient_cash_throws_and_state_unchanged() {
            Money beforeAvailable = portfolio.getAvailableCash();
            Money beforeReserved = portfolio.getReservedCash();

            assertThatThrownBy(() -> portfolio.reserveAndDeductCash(new Money(200_000L)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("보유 현금이 부족");

            assertThat(portfolio.getAvailableCash()).isEqualTo(beforeAvailable);
            assertThat(portfolio.getReservedCash()).isEqualTo(beforeReserved);
        }
    }

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("0 예약 → 상태 변화 없음")
        void reserve_zero_amount() {
            portfolio.reserveAndDeductCash(new Money(0L));

            assertThat(portfolio.getAvailableCash()).isEqualTo(new Money(100_000L));
            assertThat(portfolio.getReservedCash()).isEqualTo(new Money(0L));
        }
    }
}
