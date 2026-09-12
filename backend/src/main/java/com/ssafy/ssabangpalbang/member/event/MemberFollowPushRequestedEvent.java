package com.ssafy.ssabangpalbang.member.event;

public record MemberFollowPushRequestedEvent(
        Long notificationId,
        Long recipientId,
        Long followerId,
        boolean serviceNotificationAgreed,
        String title,
        String body
) {
}
