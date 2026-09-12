package com.ssafy.ssabangpalbang.fieldvisit.stt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "stt_job",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_stt_job_stt_id", columnNames = "stt_id"),
                @UniqueConstraint(
                        name = "uk_stt_job_audio_file",
                        columnNames = "audio_file_id"
                ),
                @UniqueConstraint(
                        name = "uk_stt_job_initial_request",
                        columnNames = {"member_id", "initial_client_request_id"}
                ),
                @UniqueConstraint(
                        name = "uk_stt_job_field_record",
                        columnNames = "field_record_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SttJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stt_id", nullable = false, length = 50)
    private String sttId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "study_id", nullable = false)
    private Long studyId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "audio_file_id", nullable = false)
    private Long audioFileId;

    @Column(name = "checklist_item_id", nullable = false)
    private Long checklistItemId;

    @Column(name = "field_record_id")
    private Long fieldRecordId;

    @Column(name = "initial_client_request_id", nullable = false)
    private UUID initialClientRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SttStatus status;

    @Column(name = "fail_code", length = 100)
    private String failCode;

    @Column(name = "fail_reason", length = 500)
    private String failReason;

    @Column(nullable = false)
    private boolean retryable;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "last_dispatched_at")
    private OffsetDateTime lastDispatchedAt;

    @Column(name = "dispatch_count", nullable = false)
    private int dispatchCount;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static SttJob create(
            String sttId,
            Long memberId,
            Long studyId,
            Long sessionId,
            Long audioFileId,
            Long checklistItemId,
            UUID initialClientRequestId,
            OffsetDateTime requestedAt
    ) {
        SttJob job = new SttJob();
        job.sttId = Objects.requireNonNull(sttId);
        job.memberId = Objects.requireNonNull(memberId);
        job.studyId = Objects.requireNonNull(studyId);
        job.sessionId = Objects.requireNonNull(sessionId);
        job.audioFileId = Objects.requireNonNull(audioFileId);
        job.checklistItemId = Objects.requireNonNull(checklistItemId);
        job.initialClientRequestId = Objects.requireNonNull(initialClientRequestId);
        job.status = SttStatus.PENDING;
        job.retryable = false;
        job.retryCount = 0;
        job.dispatchCount = 0;
        job.requestedAt = Objects.requireNonNull(requestedAt);
        job.createdAt = requestedAt;
        job.updatedAt = requestedAt;
        return job;
    }

    public boolean hasSameFingerprint(
            Long memberId,
            Long studyId,
            Long audioFileId,
            Long checklistItemId
    ) {
        return Objects.equals(this.memberId, memberId)
                && Objects.equals(this.studyId, studyId)
                && Objects.equals(this.audioFileId, audioFileId)
                && Objects.equals(this.checklistItemId, checklistItemId);
    }

    public boolean isInProgress() {
        return status == SttStatus.PENDING
                || status == SttStatus.PROCESSING;
    }

    public void prepareRetry(OffsetDateTime requestedAt) {
        if (status != SttStatus.FAILED || !retryable) {
            throw new IllegalStateException(
                    "재처리할 수 없는 STT 작업입니다. sttId=" + sttId
            );
        }

        status = SttStatus.PENDING;
        retryable = false;
        failCode = null;
        failReason = null;
        retryCount++;
        startedAt = null;
        completedAt = null;
        lastDispatchedAt = null;
        updatedAt = Objects.requireNonNull(requestedAt);
    }

    public void start(OffsetDateTime startedAt) {
        start(startedAt, startedAt);
    }

    public void start(
            OffsetDateTime startedAt,
            OffsetDateTime observedAt
    ) {
        if (status != SttStatus.PENDING) {
            throw new IllegalStateException(
                    "처리를 시작할 수 없는 STT 작업입니다. sttId=" + sttId
            );
        }

        status = SttStatus.PROCESSING;
        this.startedAt = Objects.requireNonNull(startedAt);
        updatedAt = Objects.requireNonNull(observedAt);
    }

    public void recordDispatched(OffsetDateTime dispatchedAt) {
        lastDispatchedAt = Objects.requireNonNull(dispatchedAt);
        dispatchCount++;
        if (updatedAt == null || dispatchedAt.isAfter(updatedAt)) {
            updatedAt = dispatchedAt;
        }
    }

    public void complete(
            Long fieldRecordId,
            OffsetDateTime completedAt
    ) {
        this.status = SttStatus.DONE;
        this.fieldRecordId = Objects.requireNonNull(fieldRecordId);
        this.retryable = false;
        this.failCode = null;
        this.failReason = null;
        this.completedAt = Objects.requireNonNull(completedAt);
        this.updatedAt = completedAt;
    }

    public void fail(
            String failCode,
            String failReason,
            boolean retryable,
            OffsetDateTime failedAt
    ) {
        this.status = SttStatus.FAILED;
        this.failCode = failCode;
        this.failReason = failReason;
        this.retryable = retryable;
        this.completedAt = null;
        this.updatedAt = Objects.requireNonNull(failedAt);
    }
}
