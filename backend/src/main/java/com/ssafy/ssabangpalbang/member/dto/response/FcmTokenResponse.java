package com.ssafy.ssabangpalbang.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record FcmTokenResponse(
        @Schema(description = "등록 또는 갱신된 앱 설치별 UUID")
        String deviceId,
        @Schema(description = "등록 성공 여부", example = "true")
        boolean registered,
        @Schema(description = "토큰이 마지막으로 등록 또는 갱신된 시각")
        OffsetDateTime updatedAt
) {
}
