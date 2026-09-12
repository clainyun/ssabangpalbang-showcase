package com.ssafy.ssabangpalbang.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record PasswordResetCodeRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email
) {

    public PasswordResetCodeRequest {
        if (email != null) {
            email = email.strip();
        }
    }

    public String normalizedEmail() {
        return email.toLowerCase(Locale.ROOT);
    }
}
