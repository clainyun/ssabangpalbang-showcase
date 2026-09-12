package com.ssafy.ssabangpalbang.fieldvisit.stt.domain;

import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchCommand;
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
        name = "stt_dispatch_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stt_dispatch_outbox_attempt",
                columnNames = {"stt_id", "attempt_no"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SttDispatchOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stt_job_id", nullable = false)
    private Long sttJobId;

    @Column(name = "stt_id", nullable = false, length = 50)
    private String sttId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "audio_file_id", nullable = false)
    private Long audioFileId;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false, length = 20)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SttDispatchOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static SttDispatchOutbox pending(
            Long sttJobId,
            SttDispatchCommand command,
            OffsetDateTime createdAt
    ) {
        Objects.requireNonNull(command);
        SttDispatchOutbox outbox = new SttDispatchOutbox();
        outbox.sttJobId = Objects.requireNonNull(sttJobId);
        outbox.sttId = Objects.requireNonNull(command.sttId());
        outbox.attemptNo = command.attemptNo();
        outbox.audioFileId = Objects.requireNonNull(command.audioFileId());
        outbox.objectKey = Objects.requireNonNull(command.objectKey());
        outbox.contentType = Objects.requireNonNull(command.contentType());
        outbox.language = Objects.requireNonNull(command.language());
        outbox.status = SttDispatchOutboxStatus.PENDING;
        outbox.attemptCount = 0;
        outbox.nextAttemptAt = Objects.requireNonNull(createdAt);
        outbox.createdAt = createdAt;
        outbox.updatedAt = createdAt;
        return outbox;
    }

    public SttDispatchCommand toCommand() {
        return new SttDispatchCommand(
                sttId,
                attemptNo,
                audioFileId,
                objectKey,
                contentType,
                language
        );
    }

    public boolean isReady(OffsetDateTime now) {
        return status == SttDispatchOutboxStatus.PENDING
                && !nextAttemptAt.isAfter(now);
    }

    public void markPublished(OffsetDateTime now) {
        status = SttDispatchOutboxStatus.PUBLISHED;
        attemptCount++;
        publishedAt = Objects.requireNonNull(now);
        lastError = null;
        updatedAt = now;
    }

    public void markObsolete(OffsetDateTime now) {
        status = SttDispatchOutboxStatus.FAILED;
        lastError = "Dispatch attempt is no longer current";
        updatedAt = Objects.requireNonNull(now);
    }

    public boolean recordFailure(
            Throwable failure,
            int maxAttempts,
            Duration retryDelay,
            OffsetDateTime now
    ) {
        attemptCount++;
        lastError = safeMessage(failure);
        updatedAt = Objects.requireNonNull(now);
        if (attemptCount >= maxAttempts) {
            status = SttDispatchOutboxStatus.FAILED;
            return true;
        }
        nextAttemptAt = now.plus(Objects.requireNonNull(retryDelay));
        return false;
    }

    private static String safeMessage(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure == null
                    ? "Unknown dispatch failure"
                    : failure.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(message.length(), 500));
    }
}
