package com.ssafy.ssabangpalbang.chat.dto;

import com.ssafy.ssabangpalbang.chat.repository.ChatMessageHistoryRow;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 채팅 이력 조회(GET /api/v1/studies/{studyId}/chat/messages) 응답의 메시지 한 건이다.
 * SYSTEM 메시지는 sender가 null이다.
 *
 * <p>삭제된 메시지는 {@code deleted=true}이고 content·image를 내려보내지 않는다.
 * sender는 유지한다 — 클라이언트가 "누구의 자리에 톰스톤을 그릴지"(좌/우 정렬)를
 * 판단하는 데 필요하다.</p>
 */
public record ChatMessageItemResponse(
        Long messageId,
        String messageType,
        String content,
        ChatImageResponse image,
        ChatSenderResponse sender,
        OffsetDateTime createdAt,
        boolean deleted,
        /** 마지막 수정 시각. 수정된 적 없으면 null이며, 있으면 "수정됨" 표시에 쓴다. */
        OffsetDateTime editedAt
) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static ChatMessageItemResponse from(ChatMessageHistoryRow row, String imageUrl) {
        boolean isImage = "IMAGE".equals(row.getMessageType());
        boolean isSystem = "SYSTEM".equals(row.getMessageType());
        boolean deleted = row.getDeletedAt() != null;

        ChatImageResponse image = isImage && !deleted
                ? new ChatImageResponse(row.getImageFileId(), imageUrl, "COMPLETED")
                : null;
        ChatSenderResponse sender = isSystem || row.getSenderId() == null
                ? null
                : new ChatSenderResponse(
                        row.getSenderId(),
                        row.getSenderNickname(),
                        row.getSenderSelectedCharacterId()
                );

        return new ChatMessageItemResponse(
                row.getMessageId(),
                row.getMessageType(),
                deleted ? null : row.getContent(),
                image,
                sender,
                OffsetDateTime.ofInstant(row.getCreatedAt(), SEOUL),
                deleted,
                row.getEditedAt() == null || deleted
                        ? null
                        : OffsetDateTime.ofInstant(row.getEditedAt(), SEOUL)
        );
    }
}
