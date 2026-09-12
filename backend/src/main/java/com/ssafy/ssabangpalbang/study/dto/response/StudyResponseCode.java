package com.ssafy.ssabangpalbang.study.dto.response;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum StudyResponseCode implements ResponseCode {

    STUDY_CREATE_SUCCESS(
            "STUDY_CREATE_SUCCESS",
            "스터디가 생성되었습니다."
    ),
    STUDY_DETAIL_SUCCESS(
            "STUDY_DETAIL_SUCCESS",
            "스터디 상세 조회에 성공했습니다."
    ),
    STUDY_APPLICATION_CREATE_SUCCESS(
            "STUDY_APPLICATION_CREATE_SUCCESS",
            "스터디 참여를 신청했습니다."
    ),
    STUDY_APPLICATION_LIST_SUCCESS(
            "STUDY_APPLICATION_LIST_SUCCESS",
            "신청자 목록 조회에 성공했습니다."
    ),
    STUDY_APPLICATION_APPROVE_SUCCESS(
            "STUDY_APPLICATION_APPROVE_SUCCESS",
            "스터디 신청을 승인했습니다."
    ),
    STUDY_APPLICATION_REJECT_SUCCESS(
            "STUDY_APPLICATION_REJECT_SUCCESS",
            "스터디 신청을 거절했습니다."
    ),
    STUDY_MEMBER_LIST_SUCCESS(
            "STUDY_MEMBER_LIST_SUCCESS",
            "스터디 멤버 목록 조회에 성공했습니다."
    ),
    STUDY_NOTICE_CREATE_SUCCESS(
            "STUDY_NOTICE_CREATE_SUCCESS",
            "스터디 공지가 등록되었습니다."
    ),
    STUDY_NOTICE_LIST_SUCCESS(
            "STUDY_NOTICE_LIST_SUCCESS",
            "스터디 공지 목록 조회에 성공했습니다."
    ),
    STUDY_NOTICE_UPDATE_SUCCESS(
            "STUDY_NOTICE_UPDATE_SUCCESS",
            "스터디 공지가 수정되었습니다."
    ),
    STUDY_NOTICE_DELETE_SUCCESS(
            "STUDY_NOTICE_DELETE_SUCCESS",
            "스터디 공지가 삭제되었습니다."
    ),
    STUDY_SCHEDULE_CREATE_SUCCESS("STUDY_SCHEDULE_CREATE_SUCCESS", "임장 일정이 등록되었습니다."),
    STUDY_SCHEDULE_DETAIL_SUCCESS("STUDY_SCHEDULE_DETAIL_SUCCESS", "임장 일정 조회에 성공했습니다."),
    STUDY_SCHEDULE_UPDATE_SUCCESS("STUDY_SCHEDULE_UPDATE_SUCCESS", "임장 일정이 수정되었습니다."),
    STUDY_SCHEDULE_DELETE_SUCCESS("STUDY_SCHEDULE_DELETE_SUCCESS", "임장 일정이 삭제되었습니다."),
    STUDY_RECRUITMENT_CLOSE_SUCCESS("STUDY_RECRUITMENT_CLOSE_SUCCESS", "스터디 모집을 마감했습니다."),
    STUDY_RECRUITMENT_OPEN_SUCCESS("STUDY_RECRUITMENT_OPEN_SUCCESS", "스터디 모집을 재개했습니다."),
    STUDY_MEMBER_KICK_SUCCESS("STUDY_MEMBER_KICK_SUCCESS", "스터디 멤버를 강퇴했습니다."
    ),
    STUDY_MEMBER_LEAVE_SUCCESS("STUDY_MEMBER_LEAVE_SUCCESS", "스터디에서 나갔습니다."),
    STUDY_UPDATE_SUCCESS("STUDY_UPDATE_SUCCESS", "스터디 목표와 소개를 수정했습니다."),
    STUDY_CANCEL_SUCCESS("STUDY_CANCEL_SUCCESS", "스터디가 취소되었습니다."
    );

    private final String code;
    private final String message;

    StudyResponseCode(String code, String message) {
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
