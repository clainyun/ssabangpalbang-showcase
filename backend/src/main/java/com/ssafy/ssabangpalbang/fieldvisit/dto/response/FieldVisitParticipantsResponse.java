package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "참여자별 임장 상태 조회 응답")
public record FieldVisitParticipantsResponse(
        Long studyId,
        Long sessionId,
        String status,
        int participantCount,
        int inProgressCount,
        int endedCount,
        int notJoinedCount,
        List<ParticipantBody> participants
) {

    // 이 패키지에 중첩 record 이름이 'ParticipantBody'로 여러 개(Status/Start/Finish 등)
    // 있어, springdoc이 단순 클래스명으로 스키마를 등록하면 서로 덮어써 /v3/api-docs 계약이
    // 뭉개집니다. 고유 이름을 부여해 이 엔드포인트의 14개 필드가 올바로 게시되게 합니다
    // (FieldVisitFinishCancelResponse가 쓰는 회피법과 동일).
    @Schema(name = "FieldVisitParticipantBody", description = "세션 고정 후보 한 명의 임장 상태")
    public record ParticipantBody(
            Long participantId,
            Long memberId,
            String nickname,
            String profileImageUrl,
            String selectedCharacterId,
            String role,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            String endReason,
            int stayDurationSec,
            boolean isMe,
            boolean isLeader,
            boolean canRequestFinish
    ) {
    }
}
