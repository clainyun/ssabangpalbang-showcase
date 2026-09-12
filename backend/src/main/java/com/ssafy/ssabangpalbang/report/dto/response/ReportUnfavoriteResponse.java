package com.ssafy.ssabangpalbang.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record ReportUnfavoriteResponse(
        @Schema(example = "48")
        Long reportId,
        @Schema(example = "false")
        boolean favoritedByMe,
        @Schema(example = "17")
        Long favoriteCount,
        @Schema(example = "2026-07-25T15:45:00+09:00")
        OffsetDateTime unfavoritedAt
) {

    public static ReportUnfavoriteResponse of(
            Long reportId,
            long favoriteCount,
            OffsetDateTime unfavoritedAt
    ) {
        return new ReportUnfavoriteResponse(
                reportId,
                false,
                favoriteCount,
                unfavoritedAt
        );
    }
}
