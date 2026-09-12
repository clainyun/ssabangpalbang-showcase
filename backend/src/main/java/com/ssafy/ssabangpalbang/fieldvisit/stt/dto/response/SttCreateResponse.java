package com.ssafy.ssabangpalbang.fieldvisit.stt.dto.response;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record SttCreateResponse(
        @Schema(example = "stt-4c7186fb")
        String sttId,
        @Schema(example = "7")
        Long studyId,
        @Schema(example = "100")
        Long sessionId,
        @Schema(example = "90")
        Long audioFileId,
        @Schema(example = "501")
        Long checklistItemId,
        SttStatus status,
        @Schema(nullable = true, example = "830")
        Long sourceId,
        OffsetDateTime requestedAt
) {

    public static SttCreateResponse from(SttJob job) {
        return new SttCreateResponse(
                job.getSttId(),
                job.getStudyId(),
                job.getSessionId(),
                job.getAudioFileId(),
                job.getChecklistItemId(),
                job.getStatus(),
                job.getFieldRecordId(),
                job.getRequestedAt()
        );
    }
}
