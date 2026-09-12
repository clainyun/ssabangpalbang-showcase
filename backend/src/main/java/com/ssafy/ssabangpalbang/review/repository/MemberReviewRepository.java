package com.ssafy.ssabangpalbang.review.repository;

import com.ssafy.ssabangpalbang.review.domain.MemberReview;
import com.ssafy.ssabangpalbang.review.repository.projection.MemberReviewTagCountRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MemberReviewRepository
        extends JpaRepository<MemberReview, Long> {

    boolean existsByStudyIdAndReviewerIdAndRevieweeId(
            Long studyId,
            Long reviewerId,
            Long revieweeId
    );

    @Query("""
            SELECT review.revieweeId
            FROM MemberReview review
            WHERE review.studyId = :studyId
              AND review.reviewerId = :reviewerId
            """)
    List<Long> findRevieweeIdsByStudyIdAndReviewerId(
            @Param("studyId") Long studyId,
            @Param("reviewerId") Long reviewerId
    );

    long countByRevieweeId(Long revieweeId);

    long countByRevieweeIdAndLikedTrue(Long revieweeId);

    /**
     * 피평가자가 받은 전체 태그를 tag_code별로 집계한다.
     * count 내림차순, 동점이면 tag_code 오름차순으로 안정 정렬한다.
     */
    @Query("""
            SELECT tag.tagCode AS tagCode,
                   COUNT(tag.id) AS tagCount
            FROM MemberReviewTag tag, MemberReview review
            WHERE review.id = tag.reviewId
              AND review.revieweeId = :revieweeId
            GROUP BY tag.tagCode
            ORDER BY COUNT(tag.id) DESC, tag.tagCode ASC
            """)
    List<MemberReviewTagCountRow> aggregateTagCountsByRevieweeId(
            @Param("revieweeId") Long revieweeId
    );

    @Query("""
            SELECT review
            FROM MemberReview review
            WHERE review.revieweeId = :revieweeId
            ORDER BY review.createdAt DESC, review.id DESC
            """)
    List<MemberReview> findFirstPageByRevieweeId(
            @Param("revieweeId") Long revieweeId,
            Pageable pageable
    );

    @Query("""
            SELECT review
            FROM MemberReview review
            WHERE review.revieweeId = :revieweeId
              AND (
                  review.createdAt < :createdAt
                  OR (
                      review.createdAt = :createdAt
                      AND review.id < :reviewId
                  )
              )
            ORDER BY review.createdAt DESC, review.id DESC
            """)
    List<MemberReview> findNextPageByRevieweeId(
            @Param("revieweeId") Long revieweeId,
            @Param("createdAt") Instant createdAt,
            @Param("reviewId") Long reviewId,
            Pageable pageable
    );
}
