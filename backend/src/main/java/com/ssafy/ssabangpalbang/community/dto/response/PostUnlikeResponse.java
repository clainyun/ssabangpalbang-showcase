package com.ssafy.ssabangpalbang.community.dto.response;

import com.ssafy.ssabangpalbang.community.domain.Post;
import com.ssafy.ssabangpalbang.community.repository.projection.PostInteractionRow;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record PostUnlikeResponse(
        Long postId,
        boolean likedByMe,
        long likeCount,
        long commentCount,
        long viewCount,
        boolean isHot,
        BigDecimal hotScore,
        Long hotRank,
        OffsetDateTime unlikedAt
) {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static PostUnlikeResponse from(
            Post post,
            PostInteractionRow interactions,
            Instant unlikedAt
    ) {
        boolean isHot = Boolean.TRUE.equals(interactions.hot());
        return new PostUnlikeResponse(
                post.getId(),
                false,
                interactions.likeCount(),
                interactions.commentCount(),
                post.getViewCount(),
                isHot,
                interactions.hotScore(),
                isHot ? interactions.hotRank() : null,
                OffsetDateTime.ofInstant(unlikedAt, SEOUL_ZONE_ID)
        );
    }
}
