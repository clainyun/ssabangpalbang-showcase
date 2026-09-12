package com.ssafy.ssabangpalbang.chatbot.dto;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum ChatbotResponseCode implements ResponseCode {

    CHATBOT_CONVERSATION_CREATE_SUCCESS(
            "CHATBOT_CONVERSATION_CREATE_SUCCESS",
            "챗봇 대화가 시작되었습니다."
    ),
    CHATBOT_MESSAGE_LIST_SUCCESS(
            "CHATBOT_MESSAGE_LIST_SUCCESS",
            "챗봇 대화 이력 조회에 성공했습니다."
    ),
    CHATBOT_MESSAGE_ACCEPTED(
            "CHATBOT_MESSAGE_ACCEPTED",
            "질문이 전송되었습니다."
    );

    private final String code;
    private final String message;

    ChatbotResponseCode(String code, String message) {
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
