package com.ssafy.ssabangpalbang.fieldvisit.dto;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum FieldVisitResponseCode implements ResponseCode {

    FIELD_VISIT_STATUS_SUCCESS(
            "FIELD_VISIT_STATUS_SUCCESS",
            "임장 세션 상태 조회에 성공했습니다."
    ),
    FIELD_VISIT_PARTICIPANTS_SUCCESS(
            "FIELD_VISIT_PARTICIPANTS_SUCCESS",
            "참여자 임장 상태 조회에 성공했습니다."
    ),
    FIELD_VISIT_START_SUCCESS(
            "FIELD_VISIT_START_SUCCESS",
            "임장을 시작했습니다."
    ),
    FIELD_VISIT_ALREADY_STARTED(
            "FIELD_VISIT_ALREADY_STARTED",
            "이미 임장을 시작한 상태입니다."
    ),
    FIELD_VISIT_FINISH_SUCCESS(
            "FIELD_VISIT_FINISH_SUCCESS",
            "임장을 종료했습니다."
    ),
    FIELD_VISIT_FINISH_AND_SESSION_END_SUCCESS(
            "FIELD_VISIT_FINISH_AND_SESSION_END_SUCCESS",
            "임장을 종료하고 리포트 생성을 시작했습니다."
    ),
    /**
     * ErrorCode에 같은 code 문자열이 409로 존재한다.
     * 종료 재요청은 200 성공이며 의도된 중복이다.
     */
    FIELD_PARTICIPANT_ALREADY_ENDED(
            "FIELD_PARTICIPANT_ALREADY_ENDED",
            "이미 임장을 종료했습니다."
    ),
    FIELD_VISIT_FINISH_CANCEL_SUCCESS(
            "FIELD_VISIT_FINISH_CANCEL_SUCCESS",
            "개인 임장 종료를 취소하고 다시 진행 중으로 되돌렸습니다."
    ),
    FIELD_PARTICIPANT_ALREADY_IN_PROGRESS(
            "FIELD_PARTICIPANT_ALREADY_IN_PROGRESS",
            "이미 임장을 진행 중입니다."
    ),
    FIELD_VISIT_CLOSE_SUCCESS(
            "FIELD_VISIT_CLOSE_SUCCESS",
            "전체 임장을 마감하고 리포트 생성을 시작했습니다."
    ),
    FIELD_VISIT_ALREADY_CLOSED(
            "FIELD_VISIT_ALREADY_CLOSED",
            "이미 마감된 임장 세션입니다."
    ),
    FIELD_VISIT_CLOSE_VOTE_SUCCESS(
            "FIELD_VISIT_CLOSE_VOTE_SUCCESS",
            "전체 임장 종료 요청에 동의했습니다."
    ),
    FIELD_VISIT_CLOSE_VOTE_AND_SESSION_END_SUCCESS(
            "FIELD_VISIT_CLOSE_VOTE_AND_SESSION_END_SUCCESS",
            "과반수 동의로 전체 임장을 종료하고 리포트 생성을 시작했습니다."
    );

    private final String code;
    private final String message;

    FieldVisitResponseCode(String code, String message) {
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
