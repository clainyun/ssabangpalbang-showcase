package com.ssafy.ssabangpalbang.community.dto.response;

import java.time.OffsetDateTime;

public record CommentResponse(
        Long commentId,
        Long postId,
        String content,
        CommentAuthorResponse author,
        boolean isMine,
        boolean isPostAuthor,
        boolean canEdit,
        boolean canDelete,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
