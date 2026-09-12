package com.ssafy.ssabangpalbang.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 채팅 메시지 수정(PATCH /api/v1/studies/{studyId}/chat/messages/{messageId}) 요청이다.
 * TEXT 메시지만 수정할 수 있다. 길이 상한은 전송(SEND) 경로와 동일하게 두지 않는다
 * (chat_message.content TEXT 컬럼, SEND도 공백만 금지).
 */
public record ChatMessageEditRequest(
        @NotBlank(message = "메시지 내용을 입력해 주세요.")
        String content
) {
}
