package com.ssafy.ssabangpalbang.apartment.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApartmentReportSearchConditionTest {

    @Test
    void 기본값과_경계값을_적용한다() {
        assertThat(ApartmentReportSearchCondition.of(1L, null, null))
                .isEqualTo(new ApartmentReportSearchCondition(1L, 0, 20));
        assertThat(ApartmentReportSearchCondition.of(1L, 0, 1).size())
                .isEqualTo(1);
        assertThat(ApartmentReportSearchCondition.of(1L, 0, 100).size())
                .isEqualTo(100);
    }

    @Test
    void 잘못된_아파트와_페이지를_구분한다() {
        assertError(() -> ApartmentReportSearchCondition.of(0L, 0, 20),
                ErrorCode.APARTMENT_ID_INVALID, "apartmentId");
        assertError(() -> ApartmentReportSearchCondition.of(-5L, 0, 20),
                ErrorCode.APARTMENT_ID_INVALID, "apartmentId");
        assertError(() -> ApartmentReportSearchCondition.of(1L, -1, 20),
                ErrorCode.INVALID_INPUT_VALUE, "page");
        assertError(() -> ApartmentReportSearchCondition.of(1L, 0, 0),
                ErrorCode.INVALID_INPUT_VALUE, "size");
        assertError(() -> ApartmentReportSearchCondition.of(1L, 0, 101),
                ErrorCode.INVALID_INPUT_VALUE, "size");
    }

    private void assertError(
            Runnable action,
            ErrorCode code,
            String field
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(code);
                    assertThat(exception.getData()).containsEntry("field", field);
                });
    }
}
