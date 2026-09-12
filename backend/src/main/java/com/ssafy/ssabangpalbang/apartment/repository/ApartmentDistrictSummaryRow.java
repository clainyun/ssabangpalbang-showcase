package com.ssafy.ssabangpalbang.apartment.repository;

public record ApartmentDistrictSummaryRow(
        String districtCode,
        long apartmentCount,
        Double centerLatitude,
        Double centerLongitude
) {
}
