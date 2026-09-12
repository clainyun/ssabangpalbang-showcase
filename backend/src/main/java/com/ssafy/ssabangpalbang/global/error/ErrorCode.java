package com.ssafy.ssabangpalbang.global.error;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;
import org.springframework.http.HttpStatus;

public enum ErrorCode implements ResponseCode {
    APARTMENT_BOUNDS_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "APARTMENT_BOUNDS_REQUIRED",
            "지도 영역 정보를 입력해 주세요."
    ),
    APARTMENT_BOUNDS_COORDINATE_INVALID(
            HttpStatus.BAD_REQUEST,
            "APARTMENT_BOUNDS_COORDINATE_INVALID",
            "지도 좌표가 올바르지 않습니다."
    ),
    APARTMENT_BOUNDS_ORDER_INVALID(
            HttpStatus.BAD_REQUEST,
            "APARTMENT_BOUNDS_ORDER_INVALID",
            "지도 영역의 남서쪽과 북동쪽 좌표를 확인해 주세요."
    ),
    APARTMENT_BOUNDS_TOO_LARGE(
            HttpStatus.BAD_REQUEST,
            "APARTMENT_BOUNDS_TOO_LARGE",
            "지도 영역을 조금 더 확대해 주세요."
    ),

    INVALID_INPUT_VALUE(
            HttpStatus.BAD_REQUEST,
            "COMMON_INVALID_REQUEST",
            "입력값을 확인해 주세요."
    ),
    INVALID_CURSOR(
            HttpStatus.BAD_REQUEST,
            "COMMON_INVALID_CURSOR",
            "유효하지 않은 페이지 커서입니다."
    ),
    NOTIFICATION_CURSOR_INVALID(
            HttpStatus.BAD_REQUEST,
            "NOTIFICATION_CURSOR_INVALID",
            "알림 목록 커서가 올바르지 않습니다."
    ),
    NOTIFICATION_ID_INVALID(
            HttpStatus.BAD_REQUEST,
            "NOTIFICATION_ID_INVALID",
            "알림 ID가 올바르지 않습니다."
    ),
    NOTIFICATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "NOTIFICATION_NOT_FOUND",
            "알림을 찾을 수 없습니다."
    ),
    AUTH_EMAIL_DUPLICATED(
            HttpStatus.CONFLICT,
            "AUTH_EMAIL_DUPLICATED",
            "이미 사용 중인 이메일입니다."
    ),
    AUTH_NICKNAME_DUPLICATED(
            HttpStatus.CONFLICT,
            "AUTH_NICKNAME_DUPLICATED",
            "이미 사용 중인 닉네임입니다."
    ),
    AUTH_LOGIN_FAILED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_LOGIN_FAILED",
            "이메일 또는 비밀번호를 확인해 주세요."
    ),
    AUTH_MEMBER_WITHDRAWN(
            HttpStatus.FORBIDDEN,
            "AUTH_MEMBER_WITHDRAWN",
            "탈퇴 처리된 회원입니다."
    ),
    AUTH_PASSWORD_LOGIN_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "AUTH_PASSWORD_LOGIN_NOT_AVAILABLE",
            "간편 로그인을 이용해 주세요."
    ),
    AUTH_PASSWORD_RESET_CODE_INVALID(
            HttpStatus.BAD_REQUEST,
            "AUTH_PASSWORD_RESET_CODE_INVALID",
            "인증 코드가 올바르지 않거나 만료되었습니다."
    ),
    AUTH_SOCIAL_PROVIDER_INVALID(
            HttpStatus.BAD_REQUEST,
            "AUTH_SOCIAL_PROVIDER_INVALID",
            "지원하지 않는 간편 로그인 제공자입니다."
    ),
    AUTH_SOCIAL_AUTHENTICATION_FAILED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_SOCIAL_AUTHENTICATION_FAILED",
            "간편 로그인 인증에 실패했습니다."
    ),
    AUTH_SOCIAL_PROVIDER_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "AUTH_SOCIAL_PROVIDER_UNAVAILABLE",
            "간편 로그인 서비스에 일시적인 오류가 발생했습니다."
    ),
    AUTH_SOCIAL_SIGNUP_TOKEN_INVALID(
            HttpStatus.BAD_REQUEST,
            "AUTH_SOCIAL_SIGNUP_TOKEN_INVALID",
            "소셜 회원가입 정보가 유효하지 않습니다."
    ),
    AUTH_SOCIAL_EMAIL_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "AUTH_SOCIAL_EMAIL_REQUIRED",
            "회원가입에 사용할 이메일을 입력해 주세요."
    ),
    AUTH_SOCIAL_SIGNUP_TOKEN_EXPIRED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_SOCIAL_SIGNUP_TOKEN_EXPIRED",
            "소셜 인증 정보가 만료되었습니다. 다시 로그인해 주세요."
    ),
    AUTH_SOCIAL_ACCOUNT_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "AUTH_SOCIAL_ACCOUNT_ALREADY_EXISTS",
            "이미 가입된 간편 로그인 계정입니다."
    ),
    AUTH_REFRESH_TOKEN_INVALID(
            HttpStatus.UNAUTHORIZED,
            "AUTH_REFRESH_TOKEN_INVALID",
            "유효하지 않은 Refresh Token입니다."
    ),
    AUTH_REFRESH_TOKEN_EXPIRED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_REFRESH_TOKEN_EXPIRED",
            "로그인이 만료되었습니다. 다시 로그인해 주세요."
    ),
    AUTH_REFRESH_TOKEN_REVOKED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_REFRESH_TOKEN_REVOKED",
            "사용할 수 없는 Refresh Token입니다."
    ),
    AUTH_REFRESH_TOKEN_MEMBER_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "AUTH_REFRESH_TOKEN_MEMBER_MISMATCH",
            "로그아웃 요청 정보가 올바르지 않습니다."
    ),
    AUTH_ACCESS_TOKEN_EXPIRED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_ACCESS_TOKEN_EXPIRED",
            "Access Token이 만료되었습니다."
    ),
    MEMBER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MEMBER_NOT_FOUND",
            "회원 정보를 찾을 수 없습니다."
    ),
    MEMBER_WITHDRAW_CONFIRMATION_INVALID(
            HttpStatus.BAD_REQUEST,
            "MEMBER_WITHDRAW_CONFIRMATION_INVALID",
            "회원 탈퇴 확인 문구가 일치하지 않습니다."
    ),
    MEMBER_WITHDRAWAL_FIELD_SESSION_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "MEMBER_WITHDRAWAL_FIELD_SESSION_IN_PROGRESS",
            "진행 중인 임장을 종료한 후 회원 탈퇴를 진행해 주세요."
    ),
    MEMBER_WITHDRAWAL_ACTIVE_STUDY_LEADER(
            HttpStatus.CONFLICT,
            "MEMBER_WITHDRAWAL_ACTIVE_STUDY_LEADER",
            "운영 중인 스터디를 정리한 후 회원 탈퇴를 진행해 주세요."
    ),
    MEMBER_ALREADY_WITHDRAWN(
            HttpStatus.CONFLICT,
            "MEMBER_ALREADY_WITHDRAWN",
            "이미 탈퇴 처리된 회원입니다."
    ),
    MEMBER_SELF_FOLLOW_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST,
            "MEMBER_SELF_FOLLOW_NOT_ALLOWED",
            "본인은 팔로우할 수 없습니다."
    ),
    MEMBER_SELF_UNFOLLOW_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST,
            "MEMBER_SELF_UNFOLLOW_NOT_ALLOWED",
            "본인을 팔로우 해제 대상으로 지정할 수 없습니다."
    ),
    MEMBER_FOLLOWING_CURSOR_INVALID(
            HttpStatus.BAD_REQUEST,
            "MEMBER_FOLLOWING_CURSOR_INVALID",
            "팔로잉 목록 커서가 올바르지 않습니다."
    ),
    MEMBER_MESSAGE_SELF_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST,
            "MEMBER_MESSAGE_SELF_NOT_ALLOWED",
            "본인에게 쪽지를 보낼 수 없습니다."
    ),
    MEMBER_MESSAGE_CONTENT_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "MEMBER_MESSAGE_CONTENT_REQUIRED",
            "쪽지 내용을 입력해 주세요."
    ),
    MEMBER_MESSAGE_CONTENT_TOO_LONG(
            HttpStatus.BAD_REQUEST,
            "MEMBER_MESSAGE_CONTENT_TOO_LONG",
            "쪽지는 500자 이하로 입력해 주세요."
    ),
    MEMBER_MESSAGE_FOLLOW_REQUIRED(
            HttpStatus.FORBIDDEN,
            "MEMBER_MESSAGE_FOLLOW_REQUIRED",
            "팔로잉 중인 사용자에게만 쪽지를 보낼 수 있습니다."
    ),
    MEMBER_VISIT_CALENDAR_YEAR_INVALID(
            HttpStatus.BAD_REQUEST,
            "MEMBER_VISIT_CALENDAR_YEAR_INVALID",
            "조회할 수 없는 연도입니다."
    ),
    MEMBER_VISIT_CALENDAR_MONTH_INVALID(
            HttpStatus.BAD_REQUEST,
            "MEMBER_VISIT_CALENDAR_MONTH_INVALID",
            "조회할 수 없는 월입니다."
    ),
    MEMBER_STUDY_STATUS_INVALID(
            HttpStatus.BAD_REQUEST,
            "MEMBER_STUDY_STATUS_INVALID",
            "조회할 수 없는 내 스터디 상태입니다."
    ),
    MEMBER_ONBOARDING_REQUIRED_FIELD_MISSING(
            HttpStatus.BAD_REQUEST,
            "MEMBER_ONBOARDING_REQUIRED_FIELD_MISSING",
            "온보딩 필수 항목을 모두 선택해 주세요."
    ),
    MEMBER_CHARACTER_INVALID(
            HttpStatus.BAD_REQUEST,
            "MEMBER_CHARACTER_INVALID",
            "선택할 수 없는 캐릭터입니다."
    ),
    MEMBER_AGE_GROUP_INVALID(
            HttpStatus.BAD_REQUEST,
            "MEMBER_AGE_GROUP_INVALID",
            "선택할 수 없는 연령대입니다."
    ),
    MEMBER_PROFILE_UPDATE_EMPTY(
            HttpStatus.BAD_REQUEST,
            "MEMBER_PROFILE_UPDATE_EMPTY",
            "수정할 내 정보를 입력해 주세요."
    ),
    MEMBER_NOTIFICATION_SETTINGS_UPDATE_EMPTY(
            HttpStatus.BAD_REQUEST,
            "MEMBER_NOTIFICATION_SETTINGS_UPDATE_EMPTY",
            "변경할 알림 설정을 입력해 주세요."
    ),
    MEMBER_PRIORITY_DUPLICATED(
            HttpStatus.BAD_REQUEST,
            "MEMBER_PRIORITY_DUPLICATED",
            "같은 우선순위를 중복해서 선택할 수 없습니다."
    ),
    HOME_LOCATION_INCOMPLETE(
            HttpStatus.BAD_REQUEST,
            "HOME_LOCATION_INCOMPLETE",
            "위치 정보를 확인해 주세요."
    ),
    HOME_LATITUDE_INVALID(
            HttpStatus.BAD_REQUEST,
            "HOME_LATITUDE_INVALID",
            "위도 값이 올바르지 않습니다."
    ),
    HOME_LONGITUDE_INVALID(
            HttpStatus.BAD_REQUEST,
            "HOME_LONGITUDE_INVALID",
            "경도 값이 올바르지 않습니다."
    ),
    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_ACCESS_TOKEN_INVALID",
            "로그인이 필요합니다."
    ),
    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "COMMON_FORBIDDEN",
            "요청을 수행할 권한이 없습니다."
    ),
    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COMMON_RESOURCE_NOT_FOUND",
            "요청한 리소스를 찾을 수 없습니다."
    ),
    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "COMMON_METHOD_NOT_ALLOWED",
            "지원하지 않는 HTTP 메서드입니다."
    ),
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "COMMON_INTERNAL_SERVER_ERROR",
            "일시적인 오류가 발생했습니다."
    ),

    POST_BOARD_TYPE_INVALID(
            HttpStatus.BAD_REQUEST,
            "POST_BOARD_TYPE_INVALID",
            "게시판 유형을 확인해 주세요."
    ),
    POST_SORT_INVALID(
            HttpStatus.BAD_REQUEST,
            "POST_SORT_INVALID",
            "게시글 정렬 기준을 확인해 주세요."
    ),
    POST_ATTACHMENT_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "POST_ATTACHMENT_LIMIT_EXCEEDED",
            "첨부 파일은 최대 10개까지 등록할 수 있습니다."
    ),
    POST_UPDATE_EMPTY(
            HttpStatus.BAD_REQUEST,
            "POST_UPDATE_EMPTY",
            "수정할 내용을 입력해 주세요."
    ),
    POST_UPDATE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "POST_UPDATE_FORBIDDEN",
            "게시글을 수정할 권한이 없습니다."
    ),
    POST_AUTO_REPORT_UPDATE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "POST_AUTO_REPORT_UPDATE_FORBIDDEN",
            "자동 생성된 리포트 게시글은 수정할 수 없습니다."
    ),
    POST_DELETE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "POST_DELETE_FORBIDDEN",
            "게시글을 삭제할 권한이 없습니다."
    ),
    POST_AUTO_REPORT_DELETE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "POST_AUTO_REPORT_DELETE_FORBIDDEN",
            "자동 생성된 리포트 게시글은 직접 삭제할 수 없습니다."
    ),
    POST_ATTACHMENT_ALREADY_USED(
            HttpStatus.CONFLICT,
            "POST_ATTACHMENT_ALREADY_USED",
            "이미 다른 게시글에 사용된 첨부 파일입니다."
    ),
    POST_STATUS_CONFLICT(
            HttpStatus.CONFLICT,
            "POST_STATUS_CONFLICT",
            "현재 상태에서는 게시글을 수정할 수 없습니다."
    ),
    POST_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "POST_NOT_FOUND",
            "게시글을 찾을 수 없습니다."
    ),
    COMMENT_DELETE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "COMMENT_DELETE_FORBIDDEN",
            "댓글을 삭제할 권한이 없습니다."
    ),
    COMMENT_UPDATE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "COMMENT_UPDATE_FORBIDDEN",
            "댓글을 수정할 권한이 없습니다."
    ),
    COMMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COMMENT_NOT_FOUND",
            "댓글을 찾을 수 없습니다."
    ),
    COMMENT_ALREADY_DELETED(
            HttpStatus.CONFLICT,
            "COMMENT_ALREADY_DELETED",
            "이미 삭제된 댓글입니다."
    ),
    MEDIA_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "MEDIA_ACCESS_DENIED",
            "사용할 수 없는 첨부 파일입니다."
    ),
    MEDIA_FILE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MEDIA_FILE_NOT_FOUND",
            "첨부 파일을 찾을 수 없습니다."
    ),
    MEDIA_FILE_USAGE_INVALID(
            HttpStatus.BAD_REQUEST,
            "MEDIA_FILE_USAGE_INVALID",
            "지원하지 않는 파일 사용 목적입니다."
    ),
    MEDIA_CONTENT_TYPE_INVALID(
            HttpStatus.BAD_REQUEST,
            "MEDIA_CONTENT_TYPE_INVALID",
            "지원하지 않는 파일 형식입니다."
    ),
    MEDIA_FILE_SIZE_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "MEDIA_FILE_SIZE_EXCEEDED",
            "파일 크기가 허용 범위를 초과했습니다."
    ),
    MEDIA_SIZE_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "MEDIA_SIZE_MISMATCH",
            "업로드한 파일 크기가 요청 정보와 일치하지 않습니다."
    ),
    MEDIA_STUDY_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "MEDIA_STUDY_FORBIDDEN",
            "해당 스터디에 파일을 업로드할 권한이 없습니다."
    ),
    MEDIA_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "MEDIA_FORBIDDEN",
            "해당 파일에 접근할 권한이 없습니다."
    ),
    MEDIA_NOT_OWNER(
            HttpStatus.FORBIDDEN,
            "MEDIA_NOT_OWNER",
            "해당 파일을 처리할 권한이 없습니다."
    ),
    MEDIA_OBJECT_NOT_FOUND(
            HttpStatus.CONFLICT,
            "MEDIA_OBJECT_NOT_FOUND",
            "업로드된 파일을 확인할 수 없습니다."
    ),
    MEDIA_UPLOAD_EXPIRED(
            HttpStatus.CONFLICT,
            "MEDIA_UPLOAD_EXPIRED",
            "업로드 가능 시간이 만료되었습니다. 다시 업로드해 주세요."
    ),
    MEDIA_UPLOAD_ALREADY_FAILED(
            HttpStatus.CONFLICT,
            "MEDIA_UPLOAD_ALREADY_FAILED",
            "실패 처리된 파일입니다. 새로운 업로드 URL을 발급받아 주세요."
    ),
    MEDIA_UPLOAD_CHANGED(
            HttpStatus.CONFLICT,
            "MEDIA_UPLOAD_CHANGED",
            "검증 중 업로드된 파일이 변경되었습니다. 다시 업로드해 주세요."
    ),
    MEDIA_GATEWAY_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "MEDIA_GATEWAY_UNAVAILABLE",
            "파일 저장소에 일시적으로 연결할 수 없습니다."
    ),

    APARTMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "APARTMENT_NOT_FOUND",
            "아파트를 찾을 수 없습니다."
    ),
    APARTMENT_ID_INVALID(
            HttpStatus.BAD_REQUEST,
            "APARTMENT_ID_INVALID",
            "아파트 ID가 올바르지 않습니다."
    ),
    APARTMENT_TRANSACTION_AREA_INVALID(
            HttpStatus.BAD_REQUEST,
            "APARTMENT_TRANSACTION_AREA_INVALID",
            "전용면적을 확인해 주세요."
    ),
    APARTMENT_TRANSACTION_YEAR_INVALID(
            HttpStatus.BAD_REQUEST,
            "APARTMENT_TRANSACTION_YEAR_INVALID",
            "조회할 연도를 확인해 주세요."
    ),
    APARTMENT_TRANSACTION_SORT_INVALID(
            HttpStatus.BAD_REQUEST,
            "APARTMENT_TRANSACTION_SORT_INVALID",
            "정렬 기준을 확인해 주세요."
    ),
    APARTMENT_STUDY_SORT_INVALID(
            HttpStatus.BAD_REQUEST,
            "APARTMENT_STUDY_SORT_INVALID",
            "정렬 기준을 확인해 주세요."
    ),

    // BE-026: 아파트 챗봇 게이트웨이
    CHATBOT_CONVERSATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CHATBOT_CONVERSATION_NOT_FOUND",
            "챗봇 대화를 찾을 수 없습니다."
    ),
    CHATBOT_CONVERSATION_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "CHATBOT_CONVERSATION_ACCESS_DENIED",
            "본인의 챗봇 대화만 조회할 수 있습니다."
    ),
    CHATBOT_APARTMENT_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "CHATBOT_APARTMENT_MISMATCH",
            "대화와 아파트 정보가 일치하지 않습니다."
    ),
    CHATBOT_RESPONSE_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "CHATBOT_RESPONSE_IN_PROGRESS",
            "이전 답변을 생성하는 중입니다. 잠시 후 다시 시도해 주세요."
    ),
    APARTMENT_FILTER_REQUIRED(HttpStatus.BAD_REQUEST, "APARTMENT_FILTER_REQUIRED", "지역 또는 검색어를 입력해 주세요."),
    APARTMENT_LOCATION_INVALID(HttpStatus.BAD_REQUEST, "APARTMENT_LOCATION_INVALID", "위치 정보가 올바르지 않습니다."),
    APARTMENT_RADIUS_INVALID(HttpStatus.BAD_REQUEST, "APARTMENT_RADIUS_INVALID", "검색 반경이 올바르지 않습니다."),
    REGION_DISTRICT_CODE_INVALID(HttpStatus.BAD_REQUEST, "REGION_DISTRICT_CODE_INVALID", "자치구 코드가 올바르지 않습니다."),
    REGION_DONG_CODE_INVALID(HttpStatus.BAD_REQUEST, "REGION_DONG_CODE_INVALID", "법정동 코드가 올바르지 않습니다."),
    REGION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "REGION_CODE_MISMATCH", "자치구와 법정동 정보가 일치하지 않습니다."),
    REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "REGION_NOT_FOUND", "지역 정보를 찾을 수 없습니다."),
    REGION_OUT_OF_SERVICE_AREA(HttpStatus.BAD_REQUEST, "REGION_OUT_OF_SERVICE_AREA", "현재 서울 지역만 지원합니다."),
    REGION_DISTRICT_NOT_FOUND(HttpStatus.NOT_FOUND, "REGION_DISTRICT_NOT_FOUND", "자치구 정보를 찾을 수 없습니다."),
    REGION_DATA_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "REGION_DATA_UNAVAILABLE", "지역 정보를 불러올 수 없습니다."),

    REPORT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "REPORT_NOT_FOUND",
            "리포트를 찾을 수 없습니다."
    ),
    REPORT_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "REPORT_ACCESS_DENIED",
            "해당 리포트에 접근할 권한이 없습니다."
    ),
    REPORT_NOT_DONE(
            HttpStatus.CONFLICT,
            "REPORT_NOT_DONE",
            "아직 생성이 완료되지 않은 리포트입니다."
    ),
    REPORT_GENERATION_FAILED(
            HttpStatus.CONFLICT,
            "REPORT_GENERATION_FAILED",
            "생성에 실패한 리포트입니다."
    ),
    REPORT_RETRY_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "REPORT_RETRY_ACCESS_DENIED",
            "리포트를 재생성할 권한이 없습니다."
    ),
    REPORT_RETRY_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "REPORT_RETRY_NOT_ALLOWED",
            "재생성할 수 없는 리포트 상태입니다."
    ),
    REPORT_RETRY_NOT_RETRYABLE(
            HttpStatus.CONFLICT,
            "REPORT_RETRY_NOT_RETRYABLE",
            "현재 리포트는 다시 생성할 수 없습니다."
    ),
    REPORT_SOURCE_DATA_INSUFFICIENT(
            HttpStatus.CONFLICT,
            "REPORT_SOURCE_DATA_INSUFFICIENT",
            "리포트를 생성하기 위한 임장 기록이 부족합니다."
    ),
    REPORT_EVIDENCE_SOURCE_TYPE_INVALID(
            HttpStatus.BAD_REQUEST,
            "REPORT_EVIDENCE_SOURCE_TYPE_INVALID",
            "유효하지 않은 근거 유형입니다."
    ),
    REPORT_EVIDENCE_REPORT_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "REPORT_EVIDENCE_REPORT_MISMATCH",
            "요청한 근거가 해당 리포트와 일치하지 않습니다."
    ),
    REPORT_EVIDENCE_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "REPORT_EVIDENCE_ACCESS_DENIED",
            "리포트 원문 근거를 조회할 권한이 없습니다."
    ),
    REPORT_EVIDENCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "REPORT_EVIDENCE_NOT_FOUND",
            "리포트 근거를 찾을 수 없습니다."
    ),
    REPORT_EVIDENCE_MEDIA_UNAVAILABLE(
            HttpStatus.GONE,
            "REPORT_EVIDENCE_MEDIA_UNAVAILABLE",
            "근거 사진 파일을 더 이상 확인할 수 없습니다."
    ),
    REPORT_FAVORITE_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "REPORT_FAVORITE_ACCESS_DENIED",
            "해당 리포트를 찜할 권한이 없습니다."
    ),
    REPORT_FAVORITE_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "REPORT_FAVORITE_NOT_ALLOWED",
            "생성이 완료된 리포트만 찜할 수 있습니다."
    ),
    REPORT_INTERNAL_AUTHENTICATION_FAILED(
            HttpStatus.UNAUTHORIZED,
            "REPORT_INTERNAL_AUTHENTICATION_FAILED",
            "내부 API 인증이 필요합니다."
    ),
    REPORT_INTERNAL_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "REPORT_INTERNAL_ACCESS_DENIED",
            "내부 API에 접근할 권한이 없습니다."
    ),
    STALE_PROCESSING_TOKEN(
            HttpStatus.CONFLICT,
            "STALE_PROCESSING_TOKEN",
            "리포트 처리 상태가 충돌합니다."
    ),
    COMPLETE_PAYLOAD_CONFLICT(
            HttpStatus.CONFLICT,
            "COMPLETE_PAYLOAD_CONFLICT",
            "이미 저장된 리포트 결과와 일치하지 않습니다."
    ),
    FAIL_PAYLOAD_CONFLICT(
            HttpStatus.CONFLICT,
            "FAIL_PAYLOAD_CONFLICT",
            "이미 저장된 리포트 실패 정보와 일치하지 않습니다."
    ),

    MEMBER_DEVICE_ID_INVALID(
            HttpStatus.BAD_REQUEST,
            "MEMBER_DEVICE_ID_INVALID",
            "기기 식별 정보가 올바르지 않습니다."
    ),
    MEMBER_FCM_TOKEN_INVALID(
            HttpStatus.BAD_REQUEST,
            "MEMBER_FCM_TOKEN_INVALID",
            "FCM 토큰 정보가 올바르지 않습니다."
    ),
    MEMBER_FCM_TOKEN_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MEMBER_FCM_TOKEN_NOT_FOUND",
            "현재 기기에 등록된 FCM 토큰을 찾을 수 없습니다."
    ),
    FCM_PUSH_NOT_CONFIGURED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "FCM_PUSH_NOT_CONFIGURED",
            "FCM 알림 발송 설정이 준비되지 않았습니다."
    ),
    FCM_PUSH_DELIVERY_FAILED(
            HttpStatus.BAD_GATEWAY,
            "FCM_PUSH_DELIVERY_FAILED",
            "FCM 알림 전송에 실패했습니다."
    ),
    FCM_PUSH_SCHEDULING_FAILED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "FCM_PUSH_SCHEDULING_FAILED",
            "FCM 알림 예약을 처리할 수 없습니다."
    ),
    MEMBER_FCM_TOKEN_LOCK_TIMEOUT(
            HttpStatus.SERVICE_UNAVAILABLE,
            "MEMBER_FCM_TOKEN_LOCK_TIMEOUT",
            "기기 알림 정보를 처리 중입니다. 잠시 후 다시 시도해 주세요."
    ),

    STUDY_CAPACITY_INVALID(
            HttpStatus.BAD_REQUEST,
            "STUDY_CAPACITY_INVALID",
            "최대 참여 인원을 확인해 주세요."
    ),
    STUDY_PURPOSE_INVALID(
            HttpStatus.BAD_REQUEST,
            "STUDY_PURPOSE_INVALID",
            "스터디 목적을 확인해 주세요."
    ),
    STUDY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "STUDY_NOT_FOUND",
            "스터디를 찾을 수 없습니다."
    ),
    STUDY_APPLICATION_PURPOSE_INVALID(
            HttpStatus.BAD_REQUEST,
            "STUDY_APPLICATION_PURPOSE_INVALID",
            "스터디 신청 목적이 올바르지 않습니다."
    ),
    STUDY_APPLICATION_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "STUDY_APPLICATION_ALREADY_EXISTS",
            "이미 신청한 스터디입니다."
    ),
    STUDY_ALREADY_MEMBER(
            HttpStatus.CONFLICT,
            "STUDY_ALREADY_MEMBER",
            "이미 참여 중인 스터디입니다."
    ),
    STUDY_APPLICATION_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "STUDY_APPLICATION_NOT_ALLOWED",
            "현재 신청할 수 없는 스터디입니다."
    ),
    STUDY_CAPACITY_FULL(
            HttpStatus.CONFLICT,
            "STUDY_CAPACITY_FULL",
            "스터디 정원이 가득 찼습니다."
    ),
    STUDY_APPLICATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "STUDY_APPLICATION_NOT_FOUND",
            "스터디 신청 정보를 찾을 수 없습니다."),
    STUDY_APPLICATION_STUDY_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "STUDY_APPLICATION_STUDY_MISMATCH",
            "해당 스터디의 신청 정보가 아닙니다."),
    STUDY_APPLICATION_STATUS_INVALID(
            HttpStatus.BAD_REQUEST,
            "STUDY_APPLICATION_STATUS_INVALID",
            "신청 상태 값이 올바르지 않습니다."),
    STUDY_APPLICATION_ALREADY_PROCESSED(
            HttpStatus.CONFLICT,
            "STUDY_APPLICATION_ALREADY_PROCESSED",
            "이미 처리된 스터디 신청입니다."),
    STUDY_APPLICATION_LIST_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "STUDY_APPLICATION_LIST_FORBIDDEN",
            "신청자 목록을 조회할 권한이 없습니다."),
    STUDY_APPLICATION_APPROVE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "STUDY_APPLICATION_APPROVE_FORBIDDEN",
            "스터디 신청을 승인할 권한이 없습니다."),
    STUDY_APPLICATION_APPROVE_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "STUDY_APPLICATION_APPROVE_NOT_ALLOWED",
            "현재 상태에서는 신청을 승인할 수 없습니다."),
    STUDY_APPLICATION_REJECT_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "STUDY_APPLICATION_REJECT_FORBIDDEN",
            "스터디 신청을 거절할 권한이 없습니다."),
    STUDY_APPLICATION_REJECT_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "STUDY_APPLICATION_REJECT_NOT_ALLOWED",
            "현재 상태에서는 신청을 거절할 수 없습니다."),
    STUDY_MEMBER_LIST_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "STUDY_MEMBER_LIST_FORBIDDEN",
            "스터디 멤버 목록을 조회할 권한이 없습니다."
    ),
    STUDY_RECRUITMENT_CLOSE_FORBIDDEN(HttpStatus.FORBIDDEN, "STUDY_RECRUITMENT_CLOSE_FORBIDDEN", "스터디 모집을 마감할 권한이 없습니다."),
    STUDY_RECRUITMENT_CLOSE_NOT_ALLOWED(HttpStatus.CONFLICT, "STUDY_RECRUITMENT_CLOSE_NOT_ALLOWED", "현재 상태에서는 모집을 마감할 수 없습니다."),
    STUDY_RECRUITMENT_ALREADY_CLOSED(HttpStatus.CONFLICT, "STUDY_RECRUITMENT_ALREADY_CLOSED", "이미 모집이 마감된 스터디입니다."),
    STUDY_RECRUITMENT_OPEN_FORBIDDEN(HttpStatus.FORBIDDEN, "STUDY_RECRUITMENT_OPEN_FORBIDDEN", "스터디 모집을 재개할 권한이 없습니다."),
    STUDY_RECRUITMENT_OPEN_NOT_ALLOWED(HttpStatus.CONFLICT, "STUDY_RECRUITMENT_OPEN_NOT_ALLOWED", "현재 상태에서는 모집을 재개할 수 없습니다."),
    STUDY_RECRUITMENT_ALREADY_OPEN(HttpStatus.CONFLICT, "STUDY_RECRUITMENT_ALREADY_OPEN", "이미 모집 중인 스터디입니다."),
    STUDY_MEMBER_KICK_FORBIDDEN(HttpStatus.FORBIDDEN, "STUDY_MEMBER_KICK_FORBIDDEN", "스터디 멤버를 강퇴할 권한이 없습니다."),
    STUDY_MEMBER_KICK_NOT_ALLOWED(HttpStatus.CONFLICT, "STUDY_MEMBER_KICK_NOT_ALLOWED", "현재 상태에서는 멤버를 강퇴할 수 없습니다."),
    STUDY_LEADER_CANNOT_BE_KICKED(HttpStatus.BAD_REQUEST, "STUDY_LEADER_CANNOT_BE_KICKED", "스터디장은 강퇴할 수 없습니다."),
    STUDY_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "STUDY_MEMBER_NOT_FOUND", "스터디 멤버를 찾을 수 없습니다."),
    STUDY_LEADER_CANNOT_LEAVE(HttpStatus.CONFLICT, "STUDY_LEADER_CANNOT_LEAVE", "스터디장은 스터디를 나갈 수 없습니다."),
    STUDY_MEMBER_LEAVE_NOT_ALLOWED(HttpStatus.CONFLICT, "STUDY_MEMBER_LEAVE_NOT_ALLOWED", "현재 상태에서는 스터디를 나갈 수 없습니다."),
    STUDY_UPDATE_EMPTY(HttpStatus.BAD_REQUEST, "STUDY_UPDATE_EMPTY", "수정할 스터디 목표나 소개를 입력해 주세요."),
    STUDY_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN, "STUDY_UPDATE_FORBIDDEN", "스터디 목표와 소개를 수정할 권한이 없습니다."),
    STUDY_UPDATE_NOT_ALLOWED(HttpStatus.CONFLICT, "STUDY_UPDATE_NOT_ALLOWED", "현재 상태에서는 스터디 목표와 소개를 수정할 수 없습니다."),
    STUDY_CANCEL_FORBIDDEN(HttpStatus.FORBIDDEN, "STUDY_CANCEL_FORBIDDEN", "스터디를 취소할 권한이 없습니다."),
    STUDY_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "STUDY_CANCEL_NOT_ALLOWED", "현재 상태에서는 스터디를 취소할 수 없습니다."),
    STUDY_ALREADY_CANCELED(HttpStatus.CONFLICT, "STUDY_ALREADY_CANCELED", "이미 취소된 스터디입니다."),
    STUDY_NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "STUDY_NOTICE_NOT_FOUND", "스터디 공지를 찾을 수 없습니다."),
    STUDY_NOTICE_STUDY_MISMATCH(HttpStatus.BAD_REQUEST, "STUDY_NOTICE_STUDY_MISMATCH", "해당 스터디의 공지가 아닙니다."),
    STUDY_NOTICE_CREATE_FORBIDDEN(HttpStatus.FORBIDDEN, "STUDY_NOTICE_CREATE_FORBIDDEN", "스터디 공지를 등록할 권한이 없습니다."),
    STUDY_NOTICE_LIST_FORBIDDEN(HttpStatus.FORBIDDEN, "STUDY_NOTICE_LIST_FORBIDDEN", "스터디 공지를 조회할 권한이 없습니다."),
    STUDY_NOTICE_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN, "STUDY_NOTICE_UPDATE_FORBIDDEN", "스터디 공지를 수정할 권한이 없습니다."),
    STUDY_NOTICE_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "STUDY_NOTICE_DELETE_FORBIDDEN", "스터디 공지를 삭제할 권한이 없습니다."),
    STUDY_NOTICE_ALREADY_DELETED(HttpStatus.CONFLICT, "STUDY_NOTICE_ALREADY_DELETED", "이미 삭제된 공지입니다."),
    STUDY_NOTICE_CREATE_NOT_ALLOWED(HttpStatus.CONFLICT, "STUDY_NOTICE_CREATE_NOT_ALLOWED", "현재 스터디 상태에서는 공지를 등록할 수 없습니다."),
    STUDY_NOTICE_UPDATE_NOT_ALLOWED(HttpStatus.CONFLICT, "STUDY_NOTICE_UPDATE_NOT_ALLOWED", "현재 스터디 상태에서는 공지를 수정할 수 없습니다."),
    STUDY_NOTICE_DELETE_NOT_ALLOWED(HttpStatus.CONFLICT, "STUDY_NOTICE_DELETE_NOT_ALLOWED", "현재 스터디 상태에서는 공지를 삭제할 수 없습니다."),
    STUDY_NOTICE_UPDATE_EMPTY(HttpStatus.BAD_REQUEST, "STUDY_NOTICE_UPDATE_EMPTY", "수정할 내용이 없습니다."),
    MEMBER_REVIEW_SELF_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST,
            "MEMBER_REVIEW_SELF_NOT_ALLOWED",
            "본인은 평가할 수 없습니다."
    ),
    MEMBER_REVIEW_CREATE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "MEMBER_REVIEW_CREATE_FORBIDDEN",
            "같은 스터디의 멤버만 평가할 수 있습니다."
    ),
    MEMBER_REVIEW_TARGET_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MEMBER_REVIEW_TARGET_NOT_FOUND",
            "평가할 수 있는 스터디 멤버를 찾을 수 없습니다."
    ),
    MEMBER_REVIEW_STUDY_CANCELED(
            HttpStatus.CONFLICT,
            "MEMBER_REVIEW_STUDY_CANCELED",
            "취소된 스터디에서는 멤버를 평가할 수 없습니다."
    ),
    MEMBER_REVIEW_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "MEMBER_REVIEW_ALREADY_EXISTS",
            "해당 스터디에서 이미 이 멤버를 평가했습니다."
    ),
    MEMBER_REVIEW_SIGNAL_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "MEMBER_REVIEW_SIGNAL_REQUIRED",
            "태그를 하나 이상 선택하거나 좋아요를 남겨 주세요."
    ),
    MEMBER_REVIEW_TAG_INVALID(
            HttpStatus.BAD_REQUEST,
            "MEMBER_REVIEW_TAG_INVALID",
            "유효하지 않거나 중복된 평가 태그입니다."
    ),
    STUDY_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "STUDY_SCHEDULE_NOT_FOUND", "등록된 임장 일정을 찾을 수 없습니다."),
    STUDY_SCHEDULE_ALREADY_EXISTS(HttpStatus.CONFLICT, "STUDY_SCHEDULE_ALREADY_EXISTS", "이미 등록된 임장 일정이 있습니다."),
    STUDY_SCHEDULE_TIME_INVALID(HttpStatus.BAD_REQUEST, "STUDY_SCHEDULE_TIME_INVALID", "종료 시각은 시작 시각보다 늦어야 합니다."),
    STUDY_SCHEDULE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "STUDY_SCHEDULE_ACCESS_DENIED", "임장 일정을 조회할 권한이 없습니다."),
    STUDY_SCHEDULE_CREATE_FORBIDDEN(HttpStatus.FORBIDDEN, "STUDY_SCHEDULE_CREATE_FORBIDDEN", "임장 일정을 등록할 권한이 없습니다."),
    STUDY_SCHEDULE_CREATE_NOT_ALLOWED(HttpStatus.CONFLICT, "STUDY_SCHEDULE_CREATE_NOT_ALLOWED", "현재 상태에서는 임장 일정을 등록할 수 없습니다."),
    STUDY_SCHEDULE_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN, "STUDY_SCHEDULE_UPDATE_FORBIDDEN", "임장 일정을 수정할 권한이 없습니다."),
    STUDY_SCHEDULE_UPDATE_NOT_ALLOWED(HttpStatus.CONFLICT, "STUDY_SCHEDULE_UPDATE_NOT_ALLOWED", "현재 상태에서는 임장 일정을 수정할 수 없습니다."),
    STUDY_SCHEDULE_UPDATE_EMPTY(HttpStatus.BAD_REQUEST, "STUDY_SCHEDULE_UPDATE_EMPTY", "수정할 내용을 입력해 주세요."),
    STUDY_SCHEDULE_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "STUDY_SCHEDULE_DELETE_FORBIDDEN", "임장 일정을 삭제할 권한이 없습니다."),
    STUDY_SCHEDULE_DELETE_NOT_ALLOWED(HttpStatus.CONFLICT, "STUDY_SCHEDULE_DELETE_NOT_ALLOWED", "현재 상태에서는 임장 일정을 삭제할 수 없습니다."),

    CHAT_CONNECTION_UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "CHAT_CONNECTION_UNAUTHORIZED",
            "채팅 연결 인증에 실패했습니다."
    ),
    CHAT_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "CHAT_FORBIDDEN",
            "해당 스터디의 멤버만 채팅을 이용할 수 있습니다."
    ),
    CHAT_STUDY_COMPLETED(
            HttpStatus.FORBIDDEN,
            "CHAT_STUDY_COMPLETED",
            "완료된 스터디에는 새 메시지를 보낼 수 없습니다."
    ),
    CHAT_MESSAGE_TYPE_INVALID(
            HttpStatus.BAD_REQUEST,
            "CHAT_MESSAGE_TYPE_INVALID",
            "허용되지 않는 메시지 유형입니다."
    ),
    CHAT_IMAGE_INVALID(
            HttpStatus.BAD_REQUEST,
            "CHAT_IMAGE_INVALID",
            "이미지 메시지를 확인해 주세요."
    ),
    CHAT_CONTENT_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "CHAT_CONTENT_REQUIRED",
            "메시지 내용을 입력해 주세요."
    ),
    CHAT_MESSAGE_INVALID(
            HttpStatus.BAD_REQUEST,
            "CHAT_MESSAGE_INVALID",
            "읽음 기준 메시지를 확인해 주세요."
    ),
    CHAT_MESSAGE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CHAT_MESSAGE_NOT_FOUND",
            "해당 채팅 메시지를 찾을 수 없습니다."
    ),
    CHAT_MESSAGE_DELETE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "CHAT_MESSAGE_DELETE_FORBIDDEN",
            "본인이 보낸 메시지만 삭제할 수 있습니다."
    ),
    CHAT_MESSAGE_EDIT_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "CHAT_MESSAGE_EDIT_FORBIDDEN",
            "본인이 보낸 메시지만 수정할 수 있습니다."
    ),

    FIELD_STT_AUDIO_INVALID(
            HttpStatus.BAD_REQUEST,
            "FIELD_STT_AUDIO_INVALID",
            "STT 처리에 사용할 수 없는 음성 파일입니다."
    ),
    FIELD_STT_IDEMPOTENCY_KEY_REUSED(
            HttpStatus.BAD_REQUEST,
            "FIELD_STT_IDEMPOTENCY_KEY_REUSED",
            "동일한 요청 ID를 다른 음성 변환에 사용할 수 없습니다."
    ),
    FIELD_STT_REQUEST_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "FIELD_STT_REQUEST_FORBIDDEN",
            "해당 음성 또는 체크리스트 항목에 접근할 수 없습니다."
    ),
    FIELD_STT_STUDY_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "FIELD_STT_STUDY_MISMATCH",
            "해당 스터디의 음성 변환 작업이 아닙니다."
    ),
    FIELD_STT_STATUS_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "FIELD_STT_STATUS_FORBIDDEN",
            "본인이 요청한 음성 변환만 확인할 수 있습니다."
    ),
    FIELD_STT_RETRY_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "FIELD_STT_RETRY_FORBIDDEN",
            "본인이 요청한 음성 변환만 재처리할 수 있습니다."
    ),
    FIELD_STT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "FIELD_STT_NOT_FOUND",
            "음성 변환 작업을 찾을 수 없습니다."
    ),
    FIELD_STT_RETRY_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "FIELD_STT_RETRY_NOT_ALLOWED",
            "현재 상태에서는 음성 변환을 재처리할 수 없습니다."
    ),
    CHECKLIST_ITEM_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CHECKLIST_ITEM_NOT_FOUND",
            "체크리스트 항목을 찾을 수 없습니다."
    ),
    FIELD_PARTICIPANT_ALREADY_ENDED(
            HttpStatus.CONFLICT,
            "FIELD_PARTICIPANT_ALREADY_ENDED",
            "임장을 종료한 뒤에는 음성 기록을 추가할 수 없습니다."
    ),
    FIELD_STT_AUDIO_EXPIRED(
            HttpStatus.GONE,
            "FIELD_STT_AUDIO_EXPIRED",
            "음성 파일의 재처리 가능 기간이 만료되었습니다."
    ),
    FIELD_VISIT_ALREADY_ENDED(
            HttpStatus.CONFLICT,
            "FIELD_VISIT_ALREADY_ENDED",
            "이미 종료된 임장 세션입니다."
    ),

    // BE-014: GPS 임장 시작·상태 조회
    FIELD_VISIT_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "FIELD_VISIT_ACCESS_DENIED",
            "승인된 스터디 멤버만 임장 정보를 확인할 수 있습니다."
    ),
    FIELD_VISIT_PARTICIPANTS_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "FIELD_VISIT_PARTICIPANTS_ACCESS_DENIED",
            "승인된 스터디 멤버만 참여자 상태를 확인할 수 있습니다."
    ),
    FIELD_VISIT_START_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "FIELD_VISIT_START_FORBIDDEN",
            "승인된 스터디 멤버만 임장을 시작할 수 있습니다."
    ),
    FIELD_VISIT_START_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "FIELD_VISIT_START_NOT_AVAILABLE",
            "임장 시작 시간이 아닙니다."
    ),
    FIELD_VISIT_LOCATION_INVALID(
            HttpStatus.BAD_REQUEST,
            "FIELD_VISIT_LOCATION_INVALID",
            "현재 위치 정보를 확인해 주세요."
    ),
    FIELD_VISIT_OUT_OF_RANGE(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "FIELD_VISIT_OUT_OF_RANGE",
            "임장 지역이 아닙니다."
    ),
    FIELD_VISIT_START_IDEMPOTENCY_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "FIELD_VISIT_START_IDEMPOTENCY_MISMATCH",
            "동일한 요청 ID를 다른 임장 시작 요청에 사용할 수 없습니다."
    ),
    FIELD_VISIT_FINISH_CONFIRMATION_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "FIELD_VISIT_FINISH_CONFIRMATION_REQUIRED",
            "임장 종료 여부를 확인해 주세요."
    ),
    FIELD_VISIT_CLOSE_CONFIRMATION_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "FIELD_VISIT_CLOSE_CONFIRMATION_REQUIRED",
            "미종료 참여자 강제 종료 여부를 확인해 주세요."
    ),
    FIELD_VISIT_FINISH_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "FIELD_VISIT_FINISH_FORBIDDEN",
            "임장 세션 참여자만 종료할 수 있습니다."
    ),
    FIELD_VISIT_FINISH_CANCEL_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "FIELD_VISIT_FINISH_CANCEL_FORBIDDEN",
            "임장 세션 참여자만 개인 임장 종료를 취소할 수 있습니다."
    ),
    FIELD_VISIT_FINISH_CANCEL_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "FIELD_VISIT_FINISH_CANCEL_NOT_ALLOWED",
            "직접 종료한 임장만 다시 진행 중으로 되돌릴 수 있습니다."
    ),
    FIELD_VISIT_CLOSE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "FIELD_VISIT_CLOSE_FORBIDDEN",
            "스터디장만 전체 임장을 마감할 수 있습니다."
    ),
    FIELD_VISIT_CLOSE_VOTE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "FIELD_VISIT_CLOSE_VOTE_FORBIDDEN",
            "실제 임장을 시작한 참여자만 전체 임장 종료를 요청할 수 있습니다."
    ),
    FIELD_VISIT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "FIELD_VISIT_NOT_FOUND",
            "임장 세션을 찾을 수 없습니다."
    ),

    // AI-002: 개인 맞춤 임장 체크리스트 생성·조회
    // FIELD_PARTICIPANT_ALREADY_ENDED는 위 BE-016 정의를 그대로 재사용한다.
    // code(및 HTTP 409)는 공유하고, 체크리스트 생성 문맥 message는
    // FieldVisitAccessService.requireChecklistGenerationParticipation에서만 오버라이드한다.
    CHECKLIST_GENERATE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "CHECKLIST_GENERATE_FORBIDDEN",
            "진행 중인 임장 참여자만 체크리스트를 생성할 수 있습니다."
    ),
    CHECKLIST_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "CHECKLIST_ACCESS_DENIED",
            "승인된 스터디 멤버만 체크리스트를 확인할 수 있습니다."
    ),
    FIELD_VISIT_NOT_STARTED(
            HttpStatus.CONFLICT,
            "FIELD_VISIT_NOT_STARTED",
            "임장을 시작한 후 체크리스트를 생성할 수 있습니다."
    ),
    ROUTE_FIELD_VISIT_NOT_STARTED(
            HttpStatus.NOT_FOUND,
            "FIELD_VISIT_NOT_STARTED",
            "임장 세션을 시작한 후 추천 경로를 생성할 수 있습니다."
    ),
    ROUTE_GENERATE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "ROUTE_GENERATE_FORBIDDEN",
            "진행 중인 임장 참여자만 추천 경로를 생성할 수 있습니다."
    ),
    ROUTE_CHECKLIST_REQUIRED(
            HttpStatus.CONFLICT,
            "ROUTE_CHECKLIST_REQUIRED",
            "추천 경로를 만들려면 생성된 체크리스트가 필요합니다."
    ),
    ROUTE_NOT_APPLICABLE(
            HttpStatus.CONFLICT,
            "ROUTE_NOT_APPLICABLE",
            "추천 경로를 만들 수 있는 주변 시설이 충분하지 않습니다."
    ),
    ROUTE_POI_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "ROUTE_POI_UNAVAILABLE",
            "주변 시설 정보를 일시적으로 불러올 수 없습니다."
    ),
    ROUTE_WALKING_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "ROUTE_WALKING_UNAVAILABLE",
            "보행 경로 정보를 일시적으로 불러올 수 없습니다."
    ),

    // BE-015: 체크리스트 완료·현장 기록 CRUD
    CHECKLIST_COMPLETION_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "CHECKLIST_COMPLETION_FORBIDDEN",
            "진행 중인 임장 참여자만 체크리스트 완료 상태를 변경할 수 있습니다."
    ),
    CHECKLIST_ITEM_DUPLICATED(
            HttpStatus.BAD_REQUEST,
            "CHECKLIST_ITEM_DUPLICATED",
            "동일한 체크리스트 항목이 요청에 중복되어 있습니다."
    ),
    CHECKLIST_ITEM_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "CHECKLIST_ITEM_ACCESS_DENIED",
            "본인의 체크리스트 항목만 변경할 수 있습니다."
    ),
    CHECKLIST_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CHECKLIST_NOT_FOUND",
            "체크리스트를 찾을 수 없습니다."
    ),
    FIELD_VISIT_REPORT_LOCKED(
            HttpStatus.CONFLICT,
            "FIELD_VISIT_REPORT_LOCKED",
            "리포트 생성이 시작된 뒤에는 체크리스트와 현장 기록을 변경할 수 없습니다."
    ),
    FIELD_RECORD_CREATE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "FIELD_RECORD_CREATE_FORBIDDEN",
            "진행 중인 임장 참여자만 현장 기록을 저장할 수 있습니다."
    ),
    FIELD_RECORD_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "FIELD_RECORD_ACCESS_DENIED",
            "승인된 스터디 멤버만 현장 기록을 확인할 수 있습니다."
    ),
    FIELD_RECORD_PAYLOAD_INVALID(
            HttpStatus.BAD_REQUEST,
            "FIELD_RECORD_PAYLOAD_INVALID",
            "현장 기록 요청 값이 올바르지 않습니다."
    ),
    FIELD_RECORD_SOURCE_TYPE_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST,
            "FIELD_RECORD_SOURCE_TYPE_NOT_ALLOWED",
            "이 API에서는 텍스트와 사진 기록만 저장할 수 있습니다."
    ),
    FIELD_RECORD_SOURCE_TYPE_INVALID(
            HttpStatus.BAD_REQUEST,
            "FIELD_RECORD_SOURCE_TYPE_INVALID",
            "지원하지 않는 현장 기록 유형입니다."
    ),
    FIELD_RECORD_IDEMPOTENCY_KEY_REUSED(
            HttpStatus.BAD_REQUEST,
            "FIELD_RECORD_IDEMPOTENCY_KEY_REUSED",
            "동일한 요청 ID를 다른 현장 기록에 사용할 수 없습니다."
    ),
    FIELD_RECORD_PHOTO_INVALID(
            HttpStatus.BAD_REQUEST,
            "FIELD_RECORD_PHOTO_INVALID",
            "현장 사진으로 사용할 수 없는 파일입니다."
    ),
    FIELD_RECORD_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "FIELD_RECORD_NOT_FOUND",
            "현장 기록을 찾을 수 없습니다."
    ),
    FIELD_RECORD_UPDATE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "FIELD_RECORD_UPDATE_FORBIDDEN",
            "본인이 작성한 현장 기록만 수정할 수 있습니다."
    ),
    FIELD_RECORD_UPDATE_EMPTY(
            HttpStatus.BAD_REQUEST,
            "FIELD_RECORD_UPDATE_EMPTY",
            "수정할 필드를 한 개 이상 전달해야 합니다."
    ),
    FIELD_RECORD_UPDATE_FIELD_INVALID(
            HttpStatus.BAD_REQUEST,
            "FIELD_RECORD_UPDATE_FIELD_INVALID",
            "해당 기록 유형에서 수정할 수 없는 필드입니다."
    ),
    FIELD_RECORD_DELETE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "FIELD_RECORD_DELETE_FORBIDDEN",
            "본인이 작성한 현장 기록만 삭제할 수 있습니다."
    ),
    FIELD_RECORD_STT_PROCESSING(
            HttpStatus.CONFLICT,
            "FIELD_RECORD_STT_PROCESSING",
            "음성 변환이 진행 중인 기록은 수정하거나 삭제할 수 없습니다."
    ),
    FIELD_RECORD_STT_UPDATE_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "FIELD_RECORD_STT_UPDATE_NOT_ALLOWED",
            "변환에 실패한 음성 기록은 수정할 수 없습니다. 재처리는 STT 재시도 API를 사용하세요."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
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
