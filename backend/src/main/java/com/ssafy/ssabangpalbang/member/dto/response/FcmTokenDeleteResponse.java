package com.ssafy.ssabangpalbang.member.dto.response;

import java.time.OffsetDateTime;

public record FcmTokenDeleteResponse(
        String deviceId,
        boolean registered,
        OffsetDateTime deletedAt
) {
}
