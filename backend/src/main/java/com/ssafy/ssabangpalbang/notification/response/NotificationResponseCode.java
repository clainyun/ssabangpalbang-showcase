package com.ssafy.ssabangpalbang.notification.response;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum NotificationResponseCode implements ResponseCode {

    NOTIFICATION_LIST_SUCCESS(
            "NOTIFICATION_LIST_SUCCESS",
            "알림 목록 조회에 성공했습니다."
    ),
    NOTIFICATION_READ_SUCCESS(
            "NOTIFICATION_READ_SUCCESS",
            "알림을 읽음 처리했습니다."
    ),
    NOTIFICATION_ALREADY_READ(
            "NOTIFICATION_ALREADY_READ",
            "이미 읽은 알림입니다."
    ),
    NOTIFICATION_READ_ALL_SUCCESS(
            "NOTIFICATION_READ_ALL_SUCCESS",
            "모든 알림을 읽음 처리했습니다."
    ),
    NOTIFICATION_ALREADY_ALL_READ(
            "NOTIFICATION_ALREADY_ALL_READ",
            "이미 모든 알림을 확인했습니다."
    );

    private final String code;
    private final String message;

    NotificationResponseCode(
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
