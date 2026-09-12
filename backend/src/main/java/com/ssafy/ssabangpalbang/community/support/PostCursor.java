package com.ssafy.ssabangpalbang.community.support;

import com.ssafy.ssabangpalbang.community.dto.request.PostSort;

import java.math.BigDecimal;
import java.time.Instant;

public record PostCursor(
        int version,
        PostSort sort,
        BigDecimal hotScore,
        Instant createdAt,
        long postId,
        String filterHash
) {

    public static final int CURRENT_VERSION = 1;

    public static PostCursor latest(
            Instant createdAt,
            long postId,
            String filterHash
    ) {
        return new PostCursor(
                CURRENT_VERSION,
                PostSort.LATEST,
                null,
                createdAt,
                postId,
                filterHash
        );
    }

    public static PostCursor hot(
            BigDecimal hotScore,
            Instant createdAt,
            long postId,
            String filterHash
    ) {
        return new PostCursor(
                CURRENT_VERSION,
                PostSort.HOT,
                hotScore,
                createdAt,
                postId,
                filterHash
        );
    }
}
