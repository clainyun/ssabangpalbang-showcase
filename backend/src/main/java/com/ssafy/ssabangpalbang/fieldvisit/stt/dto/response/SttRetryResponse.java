package com.ssafy.ssabangpalbang.fieldvisit.stt.dto.response;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record SttRetryResponse(
        @Schema(example = "stt-9f2021ab")
        String sttId,

        @Schema(example = "7")
        Long studyId,

        @Schema(example = "503")
        Long checklistItemId,

        SttStatus status,

        @Schema(nullable = true, example = "830")
        Long sourceId,

        OffsetDateTime retryRequestedAt
) {

    public static SttRetryResponse from(
            SttJob job,
            OffsetDateTime retryRequestedAt
    ) {
        return new SttRetryResponse(
                job.getSttId(),
                job.getStudyId(),
                job.getChecklistItemId(),
                job.getStatus(),
                job.getFieldRecordId(),
                retryRequestedAt
        );
    }
}
