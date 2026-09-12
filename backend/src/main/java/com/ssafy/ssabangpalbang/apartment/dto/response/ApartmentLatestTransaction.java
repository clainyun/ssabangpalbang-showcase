package com.ssafy.ssabangpalbang.apartment.dto.response;

import com.ssafy.ssabangpalbang.apartment.domain.ApartmentTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ApartmentLatestTransaction(
        Long price, String priceUnit, BigDecimal exclusiveArea, LocalDate dealDate
) {
    public static ApartmentLatestTransaction from(ApartmentTransaction transaction) {
        return new ApartmentLatestTransaction(
                transaction.getPrice(), "TEN_THOUSAND_KRW",
                transaction.getExclusiveArea(),
                transaction.getDealDate()
        );
    }

    public static ApartmentLatestTransaction of(
            Long price,
            java.math.BigDecimal exclusiveArea,
            LocalDate dealDate
    ) {
        return new ApartmentLatestTransaction(
                price,
                "TEN_THOUSAND_KRW",
                exclusiveArea,
                dealDate
        );
    }
}
