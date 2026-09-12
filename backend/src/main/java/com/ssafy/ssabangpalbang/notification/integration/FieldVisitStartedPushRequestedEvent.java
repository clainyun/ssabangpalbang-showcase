package com.ssafy.ssabangpalbang.notification.integration;

public record FieldVisitStartedPushRequestedEvent(
        Long notificationId,
        Long recipientId,
        Long studyId,
        Long sessionId,
        boolean serviceNotificationAgreed,
        String title,
        String body
) {
}
