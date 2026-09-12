package com.ssafy.ssabangpalbang.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * 비밀번호 재설정 코드 검증 요청.
 * confirm 요청과 동일한 이메일·코드 제약을 사용하되 새 비밀번호는 받지 않는다.
 */
public record PasswordResetVerifyRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "인증 코드는 필수입니다.")
        String code
) {

    public PasswordResetVerifyRequest {
        if (email != null) {
            email = email.strip();
        }
        if (code != null) {
            code = code.strip();
        }
    }

    public String normalizedEmail() {
        return email.toLowerCase(Locale.ROOT);
    }
}
