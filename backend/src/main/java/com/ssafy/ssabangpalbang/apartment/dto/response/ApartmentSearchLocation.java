package com.ssafy.ssabangpalbang.apartment.dto.response;

public record ApartmentSearchLocation(
        Double latitude, Double longitude, Integer radiusMeters, String locationName
) {
}
