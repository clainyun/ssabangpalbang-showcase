package com.ssafy.ssabangpalbang.auth.oauth;

import java.util.Locale;

public record SocialOAuthUser(
        String socialUserId,
        String email
) {

    public SocialOAuthUser {
        socialUserId = normalizeRequired(socialUserId);
        email = normalizeEmail(email);
    }

    private static String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "소셜 사용자 식별자는 필수입니다."
            );
        }
        return value.strip();
    }

    private static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
