package com.ssafy.ssabangpalbang.chat.dto;

import java.util.List;

public record ChatMessageListResponse(
        Long studyId,
        List<ChatMessageItemResponse> content,
        Long nextCursor,
        boolean hasNext
) {
}
