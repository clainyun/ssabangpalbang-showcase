package com.ssafy.ssabangpalbang.chat.dto;

import java.time.OffsetDateTime;

/**
 * 채팅 메시지 삭제(DELETE /api/v1/studies/{studyId}/chat/messages/{messageId}) 응답이다.
 * 이미 삭제된 메시지를 다시 삭제해도 최초 삭제 시각을 그대로 돌려준다(멱등).
 */
public record ChatMessageDeleteResponse(
        Long studyId,
        Long messageId,
        OffsetDateTime deletedAt
) {
}
