package com.ssafy.ssabangpalbang.community.repository.projection;

import java.math.BigDecimal;

public record PostInteractionRow(
        long likeCount,
        long commentCount,
        boolean likedByMe,
        BigDecimal hotScore,
        Boolean hot,
        Long hotRank
) {
}
