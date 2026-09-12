package com.ssafy.ssabangpalbang.apartment.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ApartmentBoundsRow(
        Long apartmentId,
        String name,
        String address,
        Double latitude,
        Double longitude,
        Long price,
        BigDecimal exclusiveArea,
        LocalDate dealDate
) {
}
