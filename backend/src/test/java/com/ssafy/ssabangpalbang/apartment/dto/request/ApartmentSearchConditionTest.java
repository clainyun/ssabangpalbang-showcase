package com.ssafy.ssabangpalbang.apartment.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApartmentSearchConditionTest {

    @Test
    void 검색어를_정규화한다() {
        ApartmentSearchCondition condition = ApartmentSearchCondition.of(
                "  래미안   옥수  ", null, null, null, null, null, null, null);
        assertThat(condition.keyword()).isEqualTo("래미안 옥수");
        assertThat(condition.mode()).isEqualTo(ApartmentSearchMode.KEYWORD);
    }

    @Test
    void 조회조건이_없으면_거부한다() {
        assertCode(ErrorCode.APARTMENT_FILTER_REQUIRED,
                () -> ApartmentSearchCondition.of(null, null, null,
                        null, null, null, null, null));
    }

    @Test
    void 코드_형식과_소속을_순서대로_검증한다() {
        assertCode(ErrorCode.REGION_DISTRICT_CODE_INVALID,
                () -> ApartmentSearchCondition.of(null, "1234", null,
                        null, null, null, null, null));
        assertCode(ErrorCode.REGION_DONG_CODE_INVALID,
                () -> ApartmentSearchCondition.of(null, null, "123456789",
                        null, null, null, null, null));
        assertCode(ErrorCode.REGION_CODE_MISMATCH,
                () -> ApartmentSearchCondition.of(null, "11440", "1171010100",
                        null, null, null, null, null));
    }

    @Test
    void 위치와_반경을_검증하고_기본값을_적용한다() {
        assertCode(ErrorCode.APARTMENT_LOCATION_INVALID,
                () -> ApartmentSearchCondition.of(null, null, null,
                        37.5, null, null, null, null));
        assertCode(ErrorCode.APARTMENT_RADIUS_INVALID,
                () -> ApartmentSearchCondition.of(null, null, null,
                        37.5, 127.0, 50, null, null));
        ApartmentSearchCondition condition = ApartmentSearchCondition.of(
                "역삼", null, null, 37.5, 127.0, null, null, null);
        assertThat(condition.radiusMeters()).isEqualTo(3000);
        assertThat(condition.mode()).isEqualTo(ApartmentSearchMode.NEARBY);
        assertCode(ErrorCode.APARTMENT_LOCATION_INVALID,
                () -> ApartmentSearchCondition.of(null, null, null,
                        Double.NaN, 127.0, 3000, null, null));
        assertCode(ErrorCode.APARTMENT_LOCATION_INVALID,
                () -> ApartmentSearchCondition.of(null, null, null,
                        37.5, Double.POSITIVE_INFINITY, 3000, null, null));
    }

    @Test
    void 특수문자와_길이와_페이지를_검증한다() {
        assertCode(ErrorCode.INVALID_INPUT_VALUE,
                () -> ApartmentSearchCondition.of("!!!", null, null,
                        null, null, null, null, null));
        assertCode(ErrorCode.INVALID_INPUT_VALUE,
                () -> ApartmentSearchCondition.of("가".repeat(101), null, null,
                        null, null, null, null, null));
        assertCode(ErrorCode.INVALID_INPUT_VALUE,
                () -> ApartmentSearchCondition.of("역삼", null, null,
                        null, null, null, 0, 0));
    }

    private void assertCode(ErrorCode code, Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
