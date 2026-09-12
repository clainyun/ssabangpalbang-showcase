package com.ssafy.ssabangpalbang.review.dto.response;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum MemberReviewResponseCode implements ResponseCode {
    MEMBER_REVIEW_CREATE_SUCCESS(
            "MEMBER_REVIEW_CREATE_SUCCESS",
            "스터디 멤버 평가가 등록되었습니다."
    ),
    MEMBER_REVIEW_LIST_SUCCESS(
            "MEMBER_REVIEW_LIST_SUCCESS",
            "사용자 평가 목록 조회에 성공했습니다."
    );

    private final String code;
    private final String message;

    MemberReviewResponseCode(String code, String message) {
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
