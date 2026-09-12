package com.ssafy.ssabangpalbang.study.event;

public record StudyNoticePushRequestedEvent(
        Long notificationId,
        Long recipientId,
        Long studyId,
        Long noticeId,
        String title,
        String body,
        boolean serviceNotificationAgreed
) {
}
