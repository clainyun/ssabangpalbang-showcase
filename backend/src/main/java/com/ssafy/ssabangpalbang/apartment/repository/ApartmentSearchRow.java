package com.ssafy.ssabangpalbang.apartment.repository;

public record ApartmentSearchRow(
        Long apartmentId, String name, String address,
        String districtName, String dongName,
        Double latitude, Double longitude,
        Integer distanceMeters
) {
}
