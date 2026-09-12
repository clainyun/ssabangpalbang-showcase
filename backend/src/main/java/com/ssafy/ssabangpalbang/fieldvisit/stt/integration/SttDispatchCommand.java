package com.ssafy.ssabangpalbang.fieldvisit.stt.integration;

public record SttDispatchCommand(
        String sttId,
        int attemptNo,
        Long audioFileId,
        String objectKey,
        String contentType,
        String language
) {
}
