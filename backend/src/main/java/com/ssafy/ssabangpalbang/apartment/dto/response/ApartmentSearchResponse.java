package com.ssafy.ssabangpalbang.apartment.dto.response;

import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentSearchMode;

import java.util.List;

public record ApartmentSearchResponse(
        ApartmentSearchMode searchMode,
        ApartmentSearchLocation currentLocation,
        List<ApartmentSearchItem> content,
        long totalElements, int page, int size, int totalPages
) {
}
