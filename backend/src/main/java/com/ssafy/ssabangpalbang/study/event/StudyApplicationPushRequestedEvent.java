package com.ssafy.ssabangpalbang.study.event;

public record StudyApplicationPushRequestedEvent(
        Long notificationId,
        Long recipientId,
        Long studyId,
        String notificationType,
        String targetScreen,
        String title,
        String body,
        boolean serviceNotificationAgreed
) {
}
