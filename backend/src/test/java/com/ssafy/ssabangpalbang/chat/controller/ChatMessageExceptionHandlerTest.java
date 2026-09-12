package com.ssafy.ssabangpalbang.chat.controller;

import com.ssafy.ssabangpalbang.chat.dto.ChatErrorPayload;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageExceptionHandlerTest {

    private final ChatMessageExceptionHandler handler = new ChatMessageExceptionHandler();

    @Test
    void BusinessException은_errorCode와_message를_그대로_담아_반환한다() {
        ChatErrorPayload payload = handler.handleBusinessException(
                new BusinessException(ErrorCode.CHAT_CONTENT_REQUIRED)
        );

        assertThat(payload).isEqualTo(new ChatErrorPayload(
                ErrorCode.CHAT_CONTENT_REQUIRED.getCode(),
                ErrorCode.CHAT_CONTENT_REQUIRED.getMessage()
        ));
    }

    @Test
    void 예상하지_못한_예외는_INTERNAL_SERVER_ERROR로_변환한다() {
        ChatErrorPayload payload = handler.handleUnexpected(new IllegalStateException("boom"));

        assertThat(payload).isEqualTo(new ChatErrorPayload(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage()
        ));
    }
}
