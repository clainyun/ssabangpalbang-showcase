package com.ssafy.ssabangpalbang.community.dto.response;

public record CursorPageInfoResponse(
        int size,
        String nextCursor,
        boolean hasNext
) {
}
