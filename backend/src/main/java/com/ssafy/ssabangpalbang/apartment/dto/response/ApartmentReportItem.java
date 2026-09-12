package com.ssafy.ssabangpalbang.apartment.dto.response;

import com.ssafy.ssabangpalbang.apartment.repository.ApartmentReportRow;
import com.ssafy.ssabangpalbang.apartment.support.ReportResultJsonParser.ParsedReport;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record ApartmentReportItem(
        Long reportId,
        String title,
        List<String> analysisTags,
        String summary,
        OffsetDateTime completedAt,
        boolean isAiGenerated,
        boolean favoritedByMe
) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static ApartmentReportItem from(
            ApartmentReportRow row,
            ParsedReport parsed
    ) {
        return new ApartmentReportItem(
                row.reportId(),
                parsed.title(),
                parsed.analysisTags(),
                parsed.summary(),
                row.completedAt() == null
                        ? null
                        : row.completedAt().atZone(SEOUL).toOffsetDateTime(),
                parsed.aiGenerated(),
                row.favoritedByMe()
        );
    }
}
