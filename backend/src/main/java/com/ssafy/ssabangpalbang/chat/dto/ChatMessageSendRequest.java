package com.ssafy.ssabangpalbang.chat.dto;

/**
 * STOMP SEND(/pub/studies/{studyId}/chat/messages) 요청 본문이다.
 *
 * <p>TEXT는 content를 사용하고 imageFileId는 null이다.
 * IMAGE는 imageFileId를 사용하고 content는 null이다.
 * clientMessageId는 재전송 시 중복 저장을 막기 위한 멱등 키다.</p>
 */
public record ChatMessageSendRequest(
        String messageType,
        String content,
        Long imageFileId,
        String clientMessageId
) {
}
