package com.ssafy.ssabangpalbang.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth")
public record SocialOAuthProperties(
        Provider kakao,
        Provider naver
) {

    public SocialOAuthProperties {
        kakao = kakao == null ? Provider.empty() : kakao;
        naver = naver == null ? Provider.empty() : naver;
    }

    public record Provider(
            String clientId,
            String clientSecret
    ) {
        private static Provider empty() {
            return new Provider("", "");
        }
    }
}
