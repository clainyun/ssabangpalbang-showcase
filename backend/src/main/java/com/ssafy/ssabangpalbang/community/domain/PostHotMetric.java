package com.ssafy.ssabangpalbang.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Immutable
@Table(name = "post_hot_metric")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostHotMetric {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Enumerated(EnumType.STRING)
    @Column(name = "board_type", nullable = false, length = 20)
    private BoardType boardType;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "comment_count", nullable = false)
    private long commentCount;

    @Column(name = "hot_score", nullable = false)
    private BigDecimal hotScore;

    @Column(name = "is_hot", nullable = false)
    private boolean hot;

    @Column(name = "hot_rank", nullable = false)
    private Long hotRank;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
