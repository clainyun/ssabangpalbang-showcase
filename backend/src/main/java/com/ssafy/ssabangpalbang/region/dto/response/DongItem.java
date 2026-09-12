package com.ssafy.ssabangpalbang.region.dto.response;

import com.ssafy.ssabangpalbang.region.repository.RegionDongRow;

public record DongItem(
        String dongCode,
        String dongName,
        Boolean hasApartment,
        Integer apartmentCount
) {
    public static DongItem from(RegionDongRow row) {
        int apartmentCount = Math.toIntExact(row.apartmentCount());
        return new DongItem(
                row.dongCode(),
                row.dongName(),
                apartmentCount > 0,
                apartmentCount
        );
    }
}
