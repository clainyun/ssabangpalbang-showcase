package com.ssafy.ssabangpalbang.apartment.dto.response;

import com.ssafy.ssabangpalbang.apartment.repository.ApartmentBoundsRow;

public record ApartmentBoundsItem(
        Long apartmentId,
        String name,
        String address,
        Double latitude,
        Double longitude,
        ApartmentLatestTransaction latestTransaction,
        boolean latestTransactionAvailable,
        Integer recruitingStudyCount,
        Integer completedReportCount,
        boolean favoritedByMe
) {
    public static ApartmentBoundsItem of(
            ApartmentBoundsRow apartment,
            ApartmentLatestTransaction latestTransaction,
            int recruitingStudyCount,
            int completedReportCount,
            boolean favoritedByMe
    ) {
        return new ApartmentBoundsItem(
                apartment.apartmentId(),
                apartment.name(),
                apartment.address(),
                apartment.latitude(),
                apartment.longitude(),
                latestTransaction,
                latestTransaction != null,
                recruitingStudyCount,
                completedReportCount,
                favoritedByMe
        );
    }
}
