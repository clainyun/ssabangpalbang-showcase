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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "stt_audio_cleanup_job",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stt_audio_cleanup_file",
                columnNames = "audio_file_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SttAudioCleanupJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audio_file_id", nullable = false)
    private Long audioFileId;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SttAudioCleanupStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static SttAudioCleanupJob pending(
            Long audioFileId,
            String objectKey,
            OffsetDateTime createdAt
    ) {
        SttAudioCleanupJob job = new SttAudioCleanupJob();
        job.audioFileId = Objects.requireNonNull(audioFileId);
        job.objectKey = Objects.requireNonNull(objectKey);
        job.status = SttAudioCleanupStatus.PENDING;
        job.attemptCount = 0;
        job.nextAttemptAt = Objects.requireNonNull(createdAt);
        job.createdAt = createdAt;
        job.updatedAt = createdAt;
        return job;
    }

    public boolean isReady(OffsetDateTime now) {
        return status == SttAudioCleanupStatus.PENDING
                && !nextAttemptAt.isAfter(now);
    }

    public void markCompleted(OffsetDateTime now) {
        status = SttAudioCleanupStatus.COMPLETED;
        attemptCount++;
        completedAt = Objects.requireNonNull(now);
        lastError = null;
        updatedAt = now;
    }

    public void recordFailure(
            Throwable failure,
            int maxAttempts,
            Duration retryDelay,
            OffsetDateTime now
    ) {
        attemptCount++;
        lastError = safeMessage(failure);
        updatedAt = Objects.requireNonNull(now);
        if (attemptCount >= maxAttempts) {
            status = SttAudioCleanupStatus.FAILED;
            return;
        }
        nextAttemptAt = now.plus(Objects.requireNonNull(retryDelay));
    }

    private static String safeMessage(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure == null
                    ? "Unknown cleanup failure"
                    : failure.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(message.length(), 500));
    }
}
