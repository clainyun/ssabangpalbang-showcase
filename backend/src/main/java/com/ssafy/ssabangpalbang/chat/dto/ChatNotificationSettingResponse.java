package com.ssafy.ssabangpalbang.chat.dto;

public record ChatNotificationSettingResponse(
        Long studyId,
        boolean pushEnabled
) {
}
