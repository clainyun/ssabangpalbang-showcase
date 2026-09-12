package com.ssafy.ssabangpalbang.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Schema(description = "회원 탈퇴 결과")
public record MemberWithdrawalResponse(
        @Schema(description = "탈퇴 처리된 회원 ID", example = "1")
        Long memberId,
        @Schema(
                description = "회원 탈퇴 처리 시각",
                example = "2026-07-24T15:45:00+09:00"
        )
        OffsetDateTime withdrawnAt
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static MemberWithdrawalResponse of(
            Long memberId,
            Instant withdrawnAt
    ) {
        return new MemberWithdrawalResponse(
                memberId,
                OffsetDateTime.ofInstant(withdrawnAt, SEOUL)
        );
    }
}
