package com.ssafy.ssabangpalbang.community.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record CommentListCondition(
        @Positive(message = "커서는 1 이상의 숫자여야 합니다.")
        Long cursor,
        @Min(
                value = 1,
                message = "조회 개수는 1 이상 100 이하이어야 합니다."
        )
        @Max(
                value = 100,
                message = "조회 개수는 1 이상 100 이하이어야 합니다."
        )
        Integer size
) {

    public static final int DEFAULT_SIZE = 20;

    public CommentListCondition normalize() {
        return new CommentListCondition(
                cursor,
                size == null ? DEFAULT_SIZE : size
        );
    }
}
