package com.ssafy.ssabangpalbang.community.dto.response;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

public record PostDeleteResponse(
        Long postId,
        OffsetDateTime deletedAt
) {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static PostDeleteResponse from(
            Long postId,
            Instant deletedAt
    ) {
        return new PostDeleteResponse(
                Objects.requireNonNull(postId),
                Objects.requireNonNull(deletedAt)
                        .atZone(SEOUL_ZONE_ID)
                        .toOffsetDateTime()
        );
    }
}
