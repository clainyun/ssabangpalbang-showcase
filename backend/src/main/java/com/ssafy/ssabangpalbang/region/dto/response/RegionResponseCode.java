package com.ssafy.ssabangpalbang.region.dto.response;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum RegionResponseCode implements ResponseCode {
    REGION_DISTRICT_LIST_SUCCESS("REGION_DISTRICT_LIST_SUCCESS", "서울 자치구 목록 조회에 성공했습니다."),
    REGION_DONG_LIST_SUCCESS("REGION_DONG_LIST_SUCCESS", "동 목록 조회에 성공했습니다.");

    private final String code;
    private final String message;

    RegionResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
