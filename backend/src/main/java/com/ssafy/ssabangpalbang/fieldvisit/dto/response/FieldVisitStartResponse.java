package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "임장 시작 응답")
public record FieldVisitStartResponse(
        Long studyId,
        Long apartmentId,
        Integer distanceMeters,
        Integer allowedRadiusMeters,
        SessionBody session,
        ParticipantBody participant,
        boolean checklistGenerated
) {
    @Schema(description = "임장 세션 요약")
    public record SessionBody(
            Long sessionId,
            String status,
            OffsetDateTime startedAt
    ) {
    }

    @Schema(description = "개인 참여 요약")
    public record ParticipantBody(
            Long participantId,
            String status,
            OffsetDateTime startedAt
    ) {
    }
}
