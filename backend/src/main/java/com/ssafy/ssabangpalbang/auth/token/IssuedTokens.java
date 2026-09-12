package com.ssafy.ssabangpalbang.auth.token;

import java.time.Duration;

public record IssuedTokens(
        String accessToken,
        String refreshToken,
        Duration refreshTokenTtl
) {
}
