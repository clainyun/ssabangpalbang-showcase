package com.ssafy.ssabangpalbang.review.dto.response;

import java.util.List;

public record MemberReviewListResponse(
        MemberReviewSummaryResponse summary,
        List<MemberReviewItemResponse> content,
        String nextCursor,
        boolean hasNext
) {
}
