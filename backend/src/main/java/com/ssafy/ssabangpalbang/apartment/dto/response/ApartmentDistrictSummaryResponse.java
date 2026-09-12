package com.ssafy.ssabangpalbang.apartment.dto.response;

import java.util.List;

public record ApartmentDistrictSummaryResponse(
        List<ApartmentDistrictSummaryItem> districts,
        int totalCount,
        long totalApartmentCount
) {

    public static ApartmentDistrictSummaryResponse of(
            List<ApartmentDistrictSummaryItem> districts
    ) {
        List<ApartmentDistrictSummaryItem> immutableDistricts = List.copyOf(districts);
        long totalApartmentCount = immutableDistricts.stream()
                .mapToLong(ApartmentDistrictSummaryItem::apartmentCount)
                .sum();
        return new ApartmentDistrictSummaryResponse(
                immutableDistricts,
                immutableDistricts.size(),
                totalApartmentCount
        );
    }
}
