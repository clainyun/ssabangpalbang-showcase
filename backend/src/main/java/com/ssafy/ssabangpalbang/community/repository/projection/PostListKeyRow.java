package com.ssafy.ssabangpalbang.community.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

public record PostListKeyRow(
        Long postId,
        Instant createdAt,
        BigDecimal hotScore
) {
}
