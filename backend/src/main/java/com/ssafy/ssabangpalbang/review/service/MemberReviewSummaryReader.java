package com.ssafy.ssabangpalbang.review.service;

import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewSummaryResponse;
import com.ssafy.ssabangpalbang.review.repository.MemberReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 피평가자의 평가 요약(topTags, 받은 좋아요 수, 평가 수)을 조립한다.
 * 평가 목록 조회와 회원 프로필(내/공개)에서 공통으로 사용한다.
 */
@Component
@RequiredArgsConstructor
public class MemberReviewSummaryReader {

    private final MemberReviewRepository memberReviewRepository;

    public MemberReviewSummaryResponse read(Long revieweeId) {
        return MemberReviewSummaryResponse.of(
                memberReviewRepository.countByRevieweeId(revieweeId),
                memberReviewRepository.countByRevieweeIdAndLikedTrue(revieweeId),
                memberReviewRepository.aggregateTagCountsByRevieweeId(revieweeId)
        );
    }
}
