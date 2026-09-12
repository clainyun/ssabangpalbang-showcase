package com.ssafy.ssabangpalbang.member.dto.response;

public record NotificationSettingResponse(
        boolean serviceNotificationAgreed,
        boolean adNotificationAgreed
) {
}
