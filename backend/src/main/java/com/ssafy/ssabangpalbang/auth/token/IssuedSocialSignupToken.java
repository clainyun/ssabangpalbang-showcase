package com.ssafy.ssabangpalbang.auth.token;

public record IssuedSocialSignupToken(
        String token,
        int expiresInSeconds
) {
}
