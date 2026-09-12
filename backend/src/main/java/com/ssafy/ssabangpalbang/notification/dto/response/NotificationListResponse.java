package com.ssafy.ssabangpalbang.notification.dto.response;

import java.util.List;

public record NotificationListResponse(
        List<NotificationItemResponse> content,
        long unreadCount,
        String nextCursor,
        boolean hasNext
) {
}
