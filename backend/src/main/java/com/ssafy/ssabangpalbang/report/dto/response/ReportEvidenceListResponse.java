package com.ssafy.ssabangpalbang.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "AI 리포트에 직접 연결된 TEXT·STT 근거 목록")
public record ReportEvidenceListResponse(
        Long reportId,
        AppliedFilters filters,
        List<EvidenceItem> content,
        EvidenceSummary summary,
        Long nextCursor,
        boolean hasNext
) {

    @Schema(name = "ReportEvidenceAppliedFilters")
    public record AppliedFilters(
            String sourceType,
            String category,
            List<Long> sourceIds
    ) {
    }

    @Schema(name = "ReportEvidenceItem")
    public record EvidenceItem(
            Long sourceId,
            String sourceType,
            String category,
            ChecklistItemSummary checklistItem,
            String participantLabel,
            String preview,
            Media media,
            boolean mediaAvailable,
            boolean originalAvailable,
            List<UsedIn> usedIn,
            OffsetDateTime recordedAt
    ) {
    }

    @Schema(name = "ReportEvidenceChecklistItemSummary")
    public record ChecklistItemSummary(
            Long checklistItemId,
            String title,
            String subtitle
    ) {
    }

    @Schema(name = "ReportEvidenceUsedIn")
    public record UsedIn(
            String claimKey,
            String resultSection,
            String resultKey
    ) {
    }

    @Schema(name = "ReportEvidenceMedia")
    public record Media(
            Long fileId,
            String contentType,
            String thumbnailUrl,
            OffsetDateTime expiresAt
    ) {
    }

    @Schema(name = "ReportEvidenceSummary")
    public record EvidenceSummary(
            long totalEvidenceCount,
            long textCount,
            long photoCount,
            long sttCount
    ) {
    }
}
