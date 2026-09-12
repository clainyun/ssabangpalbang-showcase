package com.ssafy.ssabangpalbang.chat.dto;

import java.time.OffsetDateTime;

public record ChatReadResponse(
        Long studyId,
        OffsetDateTime lastReadAt,
        int unreadCount
) {
}
