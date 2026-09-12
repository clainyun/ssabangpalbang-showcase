package com.ssafy.ssabangpalbang.apartment.dto.response;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.domain.ApartmentTransaction;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ApartmentDetailResponse(
        Long apartmentId,
        String name,
        String address,
        String districtCode,
        String districtName,
        String dongName,
        Double latitude,
        Double longitude,
        Integer householdCount,
        String completionYearMonth,
        String imageUrl,
        Integer parkingSpaceCount,
        BigDecimal parkingSpacesPerHousehold,
        ApartmentDetailLatestTransaction latestTransaction,
        boolean latestTransactionAvailable,
        Integer recruitingStudyCount,
        Integer completedReportCount,
        boolean favoritedByMe
) {

    public static ApartmentDetailResponse of(
            Apartment apartment,
            ApartmentTransaction latestTransaction,
            int recruitingStudyCount,
            int completedReportCount,
            boolean favoritedByMe
    ) {
        return of(
                apartment,
                latestTransaction,
                recruitingStudyCount,
                completedReportCount,
                favoritedByMe,
                null
        );
    }

    public static ApartmentDetailResponse of(
            Apartment apartment,
            ApartmentTransaction latestTransaction,
            int recruitingStudyCount,
            int completedReportCount,
            boolean favoritedByMe,
            String imageUrl
    ) {
        ApartmentDetailLatestTransaction transaction =
                latestTransaction == null
                        ? null
                        : ApartmentDetailLatestTransaction.from(latestTransaction);
        return new ApartmentDetailResponse(
                apartment.getId(),
                apartment.getName(),
                apartment.getAddress(),
                apartment.getDistrictCode(),
                apartment.getDistrictName(),
                apartment.getDongName(),
                apartment.getLatitude(),
                apartment.getLongitude(),
                apartment.getHouseholdCount(),
                apartment.getCompletionYearMonth(),
                imageUrl,
                apartment.getParkingSpaceCount(),
                parkingSpacesPerHousehold(apartment),
                transaction,
                transaction != null,
                recruitingStudyCount,
                completedReportCount,
                favoritedByMe
        );
    }

    private static BigDecimal parkingSpacesPerHousehold(Apartment apartment) {
        Integer parkingSpaces = apartment.getParkingSpaceCount();
        Integer households = apartment.getHouseholdCount();
        if (parkingSpaces == null || households == null || households == 0) {
            return null;
        }
        return BigDecimal.valueOf(parkingSpaces)
                .divide(BigDecimal.valueOf(households), 2, RoundingMode.HALF_UP);
    }
}
