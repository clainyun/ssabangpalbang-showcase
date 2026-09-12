package com.ssafy.ssabangpalbang.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SocialLoginRequest(
        @NotBlank(message = "소셜 로그인 제공자는 필수입니다.")
        String provider,

        @NotBlank(message = "인가 코드는 필수입니다.")
        @Size(max = 2048, message = "인가 코드가 너무 깁니다.")
        String authorizationCode,

        @Size(max = 500, message = "Redirect URI가 너무 깁니다.")
        String redirectUri,

        @Size(max = 500, message = "OAuth state가 너무 깁니다.")
        String state
) {
}
