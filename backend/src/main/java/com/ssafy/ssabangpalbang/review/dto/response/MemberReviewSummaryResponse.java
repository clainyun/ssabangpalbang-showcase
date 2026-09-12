package com.ssafy.ssabangpalbang.review.dto.response;

import com.ssafy.ssabangpalbang.review.domain.ReviewTag;
import com.ssafy.ssabangpalbang.review.repository.projection.MemberReviewTagCountRow;

import java.util.List;

/**
 * 피평가자가 받은 평가 요약이다. 태그 집계(topTags), 받은 좋아요 수(likeReceivedCount),
 * 전체 평가 수(reviewCount)를 담는다.
 */
public record MemberReviewSummaryResponse(
        List<ReviewTagCountView> topTags,
        long likeReceivedCount,
        long reviewCount
) {

    public static MemberReviewSummaryResponse of(
            long reviewCount,
            long likeReceivedCount,
            List<MemberReviewTagCountRow> tagRows
    ) {
        List<ReviewTagCountView> topTags = tagRows.stream()
                .flatMap(row -> ReviewTag.findByCode(row.getTagCode())
                        .map(tag -> ReviewTagCountView.of(
                                tag,
                                row.getTagCount()
                        ))
                        .stream())
                .toList();
        return new MemberReviewSummaryResponse(
                topTags,
                likeReceivedCount,
                reviewCount
        );
    }
}
