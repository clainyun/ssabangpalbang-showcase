package com.ssafy.ssabangpalbang.report.dto.response;

import com.ssafy.ssabangpalbang.report.repository.MemberReportRow;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record MemberReportResponse(
        Long reportId,
        String title,
        String summary,
        List<String> analysisTags,
        ApartmentSummary apartment,
        StudySummary study,
        String status,
        boolean favoritedByMe,
        boolean canViewEvidence,
        OffsetDateTime completedAt
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static MemberReportResponse from(
            MemberReportRow row,
            String title,
            String summary,
            List<String> analysisTags
    ) {
        return new MemberReportResponse(
                row.getReportId(),
                title,
                summary,
                analysisTags,
                new ApartmentSummary(
                        row.getApartmentId(),
                        row.getApartmentName()
                ),
                new StudySummary(
                        row.getStudyId(),
                        row.getStudyTitle(),
                        toOffsetDateTime(row.getVisitedAt()),
                        row.getParticipantCount()
                ),
                row.getStatus(),
                Boolean.TRUE.equals(row.getFavoritedByMe()),
                Boolean.TRUE.equals(row.getCanViewEvidence()),
                toOffsetDateTime(row.getCompletedAt())
        );
    }

    private static OffsetDateTime toOffsetDateTime(
            java.time.Instant instant
    ) {
        return instant == null
                ? null
                : instant.atZone(SEOUL).toOffsetDateTime();
    }

    public record ApartmentSummary(
            Long apartmentId,
            String name
    ) {
    }

    public record StudySummary(
            Long studyId,
            String title,
            OffsetDateTime visitedAt,
            long participantCount
    ) {
    }
}
