package com.ssafy.ssabangpalbang.report.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "AI-005 ReportGenerationResult v1")
public record ReportGenerationResultRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 2000) String summary,
        @NotNull @Valid Metrics metrics,
        @NotNull @Size(max = 3)
        List<@Valid Feature> topPositiveFeatures,
        @NotNull @Size(max = 3)
        List<@Valid Feature> topCautionFeatures,
        @NotNull List<@Valid CommonOpinion> commonOpinions,
        @NotNull List<@Valid ConflictingOpinion> conflictingOpinions,
        @NotNull List<@Valid Category> categories
) {

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        rejectUnknown();
    }

    public enum OpinionType {
        POSITIVE,
        CAUTION
    }

    @Schema(description = "리포트 원본 집계")
    public record Metrics(
            @NotNull @Min(0) Integer totalChecklistItemCount,
            @NotNull @Min(0) Integer completedChecklistItemCount,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0")
            Double averageCompletionRate,
            @NotNull @Min(0) Integer fieldRecordCount
    ) {

        @JsonAnySetter
        public void rejectUnknownProperty(String name, Object value) {
            rejectUnknown();
        }
    }

    @Schema(description = "상위 긍정·주의 특징")
    public record Feature(
            @NotNull @Positive Integer rank,
            @NotBlank @Size(max = 80) String label,
            @NotBlank @Size(max = 500) String summary,
            @NotNull @Positive Integer mentionCount,
            @NotNull @Size(min = 1)
            List<@Pattern(regexp = "^P[1-9][0-9]*$") String>
                    participantRefs
    ) {

        @JsonAnySetter
        public void rejectUnknownProperty(String name, Object value) {
            rejectUnknown();
        }
    }

    @Schema(description = "다수 참여자의 공통 의견")
    public record CommonOpinion(
            @NotBlank @Size(max = 30) String category,
            @NotBlank @Size(max = 80) String label,
            @NotNull OpinionType opinionType,
            @NotBlank @Size(max = 500) String summary,
            @NotNull @Min(2) Integer participantCount,
            @NotNull @Size(min = 2)
            List<@Pattern(regexp = "^P[1-9][0-9]*$") String>
                    participantRefs
    ) {

        @JsonAnySetter
        public void rejectUnknownProperty(String name, Object value) {
            rejectUnknown();
        }
    }

    @Schema(description = "긍정·주의 의견이 공존하는 특징")
    public record ConflictingOpinion(
            @NotBlank @Size(max = 30) String category,
            @NotBlank @Size(max = 80) String label,
            @NotBlank @Size(max = 500) String summary,
            @NotNull @Positive Integer positiveParticipantCount,
            @NotNull @Positive Integer cautionParticipantCount,
            @NotNull @Size(min = 1)
            List<@Pattern(regexp = "^P[1-9][0-9]*$") String>
                    positiveParticipantRefs,
            @NotNull @Size(min = 1)
            List<@Pattern(regexp = "^P[1-9][0-9]*$") String>
                    cautionParticipantRefs
    ) {

        @JsonAnySetter
        public void rejectUnknownProperty(String name, Object value) {
            rejectUnknown();
        }
    }

    @Schema(description = "참여자별 카테고리 의견")
    public record ParticipantOpinion(
            @NotNull @Pattern(regexp = "^P[1-9][0-9]*$")
            String participantRef,
            @NotBlank @Size(max = 40) String participantLabel,
            @NotNull OpinionType opinionType,
            @NotBlank @Size(max = 500) String summary
    ) {

        @JsonAnySetter
        public void rejectUnknownProperty(String name, Object value) {
            rejectUnknown();
        }
    }

    @Schema(description = "카테고리별 의견 집계")
    public record Category(
            @NotBlank @Size(max = 30) String category,
            @NotBlank @Size(max = 500) String summary,
            @NotNull @Min(0) Integer positiveOpinionCount,
            @NotNull @Min(0) Integer cautionOpinionCount,
            @NotNull Boolean dataSufficient,
            @NotNull List<@Valid ParticipantOpinion> participantOpinions
    ) {

        @JsonAnySetter
        public void rejectUnknownProperty(String name, Object value) {
            rejectUnknown();
        }
    }

    private static void rejectUnknown() {
        throw new IllegalArgumentException("Unexpected report contract field");
    }
}
