package com.ssafy.ssabangpalbang.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 비밀번호 재설정 코드 검증 응답.
 * {@code valid=true}이면 입력한 코드가 유효함을 의미하며, 코드는 소비되지 않는다.
 */
@Schema(name = "AuthPasswordResetVerifyResponse")
public record PasswordResetVerifyResponse(
        @Schema(description = "코드 유효 여부", example = "true")
        boolean valid
) {

    public static PasswordResetVerifyResponse of(boolean valid) {
        return new PasswordResetVerifyResponse(valid);
    }
}
