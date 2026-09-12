package com.ssafy.ssabangpalbang.member.event;

public record MemberMessagePushRequestedEvent(
        Long notificationId,
        Long recipientId,
        Long senderId,
        boolean serviceNotificationAgreed,
        String title,
        String body
) {
}
