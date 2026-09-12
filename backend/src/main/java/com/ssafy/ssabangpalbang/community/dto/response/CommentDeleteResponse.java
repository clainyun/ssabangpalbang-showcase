package com.ssafy.ssabangpalbang.community.dto.response;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

public record CommentDeleteResponse(
        Long commentId,
        Long postId,
        OffsetDateTime deletedAt,
        boolean postAvailable,
        CommentPostMetricsResponse postMetrics
) {

    private static final ZoneId SEOUL_ZONE_ID =
            ZoneId.of("Asia/Seoul");

    public static CommentDeleteResponse from(
            Long commentId,
            Long postId,
            Instant deletedAt,
            boolean postAvailable,
            CommentPostMetricsResponse postMetrics
    ) {
        return new CommentDeleteResponse(
                Objects.requireNonNull(commentId),
                Objects.requireNonNull(postId),
                Objects.requireNonNull(deletedAt)
                        .atZone(SEOUL_ZONE_ID)
                        .toOffsetDateTime(),
                postAvailable,
                Objects.requireNonNull(postMetrics)
        );
    }
}
