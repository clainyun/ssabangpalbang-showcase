package com.ssafy.ssabangpalbang.member.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FcmTokenRegisterRequest(
        @NotNull(message = "FCM 토큰은 필수 값입니다.")
        @Size(min = 1, message = "FCM 토큰은 필수 값입니다.")
        String fcmToken
) {
}
