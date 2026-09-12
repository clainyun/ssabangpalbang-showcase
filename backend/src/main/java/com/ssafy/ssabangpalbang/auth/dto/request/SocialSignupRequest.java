package com.ssafy.ssabangpalbang.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record SocialSignupRequest(
        @NotBlank(message = "소셜 회원가입 임시 토큰은 필수입니다.")
        String socialSignupToken,

        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
        String nickname
) {

    public SocialSignupRequest {
        if (socialSignupToken != null) {
            socialSignupToken = socialSignupToken.strip();
        }
        if (email != null) {
            email = email.strip();
        }
        if (nickname != null) {
            nickname = nickname.strip();
        }
    }

    public String normalizedEmail() {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.toLowerCase(Locale.ROOT);
    }
}
