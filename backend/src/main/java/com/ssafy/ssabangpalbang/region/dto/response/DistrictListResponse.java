package com.ssafy.ssabangpalbang.region.dto.response;

import com.ssafy.ssabangpalbang.region.domain.SeoulDistrict;

import java.util.Arrays;
import java.util.List;

public record DistrictListResponse(
        String cityCode,
        String cityName,
        List<DistrictItem> districts,
        Integer totalCount
) {
    public static DistrictListResponse from(SeoulDistrict[] districts) {
        List<DistrictItem> items = Arrays.stream(districts)
                .map(DistrictItem::from)
                .toList();
        return new DistrictListResponse(
                SeoulDistrict.CITY_CODE,
                SeoulDistrict.CITY_NAME,
                items,
                items.size()
        );
    }
}
