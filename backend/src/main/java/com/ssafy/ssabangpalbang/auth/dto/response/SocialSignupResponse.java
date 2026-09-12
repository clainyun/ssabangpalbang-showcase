package com.ssafy.ssabangpalbang.auth.dto.response;

import com.ssafy.ssabangpalbang.auth.domain.SocialProvider;
import com.ssafy.ssabangpalbang.auth.token.IssuedTokens;
import com.ssafy.ssabangpalbang.member.domain.Member;

public record SocialSignupResponse(
        Long memberId,
        String email,
        String nickname,
        String provider,
        String selectedCharacterId,
        boolean onboardingCompleted,
        String accessToken,
        String refreshToken
) {

    public static SocialSignupResponse from(
            Member member,
            SocialProvider provider,
            IssuedTokens tokens
    ) {
        return new SocialSignupResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                provider.name(),
                member.getSelectedCharacterId(),
                false,
                tokens.accessToken(),
                tokens.refreshToken()
        );
    }
}
