package com.ssafy.ssabangpalbang.auth.token;

import com.ssafy.ssabangpalbang.auth.domain.SocialProvider;

public record SocialSignupTokenClaims(
        SocialProvider provider,
        String socialUserId,
        String email
) {
}
