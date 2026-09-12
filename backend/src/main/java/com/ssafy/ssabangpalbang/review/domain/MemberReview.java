package com.ssafy.ssabangpalbang.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Entity
@Table(name = "member_review")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false)
    private Long studyId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "reviewee_id", nullable = false)
    private Long revieweeId;

    /**
     * 별점이다. 태그·좋아요 기반 개편(BE-028) 이후 새 평가는 별점을 남기지 않아 null이며,
     * 컬럼과 기존 데이터 호환을 위해 nullable로 유지한다.
     */
    @Column
    private Integer rating;

    @Column(nullable = false)
    private boolean liked;

    @Column(length = 500)
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private MemberReview(
            Long studyId,
            Long reviewerId,
            Long revieweeId,
            boolean liked,
            String content
    ) {
        this.studyId = studyId;
        this.reviewerId = reviewerId;
        this.revieweeId = revieweeId;
        this.rating = null;
        this.liked = liked;
        this.content = content;
    }

    public static MemberReview create(
            Long studyId,
            Long reviewerId,
            Long revieweeId,
            boolean liked,
            String content
    ) {
        return new MemberReview(
                studyId,
                reviewerId,
                revieweeId,
                liked,
                content
        );
    }
}
