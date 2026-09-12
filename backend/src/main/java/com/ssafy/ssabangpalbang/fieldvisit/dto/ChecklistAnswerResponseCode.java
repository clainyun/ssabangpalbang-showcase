package com.ssafy.ssabangpalbang.fieldvisit.dto;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum ChecklistAnswerResponseCode implements ResponseCode {

    CHECKLIST_COMPLETION_SAVE_SUCCESS(
            "CHECKLIST_COMPLETION_SAVE_SUCCESS",
            "체크리스트 완료 상태를 저장했습니다."
    );

    private final String code;
    private final String message;

    ChecklistAnswerResponseCode(String code, String message) {
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
