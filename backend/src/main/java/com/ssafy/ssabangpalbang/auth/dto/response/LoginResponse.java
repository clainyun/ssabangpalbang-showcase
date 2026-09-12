package com.ssafy.ssabangpalbang.auth.dto.response;

import com.ssafy.ssabangpalbang.auth.token.IssuedTokens;
import com.ssafy.ssabangpalbang.member.domain.Member;

public record LoginResponse(
        Long memberId,
        String email,
        String nickname,
        String profileImageUrl,
        String selectedCharacterId,
        boolean onboardingCompleted,
        String accessToken,
        String refreshToken
) {

    public static LoginResponse from(
            Member member,
            boolean onboardingCompleted,
            IssuedTokens tokens
    ) {
        return new LoginResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getProfileImageUrl(),
                member.getSelectedCharacterId(),
                onboardingCompleted,
                tokens.accessToken(),
                tokens.refreshToken()
        );
    }
}
