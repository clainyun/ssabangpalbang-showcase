package com.ssafy.ssabangpalbang.community.dto.response;

import java.util.List;

public record CommentListResponse(
        Long postId,
        List<CommentListItemResponse> content,
        long totalCount,
        Long nextCursor,
        boolean hasNext
) {
}
