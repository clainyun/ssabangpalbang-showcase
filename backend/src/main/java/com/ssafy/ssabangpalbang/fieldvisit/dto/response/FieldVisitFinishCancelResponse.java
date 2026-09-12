package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "개인 임장 종료 취소 응답")
public record FieldVisitFinishCancelResponse(
        Long studyId,
        Long sessionId,
        ParticipantBody participant
) {
    // 고유 스키마명 지정: fieldvisit/dto/response 하위에 동일 단순명 ParticipantBody record가
    // 여러 개라 springdoc가 하나의 컴포넌트로 병합해 이 응답 스키마가 3개 필드로 덮어써지던
    // 문제를 끊는다(런타임 JSON은 6개 필드 정상). 이 엔드포인트 응답 문서만 정확히 노출.
    @Schema(name = "FieldVisitFinishCancelParticipant", description = "다시 진행 중이 된 참여자 정보")
    public record ParticipantBody(
            Long participantId,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            String endReason,
            Integer stayDurationSec
    ) {
    }
}
