package com.ssafy.ssabangpalbang.auth.oauth;

public record SocialOAuthAuthorization(
        String authorizationCode,
        String redirectUri,
        String state
) {
}
