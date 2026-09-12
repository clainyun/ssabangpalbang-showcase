package com.ssafy.ssabangpalbang.fieldvisit.stt.response;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum SttResponseCode implements ResponseCode {

    FIELD_STT_ACCEPTED(
            "FIELD_STT_ACCEPTED",
            "음성 변환을 시작했습니다."
    ),
    FIELD_STT_ALREADY_REQUESTED(
            "FIELD_STT_ALREADY_REQUESTED",
            "이미 접수된 음성 변환 요청입니다."
    ),
    FIELD_STT_STATUS_SUCCESS(
            "FIELD_STT_STATUS_SUCCESS",
            "음성 변환 상태 조회에 성공했습니다."
    ),
    FIELD_STT_RETRY_ACCEPTED(
            "FIELD_STT_RETRY_ACCEPTED",
            "음성 변환을 다시 시작했습니다."
    ),
    FIELD_STT_RETRY_ALREADY_IN_PROGRESS(
            "FIELD_STT_RETRY_ALREADY_IN_PROGRESS",
            "음성 변환 재처리가 이미 진행 중입니다."
    );

    private final String code;
    private final String message;

    SttResponseCode(String code, String message) {
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
