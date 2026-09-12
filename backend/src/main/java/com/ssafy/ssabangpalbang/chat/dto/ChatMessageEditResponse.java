package com.ssafy.ssabangpalbang.chat.dto;

import java.time.OffsetDateTime;

/**
 * 채팅 메시지 수정(PATCH /api/v1/studies/{studyId}/chat/messages/{messageId}) 응답이다.
 */
public record ChatMessageEditResponse(
        Long studyId,
        Long messageId,
        String content,
        OffsetDateTime editedAt
) {
}
