package com.ssafy.ssabangpalbang.apartment.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record ApartmentTransactionSearchCondition(
        BigDecimal areaMin,
        BigDecimal areaMax,
        LocalDate startDate,
        LocalDate endDate,
        TransactionSort sort
) {

    private static final BigDecimal AREA_TOLERANCE = new BigDecimal("0.05");
    private static final int MIN_YEAR = 1988;

    public static ApartmentTransactionSearchCondition of(
            Double exclusiveArea,
            Integer year,
            String sort,
            LocalDate today
    ) {
        BigDecimal areaMin = null;
        BigDecimal areaMax = null;
        if (exclusiveArea != null) {
            if (!Double.isFinite(exclusiveArea) || exclusiveArea <= 0) {
                throw new BusinessException(
                        ErrorCode.APARTMENT_TRANSACTION_AREA_INVALID,
                        Map.of("field", "exclusiveArea", "reason", "전용면적은 0보다 커야 합니다.")
                );
            }
            BigDecimal area = BigDecimal.valueOf(exclusiveArea);
            areaMin = area.subtract(AREA_TOLERANCE);
            areaMax = area.add(AREA_TOLERANCE);
        }

        LocalDate startDate = null;
        LocalDate endDate = null;
        if (year != null) {
            if (year < MIN_YEAR || year > today.getYear()) {
                throw new BusinessException(
                        ErrorCode.APARTMENT_TRANSACTION_YEAR_INVALID,
                        Map.of("field", "year", "reason",
                                MIN_YEAR + "년부터 " + today.getYear() + "년까지만 조회할 수 있습니다.")
                );
            }
            startDate = LocalDate.of(year, 1, 1);
            endDate = LocalDate.of(year, 12, 31);
        }

        return new ApartmentTransactionSearchCondition(
                areaMin, areaMax, startDate, endDate, TransactionSort.from(sort)
        );
    }
}
