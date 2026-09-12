package com.ssafy.ssabangpalbang.community.dto.response;

public record CommentCreateResponse(
        CommentResponse comment,
        CommentPostMetricsResponse postMetrics
) {
}
