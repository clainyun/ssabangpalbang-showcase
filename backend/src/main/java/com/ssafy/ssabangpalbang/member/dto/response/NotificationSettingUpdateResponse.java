package com.ssafy.ssabangpalbang.member.dto.response;

import java.time.OffsetDateTime;

public record NotificationSettingUpdateResponse(
        boolean serviceNotificationAgreed,
        boolean adNotificationAgreed,
        OffsetDateTime updatedAt
) {
}
