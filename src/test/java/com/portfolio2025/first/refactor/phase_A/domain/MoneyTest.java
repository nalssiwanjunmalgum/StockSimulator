package com.portfolio2025.first.refactor.phase_A.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio2025.first.legacy.domain.vo.Money;
import com.portfolio2025.first.legacy.domain.vo.Quantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private Quantity qty(long v) {
        // 필요 시 Quantity.of(v) 등으로 변경
        return new Quantity(v);
    }

    @Nested
    @DisplayName("생성자 유효성")
    class ConstructorValidation {

        @Test
        @DisplayName("0 이상이면 생성된다(0 허용)")
        void create_valid() {
            Money zero = new Money(0L);
            Money positive = new Money(123L);

            assertThat(zero).isNotNull();
            assertThat(positive).isNotNull();
        }

        @Test
        @DisplayName("null이면 예외")
        void create_null_throws() {
            assertThatThrownBy(() -> new Money(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("음수면 예외")
        void create_negative_throws() {
            assertThatThrownBy(() -> new Money(-1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("덧셈(plus)")
    class Plus {

        @Test
        @DisplayName("정상 합산 & 원본 불변")
        void plus_ok_and_immutable() {
            Money a = new Money(1000L);
            Money b = new Money(250L);

            Money result = a.plus(b);

            assertThat(result).isEqualTo(new Money(1250L));
            // 원본 불변
            assertThat(a).isEqualTo(new Money(1000L));
            assertThat(b).isEqualTo(new Money(250L));
            // 새 인스턴스
            assertThat(result).isNotSameAs(a);
            assertThat(result).isNotSameAs(b);
        }

        @Test
        @DisplayName("피연산자 null이면 예외")
        void plus_null_throws() {
            Money a = new Money(100L);
            assertThatThrownBy(() -> a.plus(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("뺄셈(minus)")
    class Minus {

        @Test
        @DisplayName("정상 차감 & 원본 불변")
        void minus_ok_and_immutable() {
            Money a = new Money(1000L);
            Money b = new Money(400L);

            Money result = a.minus(b);

            assertThat(result).isEqualTo(new Money(600L));
            assertThat(a).isEqualTo(new Money(1000L)); // 원본 불변
            assertThat(b).isEqualTo(new Money(400L));
        }

        @Test
        @DisplayName("보유액 부족 시 예외 & 상태 불변")
        void minus_insufficient_throws_and_state_unchanged() {
            Money before = new Money(300L);
            Money cost = new Money(400L);

            assertThatThrownBy(() -> before.minus(cost))
                    .isInstanceOf(IllegalArgumentException.class);

            // 예외 발생 후에도 원본 상태는 그대로
            assertThat(before).isEqualTo(new Money(300L));
            assertThat(cost).isEqualTo(new Money(400L));
        }

        @Test
        @DisplayName("피연산자 null이면 예외")
        void minus_null_throws() {
            Money a = new Money(100L);
            assertThatThrownBy(() -> a.minus(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("비교자(isLowerThan / isHigherThan)")
    class Comparators {

        @Test
        @DisplayName("isLowerThan: 작을 때만 true, 같거나 크면 false")
        void isLowerThan_cases() {
            Money a = new Money(100L);
            assertThat(a.isLowerThan(new Money(101L))).isTrue();
            assertThat(a.isLowerThan(new Money(100L))).isFalse();
            assertThat(a.isLowerThan(new Money(99L))).isFalse();
        }

        @Test
        @DisplayName("isHigherThan: 클 때만 true, 같거나 작으면 false")
        void isHigherThan_cases() {
            Money a = new Money(100L);
            assertThat(a.isHigherThan(new Money(99L))).isTrue();
            assertThat(a.isHigherThan(new Money(100L))).isFalse();
            assertThat(a.isHigherThan(new Money(101L))).isFalse();
        }
    }

    @Nested
    @DisplayName("곱셈(multiply)")
    class Multiply {

        @Test
        @DisplayName("수량과 곱셈")
        void multiply_ok() {
            Money price = new Money(1_000L);
            Quantity q = qty(5);

            Money total = price.multiply(q);

            assertThat(total).isEqualTo(new Money(5_000L));
            // 원본 불변
            assertThat(price).isEqualTo(new Money(1_000L));
        }

        @Test
        @DisplayName("수량 0이면 총액 0")
        void multiply_zero() {
            Money price = new Money(1_000L);
            Quantity q = qty(0);

            Money total = price.multiply(q);

            assertThat(total).isEqualTo(new Money(0L));
        }

        @Test
        @DisplayName("수량이 null이면 예외(정책에 따라)")
        void multiply_null_qty_throws() {
            Money price = new Money(1_000L);
            assertThatThrownBy(() -> price.multiply(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("동치성(equals/hashCode) 간단 검증")
    class Equality {

        @Test
        @DisplayName("같은 금액이면 동치, 해시도 동일")
        void equality() {
            Money x = new Money(777L);
            Money y = new Money(777L);

            assertThat(x).isEqualTo(y);
            assertThat(x.hashCode()).isEqualTo(y.hashCode());
        }

        @Test
        @DisplayName("다른 금액이면 비동치")
        void not_equal() {
            assertThat(new Money(1L)).isNotEqualTo(new Money(2L));
        }
    }
}
