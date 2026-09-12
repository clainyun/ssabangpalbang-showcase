package com.ssafy.ssabangpalbang.report.dto.response;

import com.ssafy.ssabangpalbang.report.domain.ReportFavorite;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record ReportFavoriteResponse(
        Long reportId,
        boolean favoritedByMe,
        Long favoriteCount,
        OffsetDateTime favoritedAt
) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static ReportFavoriteResponse from(
            ReportFavorite favorite,
            long favoriteCount
    ) {
        return new ReportFavoriteResponse(
                favorite.getReportId(),
                true,
                favoriteCount,
                OffsetDateTime.ofInstant(favorite.getCreatedAt(), SEOUL)
        );
    }
}
