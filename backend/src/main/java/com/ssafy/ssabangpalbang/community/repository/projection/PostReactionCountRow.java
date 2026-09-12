package com.ssafy.ssabangpalbang.community.repository.projection;

public record PostReactionCountRow(
        Long postId,
        long likeCount,
        long commentCount,
        boolean likedByMe
) {
}
