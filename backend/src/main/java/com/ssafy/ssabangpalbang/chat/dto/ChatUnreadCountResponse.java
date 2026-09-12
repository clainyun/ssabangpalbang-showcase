package com.ssafy.ssabangpalbang.chat.dto;

import java.time.OffsetDateTime;

public record ChatUnreadCountResponse(
        Long studyId,
        int unreadCount,
        OffsetDateTime lastReadAt
) {
}
