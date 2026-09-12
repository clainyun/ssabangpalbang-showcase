package com.ssafy.ssabangpalbang.member.response;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum MemberResponseCode implements ResponseCode {

    MEMBER_NOTIFICATION_SETTINGS_SUCCESS(
            "MEMBER_NOTIFICATION_SETTINGS_SUCCESS",
            "알림 수신 설정 조회에 성공했습니다."
    ),
    NOTIFICATION_SETTINGS_UPDATED(
            "MEMBER_NOTIFICATION_SETTINGS_UPDATED",
            "알림 수신 설정이 수정되었습니다."
    ),
    PROFILE_RETRIEVED(
            "MEMBER_PROFILE_SUCCESS",
            "내 프로필 조회에 성공했습니다."
    ),
    PUBLIC_PROFILE_RETRIEVED(
            "MEMBER_PUBLIC_PROFILE_SUCCESS",
            "사용자 프로필 조회에 성공했습니다."
    ),
    FOLLOWED(
            "MEMBER_FOLLOWED",
            "사용자를 팔로우했습니다."
    ),
    ALREADY_FOLLOWING(
            "MEMBER_ALREADY_FOLLOWING",
            "이미 팔로우 중인 사용자입니다."
    ),
    UNFOLLOWED(
            "MEMBER_UNFOLLOWED",
            "사용자 팔로우를 해제했습니다."
    ),
    ALREADY_UNFOLLOWED(
            "MEMBER_ALREADY_UNFOLLOWED",
            "이미 팔로우하지 않은 사용자입니다."
    ),
    FOLLOWING_LIST_RETRIEVED(
            "MEMBER_FOLLOWING_LIST_SUCCESS",
            "팔로잉 목록 조회에 성공했습니다."
    ),
    FAVORITE_APARTMENT_LIST_RETRIEVED(
            "MEMBER_FAVORITE_APARTMENT_LIST_SUCCESS",
            "찜한 아파트 목록 조회에 성공했습니다."
    ),
    FAVORITE_REPORT_LIST_RETRIEVED(
            "MEMBER_FAVORITE_REPORT_LIST_SUCCESS",
            "찜한 리포트 목록 조회에 성공했습니다."
    ),
    MESSAGE_SENT(
            "MEMBER_MESSAGE_SEND_SUCCESS",
            "쪽지를 보냈습니다."
    ),
    MESSAGE_ALREADY_SENT(
            "MEMBER_MESSAGE_ALREADY_SENT",
            "이미 전송된 쪽지입니다."
    ),
    PROFILE_UPDATED(
            "MEMBER_PROFILE_UPDATE_SUCCESS",
            "프로필이 수정되었습니다."
    ),
    WITHDRAWN(
            "MEMBER_WITHDRAW_SUCCESS",
            "회원 탈퇴가 완료되었습니다."
    ),
    REPORT_LIST_RETRIEVED(
            "MEMBER_REPORT_LIST_SUCCESS",
            "내 리포트 목록 조회에 성공했습니다."
    ),
    VISIT_CALENDAR_RETRIEVED(
            "MEMBER_VISIT_CALENDAR_SUCCESS",
            "월별 임장 달력 조회에 성공했습니다."
    ),
    STUDY_LIST_RETRIEVED(
            "MEMBER_STUDY_LIST_SUCCESS",
            "내 스터디 목록 조회에 성공했습니다."
    ),
    ONBOARDING_SAVED(
            "MEMBER_ONBOARDING_SAVED",
            "온보딩 정보가 저장되었습니다."
    ),
    FCM_TOKEN_REGISTERED(
            "MEMBER_FCM_TOKEN_SAVED",
            "기기 알림 정보가 등록되었습니다."
    ),
    FCM_TOKEN_UPDATED(
            "MEMBER_FCM_TOKEN_SAVED",
            "기기 알림 정보가 갱신되었습니다."
    ),
    FCM_TOKEN_DELETED(
            "MEMBER_FCM_TOKEN_DELETED",
            "기기 알림 연결이 해제되었습니다."
    ),
    FCM_TOKEN_ALREADY_DELETED(
            "MEMBER_FCM_TOKEN_ALREADY_DELETED",
            "이미 기기 알림 연결이 해제되어 있습니다."
    ),
    FCM_TEST_PUSH_SCHEDULED(
            "MEMBER_FCM_TEST_PUSH_SCHEDULED",
            "현재 기기의 FCM 테스트 알림을 예약했습니다."
    );

    private final String code;
    private final String message;

    MemberResponseCode(
            String code,
            String message
    ) {
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
