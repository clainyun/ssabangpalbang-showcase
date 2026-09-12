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

/**
 * 한 건의 멤버 평가(MemberReview)에 부여된 태그다.
 *
 * <p>주변 review 도메인 관례에 맞춰 연관관계 대신 단순 Long 컬럼(reviewId)으로 참조한다.
 * tag_code는 ReviewTag enum 코드 문자열이며 서비스에서 검증한다.</p>
 */
@Getter
@Entity
@Table(name = "member_review_tag")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberReviewTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "tag_code", nullable = false, length = 40)
    private String tagCode;

    private MemberReviewTag(Long reviewId, String tagCode) {
        this.reviewId = reviewId;
        this.tagCode = tagCode;
    }

    public static MemberReviewTag create(Long reviewId, ReviewTag tag) {
        return new MemberReviewTag(reviewId, tag.getCode());
    }
}
