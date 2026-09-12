package com.ssafy.ssabangpalbang.community.repository.projection;

import java.math.BigDecimal;

public record CommentPostMetricsRow(
        boolean postAvailable,
        long commentCount,
        long likeCount,
        long viewCount,
        Boolean hot,
        BigDecimal hotScore
) {
}
