package com.ssafy.ssabangpalbang.media.service;

import java.time.Instant;

public record MediaAccessUrl(
        String url,
        Instant expiresAt
) {
}
