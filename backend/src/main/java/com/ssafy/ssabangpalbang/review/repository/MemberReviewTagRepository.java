package com.ssafy.ssabangpalbang.review.repository;

import com.ssafy.ssabangpalbang.review.domain.MemberReviewTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MemberReviewTagRepository
        extends JpaRepository<MemberReviewTag, Long> {

    /**
     * 여러 평가의 태그를 한 번에 조회한다. 목록 조회에서 N+1을 피하기 위한 배치 로딩이다.
     * review_id 오름차순, id 오름차순으로 안정 정렬한다.
     */
    @Query("""
            SELECT tag
            FROM MemberReviewTag tag
            WHERE tag.reviewId IN :reviewIds
            ORDER BY tag.reviewId ASC, tag.id ASC
            """)
    List<MemberReviewTag> findByReviewIdIn(
            @Param("reviewIds") Collection<Long> reviewIds
    );
}
