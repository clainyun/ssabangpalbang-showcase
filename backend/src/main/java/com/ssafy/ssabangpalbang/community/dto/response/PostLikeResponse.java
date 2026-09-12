package com.ssafy.ssabangpalbang.community.dto.response;

import com.ssafy.ssabangpalbang.community.domain.Post;
import com.ssafy.ssabangpalbang.community.repository.projection.PostInteractionRow;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record PostLikeResponse(
        Long postId,
        boolean likedByMe,
        long likeCount,
        long commentCount,
        long viewCount,
        boolean isHot,
        BigDecimal hotScore,
        Long hotRank,
        OffsetDateTime likedAt
) {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static PostLikeResponse from(
            Post post,
            PostInteractionRow interactions,
            Instant likedAt
    ) {
        boolean isHot = Boolean.TRUE.equals(interactions.hot());
        return new PostLikeResponse(
                post.getId(),
                true,
                interactions.likeCount(),
                interactions.commentCount(),
                post.getViewCount(),
                isHot,
                interactions.hotScore(),
                isHot ? interactions.hotRank() : null,
                OffsetDateTime.ofInstant(likedAt, SEOUL_ZONE_ID)
        );
    }
}
