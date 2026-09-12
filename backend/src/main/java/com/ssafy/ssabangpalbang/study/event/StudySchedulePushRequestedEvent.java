package com.ssafy.ssabangpalbang.study.event;

public record StudySchedulePushRequestedEvent(Long notificationId, Long recipientId, Long studyId,
                                               String notificationType, String title, String body,
                                               boolean serviceNotificationAgreed) { }
