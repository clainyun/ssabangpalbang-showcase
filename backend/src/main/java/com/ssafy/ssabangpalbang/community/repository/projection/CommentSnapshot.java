package com.ssafy.ssabangpalbang.community.repository.projection;

import java.time.Instant;

public record CommentSnapshot(
        Long commentId,
        Long postId,
        Long authorId,
        Instant deletedAt
) {
}
