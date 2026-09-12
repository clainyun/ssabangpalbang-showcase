package com.ssafy.ssabangpalbang.fieldvisit.stt.integration.kafka;

import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchCommand;

public record SttRequestMessage(
        int schemaVersion,
        String sttId,
        int attemptNo,
        Long audioFileId,
        String objectKey,
        String contentType,
        String language
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static SttRequestMessage from(SttDispatchCommand command) {
        return new SttRequestMessage(
                CURRENT_SCHEMA_VERSION,
                command.sttId(),
                command.attemptNo(),
                command.audioFileId(),
                command.objectKey(),
                command.contentType(),
                command.language()
        );
    }
}
