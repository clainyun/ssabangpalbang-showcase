package com.ssafy.ssabangpalbang.notification.support;

import java.time.OffsetDateTime;

public record NotificationCursor(
        int version,
        long recipientId,
        long snapshotMaxId,
        OffsetDateTime snapshotAt,
        boolean unreadOnly,
        boolean unreadGroup,
        OffsetDateTime sentAt,
        long notificationId
) {

    public static final int CURRENT_VERSION = 1;

    public static NotificationCursor of(
            long recipientId,
            long snapshotMaxId,
            OffsetDateTime snapshotAt,
            boolean unreadOnly,
            boolean unreadGroup,
            OffsetDateTime sentAt,
            long notificationId
    ) {
        return new NotificationCursor(
                CURRENT_VERSION,
                recipientId,
                snapshotMaxId,
                snapshotAt,
                unreadOnly,
                unreadGroup,
                sentAt,
                notificationId
        );
    }
}
