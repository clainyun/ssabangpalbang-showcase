package com.ssafy.ssabangpalbang.review.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 스터디 멤버 평가 등록 요청이다. 별점 대신 태그와 좋아요 신호를 받는다.
 *
 * <p>tags 각 원소는 유효한 ReviewTag 코드여야 하고, 중복이 없어야 하며, 최소 하나 이상의 신호
 * (tags 비어있지 않음 또는 liked=true)가 필요하다. 이 교차 규칙은 서비스에서 검증한다.</p>
 */
public record MemberReviewCreateRequest(
        @Size(max = 15, message = "태그는 최대 15개까지 선택할 수 있습니다.")
        List<String> tags,

        boolean liked,

        @Size(max = 500, message = "리뷰는 500자 이하여야 합니다.")
        String content
) {

    public List<String> tagsOrEmpty() {
        return tags == null ? List.of() : tags;
    }
}
