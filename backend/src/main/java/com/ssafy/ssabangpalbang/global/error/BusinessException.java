package com.ssafy.ssabangpalbang.global.error;

import java.util.Map;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> data;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }

    public BusinessException(
            ErrorCode errorCode,
            String message
    ) {
        this(errorCode, message, null);
    }

    public BusinessException(
            ErrorCode errorCode,
            Map<String, Object> data
    ) {
        this(errorCode, errorCode.getMessage(), data);
    }

    public BusinessException(
            ErrorCode errorCode,
            String message,
            Map<String, Object> data
    ) {
        super(message);
        this.errorCode = errorCode;
        this.data = data == null ? null : Map.copyOf(data);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
