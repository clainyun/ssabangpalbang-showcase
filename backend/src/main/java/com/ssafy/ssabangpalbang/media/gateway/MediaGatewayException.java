package com.ssafy.ssabangpalbang.media.gateway;

public class MediaGatewayException extends RuntimeException {

    private final Failure failure;

    public MediaGatewayException(Failure failure, Throwable cause) {
        super("Media gateway request failed: " + failure, cause);
        this.failure = failure;
    }

    public MediaGatewayException(Failure failure) {
        super("Media gateway request failed: " + failure);
        this.failure = failure;
    }

    public Failure getFailure() {
        return failure;
    }

    public enum Failure {
        NOT_FOUND,
        SIZE_MISMATCH,
        CONTENT_TYPE_MISMATCH,
        CHANGED,
        REJECTED,
        UNAVAILABLE
    }
}
