package com.ssafy.ssabangpalbang.review.support;

import java.time.Instant;

public record MemberReviewCursor(
        int version,
        long revieweeId,
        Instant createdAt,
        long reviewId
) {

    public static final int CURRENT_VERSION = 1;

    public static MemberReviewCursor of(
            long revieweeId,
            Instant createdAt,
            long reviewId
    ) {
        return new MemberReviewCursor(
                CURRENT_VERSION,
                revieweeId,
                createdAt,
                reviewId
        );
    }
}
