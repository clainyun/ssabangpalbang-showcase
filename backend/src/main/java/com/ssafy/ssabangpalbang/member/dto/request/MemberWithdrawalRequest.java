package com.ssafy.ssabangpalbang.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원 탈퇴 요청")
public record MemberWithdrawalRequest(
        @Schema(
                description = "폐기할 현재 Refresh Token",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Refresh Token은 필수 값입니다.")
        String refreshToken,
        @Schema(
                description = "회원 탈퇴 확인 문구",
                example = "회원탈퇴",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String confirmationText
) {
}
