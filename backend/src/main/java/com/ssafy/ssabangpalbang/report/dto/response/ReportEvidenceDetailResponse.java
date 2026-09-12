package com.ssafy.ssabangpalbang.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "완료 임장의 TEXT·DONE STT·PHOTO 원문")
public record ReportEvidenceDetailResponse(
        Long reportId,
        Long sourceId,
        String sourceType,
        String category,
        ReportEvidenceListResponse.ChecklistItemSummary checklistItem,
        String participantLabel,
        String textContent,
        Media media,
        String sttStatus,
        List<ReportEvidenceListResponse.UsedIn> usedIn,
        OffsetDateTime recordedAt
) {

    @Schema(
            name = "ReportEvidenceDetailMedia",
            description = "PHOTO 파일 접근 정보. TEXT·STT에서는 null입니다."
    )
    public record Media(
            boolean available,
            Long fileId,
            String originalName,
            String contentType,
            Long sizeBytes,
            String accessUrl,
            OffsetDateTime expiresAt
    ) {
    }
}
