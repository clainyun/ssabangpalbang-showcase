package com.ssafy.ssabangpalbang.community.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

@JsonDeserialize(using = CommentCreateRequestDeserializer.class)
public record CommentCreateRequest(
        @NotNull(message = CONTENT_INVALID_REASON)
        String content
) {

    public static final String CONTENT_INVALID_REASON =
            "댓글은 1자 이상 1000자 이하이어야 합니다.";
}
