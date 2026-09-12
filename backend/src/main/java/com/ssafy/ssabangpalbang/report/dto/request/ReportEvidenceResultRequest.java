package com.ssafy.ssabangpalbang.report.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "AI-006 EvidenceLinkResult v1")
public record ReportEvidenceResultRequest(
        @NotNull List<@Valid Claim> claims
) {

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        rejectUnknown();
    }

    public enum ClaimType {
        FEATURE_POSITIVE,
        FEATURE_CAUTION,
        COMMON,
        CONFLICT,
        PARTICIPANT_OPINION
    }

    public enum EvidenceRole {
        SUPPORT,
        SUPPORT_POSITIVE,
        SUPPORT_CAUTION
    }

    public enum SourceType {
        TEXT,
        STT
    }

    @Schema(description = "AI 문장과 연결된 근거 묶음")
    public record Claim(
            @NotBlank @Size(max = 100) String claimKey,
            @NotNull ClaimType claimType,
            @Size(max = 30) String category,
            @Size(max = 80) String label,
            ReportGenerationResultRequest.OpinionType opinionType,
            @NotNull @Size(min = 1)
            List<@Pattern(regexp = "^P[1-9][0-9]*$") String>
                    participantRefs,
            @NotNull @Size(min = 1)
            List<@Valid Evidence> evidences,
            @NotNull @Positive Integer displayOrder
    ) {

        @JsonAnySetter
        public void rejectUnknownProperty(String name, Object value) {
            rejectUnknown();
        }
    }

    @Schema(description = "TEXT·STT 직접 근거")
    public record Evidence(
            @NotNull SourceType sourceType,
            @NotNull @Positive Long sourceId,
            @NotNull @Pattern(regexp = "^P[1-9][0-9]*$")
            String participantRef,
            @NotNull @Positive Long checklistItemId,
            @NotBlank @Size(max = 30) String category,
            @NotNull OffsetDateTime recordedAt,
            @NotNull EvidenceRole evidenceRole
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
