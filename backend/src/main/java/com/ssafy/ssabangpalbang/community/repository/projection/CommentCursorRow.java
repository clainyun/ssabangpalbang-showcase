package com.ssafy.ssabangpalbang.community.repository.projection;

import java.time.Instant;

public record CommentCursorRow(
        Long commentId,
        Instant createdAt
) {
}
