package com.ssafy.ssabangpalbang.report.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false, unique = true)
    private Long studyId;

    @Column(name = "apartment_id", nullable = false)
    private Long apartmentId;

    @Column(name = "field_session_id", nullable = false)
    private Long fieldSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "progress_stage", length = 20)
    private String progressStage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private JsonNode resultJson;

    @Column(name = "fail_reason", length = 255)
    private String failReason;

    @Column(name = "fail_code", length = 100)
    private String failCode;

    @Column(name = "is_retryable", nullable = false)
    private boolean retryable;

    @Column(name = "processing_token_hash", length = 64)
    private String processingTokenHash;

    @Column(name = "processing_attempt", nullable = false)
    private int processingAttempt;

    @Column(name = "processing_lease_expires_at")
    private Instant processingLeaseExpiresAt;

    @Column(name = "complete_payload_hash", length = 64)
    private String completePayloadHash;

    @Column(name = "fail_payload_hash", length = 64)
    private String failPayloadHash;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "retry_requested_at")
    private Instant retryRequestedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Report create(
            Long studyId,
            Long fieldSessionId,
            Long apartmentId
    ) {
        Report report = new Report();
        report.studyId = Objects.requireNonNull(studyId);
        report.fieldSessionId = Objects.requireNonNull(fieldSessionId);
        report.apartmentId = Objects.requireNonNull(apartmentId);
        report.status = ReportStatus.PENDING;
        report.processingAttempt = 0;
        return report;
    }

    public void acquire(
            String processingTokenHash,
            Instant leaseExpiresAt
    ) {
        this.status = ReportStatus.IN_PROGRESS;
        this.progressStage = ReportProgressStage.RECORD_COLLECTION.name();
        this.processingTokenHash = Objects.requireNonNull(processingTokenHash);
        this.processingAttempt = Math.addExact(this.processingAttempt, 1);
        this.processingLeaseExpiresAt = Objects.requireNonNull(leaseExpiresAt);
    }

    public boolean hasActiveLease(Instant now) {
        return processingLeaseExpiresAt != null
                && processingLeaseExpiresAt.isAfter(now);
    }

    public void updateProgress(
            ReportProgressStage stage,
            Instant leaseExpiresAt
    ) {
        ReportProgressStage safeStage = Objects.requireNonNull(stage);
        if (!canUpdateProgressTo(safeStage)) {
            throw new IllegalArgumentException(
                    "Invalid report progress stage transition"
            );
        }
        this.progressStage = safeStage.name();
        this.processingLeaseExpiresAt = Objects.requireNonNull(
                leaseExpiresAt
        );
    }

    public boolean canUpdateProgressTo(ReportProgressStage targetStage) {
        if (targetStage == null
                || targetStage == ReportProgressStage.COMPLETED
                || progressStage == null) {
            return false;
        }

        ReportProgressStage currentStage;
        try {
            currentStage = ReportProgressStage.valueOf(progressStage);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        if (currentStage == targetStage) {
            return true;
        }

        return switch (currentStage) {
            case RECORD_COLLECTION ->
                    targetStage == ReportProgressStage.STT_VALIDATION;
            case STT_VALIDATION ->
                    targetStage == ReportProgressStage.NORMALIZATION;
            case NORMALIZATION ->
                    targetStage == ReportProgressStage.REPORT_GENERATION;
            case REPORT_GENERATION ->
                    targetStage == ReportProgressStage.EVIDENCE_MAPPING
                            || targetStage == ReportProgressStage.RESULT_SAVING;
            case EVIDENCE_MAPPING ->
                    targetStage == ReportProgressStage.RESULT_SAVING;
            case RESULT_SAVING, COMPLETED -> false;
        };
    }

    public void complete(
            JsonNode generationResult,
            String payloadHash,
            Instant completedAt
    ) {
        this.resultJson = Objects.requireNonNull(generationResult);
        this.completePayloadHash = Objects.requireNonNull(payloadHash);
        this.status = ReportStatus.DONE;
        this.progressStage = ReportProgressStage.COMPLETED.name();
        this.failReason = null;
        this.failCode = null;
        this.retryable = false;
        this.failPayloadHash = null;
        this.processingLeaseExpiresAt = null;
        this.failedAt = null;
        this.completedAt = Objects.requireNonNull(completedAt);
    }

    public void fail(
            ReportProgressStage failedStage,
            String failCode,
            String failReason,
            boolean retryable,
            String payloadHash,
            Instant failedAt
    ) {
        ReportProgressStage safeStage = Objects.requireNonNull(failedStage);
        if (safeStage == ReportProgressStage.COMPLETED) {
            throw new IllegalArgumentException(
                    "COMPLETED cannot be a report failure stage"
            );
        }
        this.status = ReportStatus.FAILED;
        this.progressStage = safeStage.name();
        this.failCode = Objects.requireNonNull(failCode);
        this.failReason = Objects.requireNonNull(failReason);
        this.retryable = retryable;
        this.failPayloadHash = Objects.requireNonNull(payloadHash);
        this.processingLeaseExpiresAt = null;
        this.failedAt = Objects.requireNonNull(failedAt);
        this.completedAt = null;
    }

    public void prepareRetry(Instant requestedAt) {
        this.status = ReportStatus.PENDING;
        this.progressStage = ReportProgressStage.RECORD_COLLECTION.name();
        this.resultJson = null;
        this.failReason = null;
        this.failCode = null;
        this.retryable = false;
        this.processingTokenHash = null;
        this.processingLeaseExpiresAt = null;
        this.completePayloadHash = null;
        this.failPayloadHash = null;
        this.completedAt = null;
        this.failedAt = null;
        this.retryRequestedAt = Objects.requireNonNull(requestedAt);
    }
}
