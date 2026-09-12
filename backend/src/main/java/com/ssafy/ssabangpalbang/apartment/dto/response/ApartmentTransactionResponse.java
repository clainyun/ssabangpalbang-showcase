package com.ssafy.ssabangpalbang.apartment.dto.response;

import com.ssafy.ssabangpalbang.apartment.domain.ApartmentTransaction;

import java.time.LocalDate;

public record ApartmentTransactionResponse(
        Long transactionId,
        LocalDate dealDate,
        Long price,
        String priceUnit,
        Double exclusiveArea,
        Integer floor
) {

    private static final String PRICE_UNIT = "TEN_THOUSAND_KRW";

    public static ApartmentTransactionResponse from(ApartmentTransaction transaction) {
        return new ApartmentTransactionResponse(
                transaction.getId(),
                transaction.getDealDate(),
                transaction.getPrice(),
                PRICE_UNIT,
                transaction.getExclusiveArea() == null
                        ? null : transaction.getExclusiveArea().doubleValue(),
                transaction.getFloor()
        );
    }
}
