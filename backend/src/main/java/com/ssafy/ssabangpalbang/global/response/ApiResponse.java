package com.ssafy.ssabangpalbang.global.response;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        OffsetDateTime timestamp
) {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static <T> ApiResponse<T> success(
            ResponseCode responseCode,
            T data
    ) {
        return new ApiResponse<>(
                true,
                responseCode.getCode(),
                responseCode.getMessage(),
                data,
                now()
        );
    }

    public static ApiResponse<Void> success(ResponseCode responseCode) {
        return success(responseCode, null);
    }

    public static ApiResponse<Object> failure(ResponseCode responseCode) {
        return failure(responseCode, null);
    }

    public static ApiResponse<Object> failure(
            ResponseCode responseCode,
            Object data
    ) {
        return new ApiResponse<>(
                false,
                responseCode.getCode(),
                responseCode.getMessage(),
                data,
                now()
        );
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(SEOUL_ZONE_ID);
    }
}
