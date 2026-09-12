package com.ssafy.ssabangpalbang.apartment.dto.response;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum ApartmentResponseCode implements ResponseCode {
    APARTMENT_BOUNDS_SUCCESS(
            "APARTMENT_BOUNDS_SUCCESS",
            "지도 영역의 아파트 조회에 성공했습니다."
    ),
    APARTMENT_DISTRICT_SUMMARY_SUCCESS(
            "APARTMENT_DISTRICT_SUMMARY_SUCCESS",
            "자치구별 아파트 집계 조회에 성공했습니다."
    ),

    APARTMENT_DETAIL_SUCCESS(
            "APARTMENT_DETAIL_SUCCESS",
            "아파트 상세 조회에 성공했습니다."
    ),
    APARTMENT_TRANSACTION_LIST_SUCCESS(
            "APARTMENT_TRANSACTION_LIST_SUCCESS",
            "아파트 실거래 목록 조회에 성공했습니다."
    ),
    APARTMENT_REPORT_LIST_SUCCESS(
            "APARTMENT_REPORT_LIST_SUCCESS",
            "아파트 완료 리포트 목록 조회에 성공했습니다."
    ),
    APARTMENT_STUDY_LIST_SUCCESS(
            "APARTMENT_STUDY_LIST_SUCCESS",
            "아파트 모집 스터디 목록 조회에 성공했습니다."
    ),
    APARTMENT_LIST_SUCCESS(
            "APARTMENT_LIST_SUCCESS",
            "아파트 목록 조회에 성공했습니다."
    ),
    APARTMENT_FAVORITE_SUCCESS(
            "APARTMENT_FAVORITE_SUCCESS",
            "아파트를 찜했습니다."
    ),
    APARTMENT_FAVORITE_ALREADY_EXISTS(
            "APARTMENT_FAVORITE_ALREADY_EXISTS",
            "이미 찜한 아파트입니다."
    ),
    APARTMENT_UNFAVORITE_SUCCESS(
            "APARTMENT_UNFAVORITE_SUCCESS",
            "아파트 찜을 해제했습니다."
    ),
    APARTMENT_FAVORITE_NOT_FOUND(
            "APARTMENT_FAVORITE_NOT_FOUND",
            "찜하지 않은 아파트입니다."
    );

    private final String code;
    private final String message;

    ApartmentResponseCode(String code, String message) {
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
