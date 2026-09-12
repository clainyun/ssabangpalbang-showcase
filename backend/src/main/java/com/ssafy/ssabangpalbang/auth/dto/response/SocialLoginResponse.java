package com.ssafy.ssabangpalbang.auth.dto.response;

public sealed interface SocialLoginResponse
        permits SocialLoginResponse.ExistingMember,
        SocialLoginResponse.SignupRequired {

    record ExistingMember(
            boolean signupRequired,
            Long memberId,
            String email,
            String nickname,
            String profileImageUrl,
            String selectedCharacterId,
            boolean onboardingCompleted,
            String accessToken,
            String refreshToken
    ) implements SocialLoginResponse {
    }

    record SignupRequired(
            boolean signupRequired,
            String provider,
            String email,
            boolean emailRequired,
            String socialSignupToken,
            int expiresIn
    ) implements SocialLoginResponse {
    }
}
