package com.ssafy.ssabangpalbang.auth.dto.response;

import com.ssafy.ssabangpalbang.auth.token.IssuedTokens;
import com.ssafy.ssabangpalbang.member.domain.Member;

public record SignupResponse(
        Long memberId,
        String email,
        String nickname,
        String selectedCharacterId,
        boolean onboardingCompleted,
        String accessToken,
        String refreshToken
) {

    public static SignupResponse from(
            Member member,
            IssuedTokens tokens
    ) {
        return new SignupResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getSelectedCharacterId(),
                false,
                tokens.accessToken(),
                tokens.refreshToken()
        );
    }
}
