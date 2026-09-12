package com.ssafy.ssabangpalbang.global.error;

public record ValidationError(
        String field,
        String reason
) {
}
