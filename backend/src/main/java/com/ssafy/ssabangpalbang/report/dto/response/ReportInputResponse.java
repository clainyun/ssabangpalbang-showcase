package com.ssafy.ssabangpalbang.report.dto.response;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecordSourceType;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "AI-004 정규화용 권위 있는 임장 원본 스냅샷")
public record ReportInputResponse(
        int schemaVersion,
        Long reportId,
        Long studyId,
        Long apartmentId,
        Long fieldSessionId,
        FieldSessionStatus sessionStatus,
        OffsetDateTime sessionStartedAt,
        OffsetDateTime sessionEndedAt,
        OffsetDateTime snapshotAt,
        List<Participant> participants,
        List<ChecklistItem> checklistItems,
        List<Long> authoritativeSourceIds,
        List<FieldRecord> fieldRecords,
        List<IncompleteSttJob> incompleteSttJobs
) {

    public static final int SCHEMA_VERSION = 1;

    public ReportInputResponse {
        participants = List.copyOf(participants);
        checklistItems = List.copyOf(checklistItems);
        authoritativeSourceIds = List.copyOf(authoritativeSourceIds);
        fieldRecords = List.copyOf(fieldRecords);
        incompleteSttJobs = List.copyOf(incompleteSttJobs);
    }

    public record Participant(
            Long fieldParticipantId,
            Long memberId,
            FieldParticipantStatus status,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt
    ) {
    }

    public record ChecklistItem(
            Long checklistId,
            Long checklistItemId,
            Long memberId,
            boolean fallback,
            String category,
            String title,
            String subtitle,
            int displayOrder,
            boolean completed,
            OffsetDateTime completedAt
    ) {
    }

    public record FieldRecord(
            Long sourceId,
            Long sessionId,
            Long checklistItemId,
            Long authorId,
            FieldRecordSourceType sourceType,
            String textContent,
            SttStatus sttStatus,
            PhotoFile photoFile,
            OffsetDateTime deletedAt,
            OffsetDateTime recordedAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record PhotoFile(
            Long fileId,
            String contentType,
            Long sizeBytes,
            UploadStatus uploadStatus,
            OffsetDateTime expiresAt,
            OffsetDateTime deletedAt
    ) {
    }

    public record IncompleteSttJob(
            String sttId,
            Long memberId,
            Long checklistItemId,
            SttStatus status,
            boolean retryable,
            String failCode,
            OffsetDateTime requestedAt
    ) {
    }
}
