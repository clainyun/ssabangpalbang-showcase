package com.ssafy.ssabangpalbang.apartment.dto.response;

import com.ssafy.ssabangpalbang.apartment.domain.ApartmentTransaction;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentSearchRow;

public record ApartmentSearchItem(
        Long apartmentId, String name, String address,
        String districtName, String dongName,
        Double latitude, Double longitude,
        ApartmentLatestTransaction latestTransaction,
        boolean latestTransactionAvailable,
        Integer distanceMeters, boolean favoritedByMe
) {
    public static ApartmentSearchItem from(
            ApartmentSearchRow row, ApartmentTransaction transaction, boolean favorited
    ) {
        ApartmentLatestTransaction latest = transaction == null
                ? null : ApartmentLatestTransaction.from(transaction);
        return new ApartmentSearchItem(
                row.apartmentId(), row.name(), row.address(), row.districtName(), row.dongName(),
                row.latitude(), row.longitude(),
                latest, latest != null, row.distanceMeters(), favorited
        );
    }
}
