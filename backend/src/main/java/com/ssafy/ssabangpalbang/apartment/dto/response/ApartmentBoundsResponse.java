package com.ssafy.ssabangpalbang.apartment.dto.response;

import java.util.List;

public record ApartmentBoundsResponse(
        List<ApartmentBoundsItem> apartments,
        int count
) {
    public static ApartmentBoundsResponse of(List<ApartmentBoundsItem> apartments) {
        return new ApartmentBoundsResponse(List.copyOf(apartments), apartments.size());
    }
}
