package com.ssafy.ssabangpalbang.chatbot.dto.response;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotConversation;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessage;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessageStatus;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record ChatbotMessageListResponse(
        Long conversationId,
        ApartmentBody apartment,
        List<MessageBody> content,
        Long nextCursor,
        boolean hasNext,
        OffsetDateTime lastMessageAt,
        boolean hasResponseInProgress
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static ChatbotMessageListResponse from(
            ChatbotConversation conversation,
            Apartment apartment,
            List<MessageBody> content,
            Long nextCursor,
            boolean hasNext,
            boolean hasResponseInProgress
    ) {
        return new ChatbotMessageListResponse(
                conversation.getId(),
                new ApartmentBody(apartment.getId(), apartment.getName()),
                List.copyOf(content),
                nextCursor,
                hasNext,
                toSeoul(conversation.getLastMessageAt()),
                hasResponseInProgress
        );
    }

    public static MessageBody messageFrom(
            ChatbotMessage message,
            List<SourceBody> sources
    ) {
        return new MessageBody(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getStatus().name(),
                message.getBasisType() == null
                        ? "NONE"
                        : message.getBasisType(),
                message.getBasisLabel(),
                List.copyOf(sources),
                message.getFailReason(),
                message.getStatus() == ChatbotMessageStatus.FAILED,
                toSeoul(message.getCreatedAt()),
                toSeoul(message.getCompletedAt())
        );
    }

    private static OffsetDateTime toSeoul(Instant instant) {
        return instant == null
                ? null
                : instant.atZone(SEOUL).toOffsetDateTime();
    }

    public record ApartmentBody(Long apartmentId, String name) {
    }

    public record MessageBody(
            Long messageId,
            String role,
            String content,
            String status,
            String basisType,
            String basisLabel,
            List<SourceBody> sources,
            String failReason,
            boolean retryable,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt
    ) {
    }

    public record SourceBody(
            String sourceType,
            Long sourceId,
            Long reportId,
            String title,
            String sectionLabel,
            String url
    ) {
    }
}
