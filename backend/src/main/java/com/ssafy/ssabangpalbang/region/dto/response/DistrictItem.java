package com.ssafy.ssabangpalbang.region.dto.response;

import com.ssafy.ssabangpalbang.region.domain.SeoulDistrict;

public record DistrictItem(String districtCode, String districtName) {
    public static DistrictItem from(SeoulDistrict district) {
        return new DistrictItem(district.getCode(), district.getDistrictName());
    }
}
