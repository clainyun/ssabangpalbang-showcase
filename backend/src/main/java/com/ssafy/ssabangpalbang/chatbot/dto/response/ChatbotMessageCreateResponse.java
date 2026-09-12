package com.ssafy.ssabangpalbang.chatbot.dto.response;

import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessage;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 202 Accepted 본문이다. 필드 이름은 {@code docs/API.md} 「아파트 챗봇 질문 전송」
 * 절을 그대로 따른다.
 */
public record ChatbotMessageCreateResponse(
        Long conversationId,
        BasisPolicyBody basisPolicy,
        MessageBody userMessage,
        MessageBody assistantMessage,
        PollingBody polling
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 정본 예시의 권장 폴링 간격이다. */
    public static final int RECOMMENDED_INTERVAL_MS = 2500;

    public static ChatbotMessageCreateResponse of(
            Long conversationId,
            Long apartmentId,
            BasisPolicyBody basisPolicy,
            ChatbotMessage userMessage,
            ChatbotMessage assistantMessage
    ) {
        return new ChatbotMessageCreateResponse(
                conversationId,
                basisPolicy,
                messageFrom(userMessage, "NONE", null),
                // placeholder는 아직 근거가 없다. 사전 안내 값을 그대로 비춰 준다.
                messageFrom(
                        assistantMessage,
                        basisPolicy.basisType(),
                        basisPolicy.basisLabel()
                ),
                new PollingBody(
                        messageHistoryApi(apartmentId, conversationId),
                        RECOMMENDED_INTERVAL_MS
                )
        );
    }

    private static MessageBody messageFrom(
            ChatbotMessage message,
            String basisType,
            String basisLabel
    ) {
        return new MessageBody(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getStatus().name(),
                basisType,
                basisLabel,
                List.of(),
                message.getFailReason(),
                toSeoul(message.getCreatedAt()),
                toSeoul(message.getCompletedAt())
        );
    }

    private static String messageHistoryApi(
            Long apartmentId,
            Long conversationId
    ) {
        return "/api/v1/apartments/" + apartmentId
                + "/chatbot/conversations/" + conversationId + "/messages";
    }

    private static OffsetDateTime toSeoul(Instant instant) {
        return instant == null
                ? null
                : instant.atZone(SEOUL).toOffsetDateTime();
    }

    public record BasisPolicyBody(
            String basisType,
            String basisLabel,
            Long reportId
    ) {
    }

    /**
     * 이력 조회의 {@code MessageBody}와 달리 {@code retryable}이 없다.
     * 정본 「메시지 공통 필드」 표에 그 필드가 없기 때문이다.
     */
    public record MessageBody(
            Long messageId,
            String role,
            String content,
            String status,
            String basisType,
            String basisLabel,
            List<Object> sources,
            String failReason,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt
    ) {
    }

    public record PollingBody(
            String messageHistoryApi,
            int recommendedIntervalMs
    ) {
    }
}
