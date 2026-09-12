package com.ssafy.ssabangpalbang.notification.dto.response;

import java.time.OffsetDateTime;

public record NotificationReadResponse(
        Long notificationId,
        boolean isRead,
        OffsetDateTime readAt,
        long unreadCount
) {
}
