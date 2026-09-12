package com.ssafy.ssabangpalbang.notification.dto.response;

import java.time.OffsetDateTime;

public record NotificationReadAllResponse(
        int updatedCount,
        long unreadCount,
        OffsetDateTime readAt
) {
}
