package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.report.repository.PublicProfileReportRow;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record PublicProfileReportResponse(
        Long reportId,
        String title,
        String summary,
        List<String> analysisTags,
        ApartmentSummary apartment,
        StudySummary study,
        boolean favoritedByMe,
        OffsetDateTime completedAt
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static PublicProfileReportResponse from(
            PublicProfileReportRow row,
            String title,
            String summary,
            List<String> analysisTags
    ) {
        return new PublicProfileReportResponse(
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
                        row.getParticipantCount()
                ),
                Boolean.TRUE.equals(row.getFavoritedByMe()),
                row.getCompletedAt() == null
                        ? null
                        : row.getCompletedAt()
                                .atZone(SEOUL)
                                .toOffsetDateTime()
        );
    }

    public record ApartmentSummary(
            Long apartmentId,
            String name
    ) {
    }

    public record StudySummary(
            Long studyId,
            String title,
            long participantCount
    ) {
    }
}
