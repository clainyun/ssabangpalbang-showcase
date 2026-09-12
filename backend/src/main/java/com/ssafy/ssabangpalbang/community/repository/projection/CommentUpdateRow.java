package com.ssafy.ssabangpalbang.community.repository.projection;

import java.time.Instant;

public record CommentUpdateRow(
        Long commentId,
        Long postId,
        Long authorId,
        String content,
        Instant createdAt,
        Instant updatedAt
) {
}
