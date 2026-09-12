package com.ssafy.ssabangpalbang.notification.dto.response;

import com.ssafy.ssabangpalbang.notification.entity.Notification;

import java.time.OffsetDateTime;

public record NotificationItemResponse(
        Long notificationId,
        String category,
        String type,
        String title,
        String body,
        NotificationActorResponse actor,
        boolean isRead,
        OffsetDateTime readAt,
        String targetScreen,
        Long targetId,
        Long targetSubId,
        boolean targetAvailable,
        OffsetDateTime sentAt
) {

    public static NotificationItemResponse from(
            Notification notification,
            NotificationActorResponse actor
    ) {
        return new NotificationItemResponse(
                notification.getId(),
                notification.getCategory().name(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                actor,
                notification.isRead(),
                notification.getReadAt(),
                notification.getTargetScreen(),
                notification.getTargetId(),
                notification.getTargetSubId(),
                true,
                notification.getSentAt()
        );
    }
}
