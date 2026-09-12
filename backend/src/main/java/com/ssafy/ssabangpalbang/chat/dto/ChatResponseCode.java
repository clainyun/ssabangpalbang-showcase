package com.ssafy.ssabangpalbang.chat.dto;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum ChatResponseCode implements ResponseCode {

    CHAT_MESSAGE_LIST_SUCCESS(
            "CHAT_MESSAGE_LIST_SUCCESS",
            "채팅 이력 조회에 성공했습니다."
    ),
    CHAT_READ_SUCCESS(
            "CHAT_READ_SUCCESS",
            "채팅을 읽음 처리했습니다."
    ),
    CHAT_UNREAD_COUNT_SUCCESS(
            "CHAT_UNREAD_COUNT_SUCCESS",
            "안 읽은 채팅 수 조회에 성공했습니다."
    ),
    CHAT_NOTIFICATION_SETTING_SUCCESS(
            "CHAT_NOTIFICATION_SETTING_SUCCESS",
            "채팅 푸시 알림 설정을 조회했습니다."
    ),
    CHAT_NOTIFICATION_SETTING_UPDATED(
            "CHAT_NOTIFICATION_SETTING_UPDATED",
            "채팅 푸시 알림 설정을 변경했습니다."
    ),
    CHAT_MESSAGE_DELETE_SUCCESS(
            "CHAT_MESSAGE_DELETE_SUCCESS",
            "채팅 메시지를 삭제했습니다."
    ),
    CHAT_MESSAGE_EDIT_SUCCESS(
            "CHAT_MESSAGE_EDIT_SUCCESS",
            "채팅 메시지를 수정했습니다."
    );

    private final String code;
    private final String message;

    ChatResponseCode(String code, String message) {
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
