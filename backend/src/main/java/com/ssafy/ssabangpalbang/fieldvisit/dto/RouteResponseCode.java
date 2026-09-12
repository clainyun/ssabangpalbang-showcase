package com.ssafy.ssabangpalbang.fieldvisit.dto;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum RouteResponseCode implements ResponseCode {

    ROUTE_GENERATE_SUCCESS("ROUTE_GENERATE_SUCCESS", "추천 경로를 생성했습니다."),
    ROUTE_ALREADY_EXISTS("ROUTE_ALREADY_EXISTS", "이미 생성된 추천 경로입니다."),
    ROUTE_DETAIL_SUCCESS("ROUTE_DETAIL_SUCCESS", "추천 경로 조회에 성공했습니다.");

    private final String code;
    private final String message;

    RouteResponseCode(String code, String message) {
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
