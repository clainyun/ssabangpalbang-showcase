package com.ssafy.ssabangpalbang.apartment.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApartmentBoundsConditionTest {

    @Test
    void 정상_좌표와_최대_범위_경계값은_통과한다() {
        assertThatCode(() -> ApartmentBoundsCondition.of(37.0, 127.0, 37.5, 127.5))
                .doesNotThrowAnyException();
    }

    @Test
    void 좌표_누락을_가장_먼저_검증한다() {
        assertError(
                () -> ApartmentBoundsCondition.of(null, -181.0, 37.5, 127.5),
                ErrorCode.APARTMENT_BOUNDS_REQUIRED
        );
    }

    @Test
    void 유한하지_않거나_범위를_벗어난_좌표를_거절한다() {
        assertError(
                () -> ApartmentBoundsCondition.of(Double.NaN, 127.0, 37.5, 127.5),
                ErrorCode.APARTMENT_BOUNDS_COORDINATE_INVALID
        );
        assertError(
                () -> ApartmentBoundsCondition.of(37.0, Double.POSITIVE_INFINITY, 37.5, 127.5),
                ErrorCode.APARTMENT_BOUNDS_COORDINATE_INVALID
        );
        assertError(
                () -> ApartmentBoundsCondition.of(91.0, 127.0, 91.2, 127.2),
                ErrorCode.APARTMENT_BOUNDS_COORDINATE_INVALID
        );
        assertError(
                () -> ApartmentBoundsCondition.of(37.0, -181.0, 37.2, -180.5),
                ErrorCode.APARTMENT_BOUNDS_COORDINATE_INVALID
        );
    }

    @Test
    void 남서쪽이_북동쪽보다_작지_않으면_영역크기보다_먼저_거절한다() {
        assertError(
                () -> ApartmentBoundsCondition.of(38.0, 127.0, 37.0, 127.8),
                ErrorCode.APARTMENT_BOUNDS_ORDER_INVALID
        );
        assertError(
                () -> ApartmentBoundsCondition.of(37.0, 128.0, 37.2, 127.0),
                ErrorCode.APARTMENT_BOUNDS_ORDER_INVALID
        );
        assertError(
                () -> ApartmentBoundsCondition.of(37.0, 127.0, 37.0, 127.2),
                ErrorCode.APARTMENT_BOUNDS_ORDER_INVALID
        );
    }

    @Test
    void 위도나_경도_차이가_점오를_초과하면_거절한다() {
        assertError(
                () -> ApartmentBoundsCondition.of(37.0, 127.0, 37.6, 127.2),
                ErrorCode.APARTMENT_BOUNDS_TOO_LARGE
        );
        assertError(
                () -> ApartmentBoundsCondition.of(37.0, 127.0, 37.2, 127.6),
                ErrorCode.APARTMENT_BOUNDS_TOO_LARGE
        );
    }

    private void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
