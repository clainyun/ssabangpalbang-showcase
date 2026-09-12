package com.ssafy.ssabangpalbang.review.dto.response;

import com.ssafy.ssabangpalbang.review.domain.ReviewTag;

/**
 * 개별 평가에 붙은 태그를 코드와 함께 label/emoji로 보강한 응답 조각이다.
 * 등록 응답과 목록 아이템에서 공유해 springdoc 스키마 이름 충돌을 피한다.
 */
public record ReviewTagView(
        String code,
        String label,
        String emoji
) {

    public static ReviewTagView of(ReviewTag tag) {
        return new ReviewTagView(
                tag.getCode(),
                tag.getLabel(),
                tag.getEmoji()
        );
    }
}
