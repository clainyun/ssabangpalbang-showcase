package com.ssafy.ssabangpalbang.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record FcmTestPushResponse(
        @Schema(description = "테스트 알림을 요청한 현재 기기의 식별자")
        String deviceId,
        @Schema(description = "서버가 FCM 전송을 시도할 예약 시각")
        OffsetDateTime scheduledAt
) {
}
