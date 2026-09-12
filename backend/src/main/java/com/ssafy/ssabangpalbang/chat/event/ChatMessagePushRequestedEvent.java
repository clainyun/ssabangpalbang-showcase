package com.ssafy.ssabangpalbang.chat.event;

public record ChatMessagePushRequestedEvent(
        Long messageId,
        Long studyId,
        Long senderId,
        String senderNickname,
        String messageType,
        String content
) {
}
