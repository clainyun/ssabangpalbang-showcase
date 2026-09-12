package com.ssafy.ssabangpalbang.community.dto.response;

import java.util.List;

public record PostListResponse(
        String boardType,
        String sort,
        String keyword,
        List<PostListItemResponse> content,
        CursorPageInfoResponse pageInfo
) {
}
