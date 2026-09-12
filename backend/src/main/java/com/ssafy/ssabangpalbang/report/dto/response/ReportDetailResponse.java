package com.ssafy.ssabangpalbang.report.dto.response;

import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public record ReportDetailResponse(
        Long reportId,
        ReportStatus status,
        String progressStage,
        String title,
        String summary,
        ReportApartmentSummary apartment,
        ReportStudySummary study,
        DetailMetrics metrics,
        List<DetailFeature> topPositiveFeatures,
        List<DetailFeature> topCautionFeatures,
        List<DetailCommonOpinion> commonOpinions,
        List<DetailConflictingOpinion> conflictingOpinions,
        List<DetailCategory> categories,
        ViewerPermissions viewer,
        boolean favoritedByMe,
        long favoriteCount,
        Long postId,
        OffsetDateTime completedAt,
        OffsetDateTime updatedAt
) {

    @Schema(name = "ReportDetailApartmentSummary")
    public record ReportApartmentSummary(
            Long apartmentId,
            String name,
            String address,
            Integer householdCount,
            String completionYearMonth,
            Integer parkingSpaceCount
    ) {
    }

    @Schema(name = "ReportDetailStudySummary")
    public record ReportStudySummary(
            Long studyId,
            String title,
            String goal,
            OffsetDateTime visitedAt,
            int participantCount
    ) {
    }

    @Schema(name = "ReportDetailMetrics")
    public record DetailMetrics(
            int totalChecklistItemCount,
            int completedChecklistItemCount,
            double averageCompletionRate,
            int fieldRecordCount,
            long evidenceCount,
            boolean hasAiEvidence
    ) {
    }

    @Schema(name = "ReportDetailFeature")
    public record DetailFeature(
            int rank,
            String label,
            String summary,
            int mentionCount,
            double mentionRate,
            List<Long> sourceIds
    ) {
    }

    @Schema(name = "ReportDetailCommonOpinion")
    public record DetailCommonOpinion(
            String category,
            String label,
            ReportGenerationResultRequest.OpinionType opinionType,
            String summary,
            int participantCount,
            double participantRate,
            List<Long> sourceIds
    ) {
    }

    @Schema(name = "ReportDetailConflictingOpinion")
    public record DetailConflictingOpinion(
            String category,
            String label,
            String summary,
            int positiveParticipantCount,
            double positiveParticipantRate,
            int cautionParticipantCount,
            double cautionParticipantRate,
            List<Long> sourceIds
    ) {
    }

    @Schema(name = "ReportDetailCategory")
    public record DetailCategory(
            String category,
            String summary,
            int checklistItemCount,
            int positiveOpinionCount,
            double positiveOpinionRate,
            int cautionOpinionCount,
            double cautionOpinionRate,
            int unrecordedOpinionCount,
            double unrecordedOpinionRate,
            boolean dataSufficient,
            List<DetailParticipantOpinion> participantOpinions
    ) {
    }

    @Schema(name = "ReportDetailParticipantOpinion")
    public record DetailParticipantOpinion(
            String participantLabel,
            ReportGenerationResultRequest.OpinionType opinionType,
            String summary,
            List<Long> sourceIds
    ) {
    }

    @Schema(name = "ReportDetailViewerPermissions")
    public record ViewerPermissions(
            boolean isParticipant,
            boolean canViewEvidenceList,
            boolean canViewEvidenceOriginal,
            boolean canFavorite
    ) {
    }
}
