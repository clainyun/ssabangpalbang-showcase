package com.ssafy.ssabangpalbang.apartment.dto.response;

import com.ssafy.ssabangpalbang.apartment.repository.ApartmentDistrictSummaryRow;
import com.ssafy.ssabangpalbang.region.domain.SeoulDistrict;

public record ApartmentDistrictSummaryItem(
        String districtCode,
        String districtName,
        long apartmentCount,
        Double centerLatitude,
        Double centerLongitude
) {

    public static ApartmentDistrictSummaryItem of(
            SeoulDistrict district,
            ApartmentDistrictSummaryRow row
    ) {
        if (row == null) {
            return new ApartmentDistrictSummaryItem(
                    district.getCode(),
                    district.getDistrictName(),
                    0L,
                    null,
                    null
            );
        }
        return new ApartmentDistrictSummaryItem(
                district.getCode(),
                district.getDistrictName(),
                row.apartmentCount(),
                row.centerLatitude(),
                row.centerLongitude()
        );
    }
}
