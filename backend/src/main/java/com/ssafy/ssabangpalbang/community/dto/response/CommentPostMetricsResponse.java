package com.ssafy.ssabangpalbang.community.dto.response;

import java.math.BigDecimal;

public record CommentPostMetricsResponse(
        long commentCount,
        long likeCount,
        long viewCount,
        boolean isHot,
        BigDecimal hotScore
) {
}
