package com.ssafy.ssabangpalbang.member.dto.response;

import java.util.List;

public record MemberFollowingListResponse(
        List<MemberFollowingResponse> content,
        long totalCount,
        Long nextCursor,
        boolean hasNext
) {
}
