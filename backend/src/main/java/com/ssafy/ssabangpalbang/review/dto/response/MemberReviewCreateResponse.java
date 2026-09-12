package com.ssafy.ssabangpalbang.review.dto.response;

import com.ssafy.ssabangpalbang.review.domain.MemberReview;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record MemberReviewCreateResponse(
        Long reviewId,
        Long studyId,
        Long reviewedMemberId,
        List<ReviewTagView> tags,
        boolean liked,
        String content,
        OffsetDateTime createdAt
) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static MemberReviewCreateResponse from(
            MemberReview review,
            List<ReviewTagView> tags
    ) {
        return new MemberReviewCreateResponse(
                review.getId(),
                review.getStudyId(),
                review.getRevieweeId(),
                tags,
                review.isLiked(),
                review.getContent(),
                review.getCreatedAt().atZone(SEOUL).toOffsetDateTime()
        );
    }
}
