package com.ssafy.ssabangpalbang.community.repository.projection;

public record CommentPostContextRow(
        Long postId,
        Long authorId
) {
}
