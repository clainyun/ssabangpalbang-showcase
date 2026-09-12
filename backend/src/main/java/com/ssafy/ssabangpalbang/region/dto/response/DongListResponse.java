package com.ssafy.ssabangpalbang.region.dto.response;

import com.ssafy.ssabangpalbang.region.domain.SeoulDistrict;
import com.ssafy.ssabangpalbang.region.repository.RegionDongRow;

import java.util.List;

public record DongListResponse(
        String districtCode,
        String districtName,
        List<DongItem> dongs,
        Integer totalCount
) {
    public static DongListResponse from(
            SeoulDistrict district,
            List<RegionDongRow> rows
    ) {
        List<DongItem> items = rows.stream()
                .map(DongItem::from)
                .toList();
        return new DongListResponse(
                district.getCode(),
                district.getDistrictName(),
                items,
                items.size()
        );
    }
}
