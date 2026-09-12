package com.ssafy.ssabangpalbang.apartment.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApartmentTransactionSearchConditionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 28);

    @Test
    void convertsAreaToToleranceRange() {
        ApartmentTransactionSearchCondition condition =
                ApartmentTransactionSearchCondition.of(84.8, null, null, TODAY);

        assertThat(condition.areaMin()).isEqualByComparingTo(new BigDecimal("84.75"));
        assertThat(condition.areaMax()).isEqualByComparingTo(new BigDecimal("84.85"));
    }

    @Test
    void leavesAreaRangeNullWhenAreaIsAbsent() {
        ApartmentTransactionSearchCondition condition =
                ApartmentTransactionSearchCondition.of(null, null, null, TODAY);

        assertThat(condition.areaMin()).isNull();
        assertThat(condition.areaMax()).isNull();
    }

    @Test
    void rejectsZeroAndNegativeAreaWithFieldData() {
        assertAreaInvalid(0.0);
        assertAreaInvalid(-1.0);
    }

    @Test
    void convertsYearToDateRange() {
        ApartmentTransactionSearchCondition condition =
                ApartmentTransactionSearchCondition.of(null, 2026, null, TODAY);

        assertThat(condition.startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(condition.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void leavesDateRangeNullWhenYearIsAbsent() {
        ApartmentTransactionSearchCondition condition =
                ApartmentTransactionSearchCondition.of(null, null, null, TODAY);

        assertThat(condition.startDate()).isNull();
        assertThat(condition.endDate()).isNull();
    }

    @Test
    void rejectsFutureAndTooOldYearWithFieldData() {
        assertYearInvalid(2027);
        assertYearInvalid(1987);
    }

    private void assertAreaInvalid(double area) {
        assertThatThrownBy(() ->
                ApartmentTransactionSearchCondition.of(area, null, null, TODAY))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.APARTMENT_TRANSACTION_AREA_INVALID);
                    assertThat(exception.getData())
                            .containsEntry("field", "exclusiveArea");
                });
    }

    private void assertYearInvalid(int year) {
        assertThatThrownBy(() ->
                ApartmentTransactionSearchCondition.of(null, year, null, TODAY))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.APARTMENT_TRANSACTION_YEAR_INVALID);
                    assertThat(exception.getData()).containsEntry("field", "year");
                });
    }
}
