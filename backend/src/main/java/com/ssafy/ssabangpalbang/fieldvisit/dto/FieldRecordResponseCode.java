package com.ssafy.ssabangpalbang.fieldvisit.dto;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum FieldRecordResponseCode implements ResponseCode {

    FIELD_RECORD_CREATE_SUCCESS(
            "FIELD_RECORD_CREATE_SUCCESS",
            "현장 기록을 저장했습니다."
    ),
    FIELD_RECORD_ALREADY_CREATED(
            "FIELD_RECORD_ALREADY_CREATED",
            "이미 저장된 현장 기록입니다."
    ),
    FIELD_RECORD_LIST_SUCCESS(
            "FIELD_RECORD_LIST_SUCCESS",
            "현장 기록 목록 조회에 성공했습니다."
    ),
    FIELD_RECORD_UPDATE_SUCCESS(
            "FIELD_RECORD_UPDATE_SUCCESS",
            "현장 기록을 수정했습니다."
    ),
    FIELD_RECORD_DELETE_SUCCESS(
            "FIELD_RECORD_DELETE_SUCCESS",
            "현장 기록을 삭제했습니다."
    ),
    FIELD_RECORD_ALREADY_DELETED(
            "FIELD_RECORD_ALREADY_DELETED",
            "이미 삭제된 현장 기록입니다."
    );

    private final String code;
    private final String message;

    FieldRecordResponseCode(String code, String message) {
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
