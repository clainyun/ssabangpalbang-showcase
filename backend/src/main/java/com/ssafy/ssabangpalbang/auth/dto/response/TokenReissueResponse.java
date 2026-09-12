package com.ssafy.ssabangpalbang.auth.dto.response;

import com.ssafy.ssabangpalbang.auth.token.IssuedTokens;

public record TokenReissueResponse(
        String accessToken,
        String refreshToken
) {

    public static TokenReissueResponse from(IssuedTokens tokens) {
        return new TokenReissueResponse(
                tokens.accessToken(),
                tokens.refreshToken()
        );
    }
}
