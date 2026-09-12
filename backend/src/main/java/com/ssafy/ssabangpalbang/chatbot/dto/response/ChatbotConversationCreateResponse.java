package com.ssafy.ssabangpalbang.chatbot.dto.response;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotConversation;
import com.ssafy.ssabangpalbang.report.domain.Report;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record ChatbotConversationCreateResponse(
        Long conversationId,
        ApartmentBody apartment,
        String preferredBasisType,
        String preferredBasisLabel,
        AvailableReportBody availableReport,
        List<String> recommendedQuestions,
        OffsetDateTime lastMessageAt,
        OffsetDateTime createdAt
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static ChatbotConversationCreateResponse from(
            ChatbotConversation conversation,
            Apartment apartment,
            Report report,
            List<String> recommendedQuestions
    ) {
        boolean reportAvailable = report != null;
        return new ChatbotConversationCreateResponse(
                conversation.getId(),
                new ApartmentBody(
                        apartment.getId(),
                        apartment.getName(),
                        apartment.getAddress()
                ),
                reportAvailable ? "REPORT" : "WEB",
                reportAvailable ? "리포트 기반" : "웹 기반",
                reportAvailable
                        ? new AvailableReportBody(
                                report.getId(),
                                apartment.getName() + " 임장 리포트"
                        )
                        : null,
                List.copyOf(recommendedQuestions),
                toSeoul(conversation.getLastMessageAt()),
                toSeoul(conversation.getCreatedAt())
        );
    }

    private static OffsetDateTime toSeoul(Instant instant) {
        return instant == null
                ? null
                : instant.atZone(SEOUL).toOffsetDateTime();
    }

    public record ApartmentBody(
            Long apartmentId,
            String name,
            String address
    ) {
    }

    public record AvailableReportBody(
            Long reportId,
            String title
    ) {
    }
}
