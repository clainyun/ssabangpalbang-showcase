package com.ssafy.ssabangpalbang.apartment.dto.response;

import com.ssafy.ssabangpalbang.apartment.domain.ApartmentTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ApartmentDetailLatestTransaction(
        Long transactionId,
        Long price,
        String priceUnit,
        BigDecimal exclusiveArea,
        LocalDate dealDate,
        Integer floor
) {
    private static final String PRICE_UNIT = "TEN_THOUSAND_KRW";

    public static ApartmentDetailLatestTransaction from(
            ApartmentTransaction transaction
    ) {
        return new ApartmentDetailLatestTransaction(
                transaction.getId(),
                transaction.getPrice(),
                PRICE_UNIT,
                transaction.getExclusiveArea(),
                transaction.getDealDate(),
                transaction.getFloor()
        );
    }
}
