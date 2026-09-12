package com.ssafy.ssabangpalbang.chat.dto;

import com.ssafy.ssabangpalbang.chat.domain.ChatMessage;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * /sub/studies/{studyId}/chat으로 실시간 발행하는 메시지 Payload다.
 * AFTER_COMMIT 이후에만 발행하므로, 이 레코드는 DB 커밋이 끝난 뒤 이미
 * 완성된 값으로 채워져 있어야 한다(Listener에서 추가 조회를 하지 않는다).
 *
 * <p>삭제·수정 이벤트도 같은 토픽으로 발행한다. {@code deleted=true}이면 클라이언트는
 * 같은 messageId의 기존 말풍선을 톰스톤("삭제된 메시지")으로 교체하고,
 * {@code editedAt}이 있으면 본문을 바꿔 그리고 "수정됨"을 표시한다.
 * 삭제 페이로드의 content·image는 내려보내지 않고 sender는 정렬 판단을 위해 유지한다.</p>
 */
public record ChatMessageBroadcastResponse(
        Long messageId,
        Long studyId,
        String messageType,
        String content,
        ChatImageResponse image,
        ChatSenderResponse sender,
        OffsetDateTime createdAt,
        boolean deleted,
        OffsetDateTime editedAt
) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static ChatMessageBroadcastResponse from(
            ChatMessage message,
            ChatSenderResponse sender,
            ChatImageResponse image
    ) {
        return new ChatMessageBroadcastResponse(
                message.getId(),
                message.getStudyId(),
                message.getMessageType().name(),
                message.getContent(),
                image,
                sender,
                OffsetDateTime.ofInstant(message.getCreatedAt(), SEOUL),
                false,
                toSeoul(message.getEditedAt())
        );
    }

    /** 삭제 브로드캐스트. 원문(content·image)은 싣지 않는다. */
    public static ChatMessageBroadcastResponse deleted(
            ChatMessage message,
            ChatSenderResponse sender
    ) {
        return new ChatMessageBroadcastResponse(
                message.getId(),
                message.getStudyId(),
                message.getMessageType().name(),
                null,
                null,
                sender,
                OffsetDateTime.ofInstant(message.getCreatedAt(), SEOUL),
                true,
                null
        );
    }

    /** 수정 브로드캐스트. 바뀐 본문과 수정 시각을 싣는다. */
    public static ChatMessageBroadcastResponse edited(
            ChatMessage message,
            ChatSenderResponse sender
    ) {
        return new ChatMessageBroadcastResponse(
                message.getId(),
                message.getStudyId(),
                message.getMessageType().name(),
                message.getContent(),
                null,
                sender,
                OffsetDateTime.ofInstant(message.getCreatedAt(), SEOUL),
                false,
                toSeoul(message.getEditedAt())
        );
    }

    private static OffsetDateTime toSeoul(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, SEOUL);
    }
}
