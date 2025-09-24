package com.portfolio2025.first.refactor.phase_A.domain;

import com.portfolio2025.first.legacy.domain.vo.Quantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QuantityTest {

    private Quantity q(long v) {
        // 필요 시 Quantity.of(v) 등으로 교체
        return new Quantity(v);
    }

    @Nested
    @DisplayName("생성자 유효성")
    class ConstructorValidation {

        @Test
        @DisplayName("0 이상이면 생성된다(0 허용)")
        void create_valid() {
            assertThat(q(0)).isNotNull();
            assertThat(q(1)).isNotNull();
            assertThat(q(999_999)).isNotNull();
        }

        @Test
        @DisplayName("null이면 예외")
        void create_null_throws() {
            assertThatThrownBy(() -> new Quantity(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("음수면 예외")
        void create_negative_throws() {
            assertThatThrownBy(() -> q(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("덧셈(plus)")
    class Plus {

        @Test
        @DisplayName("정상 합산 & 원본 불변")
        void plus_ok_and_immutable() {
            Quantity a = q(10);
            Quantity b = q(5);

            Quantity result = a.plus(b);

            assertThat(result).isEqualTo(q(15));
            // 원본 불변
            assertThat(a).isEqualTo(q(10));
            assertThat(b).isEqualTo(q(5));
            // 새 인스턴스
            assertThat(result).isNotSameAs(a);
            assertThat(result).isNotSameAs(b);
        }

        @Test
        @DisplayName("피연산자 null이면 예외")
        void plus_null_throws() {
            Quantity a = q(1);
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
            Quantity a = q(10);
            Quantity b = q(4);

            Quantity result = a.minus(b);

            assertThat(result).isEqualTo(q(6));
            assertThat(a).isEqualTo(q(10)); // 원본 불변
            assertThat(b).isEqualTo(q(4));
        }

        @Test
        @DisplayName("정확히 동일 수량 차감(경계값) 성공 → 0")
        void minus_equal_to_zero() {
            Quantity a = q(7);
            assertThat(a.minus(q(7))).isEqualTo(q(0));
        }

        @Test
        @DisplayName("차감 결과 음수이면 예외 & 상태 불변")
        void minus_underflow_throws_and_state_unchanged() {
            Quantity before = q(3);
            Quantity req = q(4);

            assertThatThrownBy(() -> before.minus(req))
                    .isInstanceOf(IllegalArgumentException.class);

            // 상태 불변 확인
            assertThat(before).isEqualTo(q(3));
            assertThat(req).isEqualTo(q(4));
        }

        @Test
        @DisplayName("피연산자 null이면 예외")
        void minus_null_throws() {
            Quantity a = q(1);
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
            Quantity a = q(10);
            assertThat(a.isLowerThan(q(11))).isTrue();
            assertThat(a.isLowerThan(q(10))).isFalse();
            assertThat(a.isLowerThan(q(9))).isFalse();
        }

        @Test
        @DisplayName("isHigherThan: 클 때만 true, 같거나 작으면 false")
        void isHigherThan_cases() {
            Quantity a = q(10);
            assertThat(a.isHigherThan(q(9))).isTrue();
            assertThat(a.isHigherThan(q(10))).isFalse();
            assertThat(a.isHigherThan(q(11))).isFalse();
        }
    }

    @Nested
    @DisplayName("누적 & 경계 시나리오")
    class AccumulationAndBoundaries {

        @Test
        @DisplayName("연속 덧셈 누적")
        void accumulate_plus() {
            Quantity base = q(10);
            Quantity r1 = base.plus(q(5));    // 15
            Quantity r2 = r1.plus(q(5));      // 20

            assertThat(r2).isEqualTo(q(20));
            // 불변성: base는 그대로
            assertThat(base).isEqualTo(q(10));
        }

        @Test
        @DisplayName("연속 차감 누적(경계값 포함)")
        void accumulate_minus() {
            Quantity base = q(10);
            Quantity r1 = base.minus(q(3));  // 7
            Quantity r2 = r1.minus(q(7));    // 0

            assertThat(r2).isEqualTo(q(0));
        }
    }

    @Nested
    @DisplayName("동치성(equals/hashCode) 간단 검증")
    class Equality {

        @Test
        @DisplayName("같은 수량이면 동치, 해시도 동일")
        void equality() {
            Quantity x = q(42);
            Quantity y = q(42);

            assertThat(x).isEqualTo(y);
            assertThat(x.hashCode()).isEqualTo(y.hashCode());
        }

        @Test
        @DisplayName("다른 수량이면 비동치")
        void not_equal() {
            assertThat(q(1)).isNotEqualTo(q(2));
        }
    }
}
