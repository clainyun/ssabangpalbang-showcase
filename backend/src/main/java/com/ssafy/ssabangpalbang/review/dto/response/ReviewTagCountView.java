package com.ssafy.ssabangpalbang.review.dto.response;

import com.ssafy.ssabangpalbang.review.domain.ReviewTag;

/**
 * 피평가자가 받은 태그 집계(topTags) 한 건이다.
 * code/label/emoji/category와 받은 횟수(count)를 담는다.
 */
public record ReviewTagCountView(
        String code,
        String label,
        String emoji,
        String category,
        long count
) {

    public static ReviewTagCountView of(ReviewTag tag, long count) {
        return new ReviewTagCountView(
                tag.getCode(),
                tag.getLabel(),
                tag.getEmoji(),
                tag.getCategory().getCode(),
                count
        );
    }
}
