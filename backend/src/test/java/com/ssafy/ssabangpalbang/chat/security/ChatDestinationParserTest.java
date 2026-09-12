package com.ssafy.ssabangpalbang.chat.security;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatDestinationParserTest {

    @Test
    void subscribe_path_extracts_studyId() {
        Long studyId = ChatDestinationParser.parseChatSubscribeStudyId(
                "/sub/studies/42/chat"
        );

        assertThat(studyId).isEqualTo(42L);
    }

    @Test
    void send_path_extracts_studyId() {
        Long studyId = ChatDestinationParser.parseChatSendStudyId(
                "/pub/studies/42/chat/messages"
        );

        assertThat(studyId).isEqualTo(42L);
    }

    @Test
    void subscribe_wrong_format_throws_chat_forbidden() {
        assertThatThrownBy(() ->
                ChatDestinationParser.parseChatSubscribeStudyId(
                        "/pub/studies/42/chat/messages"
                ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_FORBIDDEN));
    }

    @Test
    void send_non_numeric_studyId_throws_chat_forbidden() {
        assertThatThrownBy(() ->
                ChatDestinationParser.parseChatSendStudyId(
                        "/pub/studies/abc/chat/messages"
                ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_FORBIDDEN));
    }

    @Test
    void null_destination_throws_chat_forbidden() {
        assertThatThrownBy(() ->
                ChatDestinationParser.parseChatSubscribeStudyId(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_FORBIDDEN));
    }
}
