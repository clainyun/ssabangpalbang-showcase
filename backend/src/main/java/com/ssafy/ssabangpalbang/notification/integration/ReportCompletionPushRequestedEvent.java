package com.ssafy.ssabangpalbang.notification.integration;

public record ReportCompletionPushRequestedEvent(
        Long notificationId,
        Long recipientId,
        Long reportId,
        boolean serviceNotificationAgreed,
        String title,
        String body
) {
}
