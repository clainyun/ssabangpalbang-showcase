package com.ssafy.ssabangpalbang.review.dto.response;

import com.ssafy.ssabangpalbang.review.domain.MemberReview;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record MemberReviewItemResponse(
        Long reviewId,
        List<ReviewTagView> tags,
        boolean liked,
        String content,
        OffsetDateTime createdAt
) {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static MemberReviewItemResponse from(
            MemberReview review,
            List<ReviewTagView> tags
    ) {
        return new MemberReviewItemResponse(
                review.getId(),
                tags,
                review.isLiked(),
                review.getContent(),
                OffsetDateTime.ofInstant(
                        review.getCreatedAt(),
                        SEOUL_ZONE_ID
                )
        );
    }
}
