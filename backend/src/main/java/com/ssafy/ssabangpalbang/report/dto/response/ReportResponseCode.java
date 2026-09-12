package com.ssafy.ssabangpalbang.report.dto.response;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum ReportResponseCode implements ResponseCode {
    REPORT_DETAIL_SUCCESS(
            "REPORT_DETAIL_SUCCESS",
            "리포트 상세 조회에 성공했습니다."
    ),
    REPORT_EVIDENCE_LIST_SUCCESS(
            "REPORT_EVIDENCE_LIST_SUCCESS",
            "리포트 근거 목록 조회에 성공했습니다."
    ),
    REPORT_EVIDENCE_DETAIL_SUCCESS(
            "REPORT_EVIDENCE_DETAIL_SUCCESS",
            "리포트 근거 원문 조회에 성공했습니다."
    ),
    REPORT_STATUS_SUCCESS(
            "REPORT_STATUS_SUCCESS",
            "리포트 생성 상태 조회에 성공했습니다."
    ),
    REPORT_RETRY_ACCEPTED(
            "REPORT_RETRY_ACCEPTED",
            "리포트 재생성을 시작했습니다."
    ),
    REPORT_RETRY_ALREADY_IN_PROGRESS(
            "REPORT_RETRY_ALREADY_IN_PROGRESS",
            "리포트 재생성이 이미 진행 중입니다."
    ),
    REPORT_ACQUIRE_SUCCESS(
            "REPORT_ACQUIRE_SUCCESS",
            "리포트 처리권을 확인했습니다."
    ),
    REPORT_INPUT_SUCCESS(
            "REPORT_INPUT_SUCCESS",
            "리포트 입력을 조회했습니다."
    ),
    REPORT_FAVORITE_SUCCESS(
            "REPORT_FAVORITE_SUCCESS",
            "리포트를 찜했습니다."
    ),
    REPORT_FAVORITE_ALREADY_EXISTS(
            "REPORT_FAVORITE_ALREADY_EXISTS",
            "이미 찜한 리포트입니다."
    ),
    REPORT_UNFAVORITE_SUCCESS(
            "REPORT_UNFAVORITE_SUCCESS",
            "리포트 찜을 해제했습니다."
    ),
    REPORT_ALREADY_UNFAVORITED(
            "REPORT_ALREADY_UNFAVORITED",
            "이미 찜하지 않은 리포트입니다."
    );

    private final String code;
    private final String message;

    ReportResponseCode(String code, String message) {
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
