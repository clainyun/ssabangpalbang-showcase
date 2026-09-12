package com.ssafy.ssabangpalbang.community.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

@JsonDeserialize(using = CommentUpdateRequestDeserializer.class)
public record CommentUpdateRequest(
        @NotNull(message = CONTENT_INVALID_REASON)
        String content
) {

    public static final String CONTENT_INVALID_REASON =
            CommentCreateRequest.CONTENT_INVALID_REASON;
}
