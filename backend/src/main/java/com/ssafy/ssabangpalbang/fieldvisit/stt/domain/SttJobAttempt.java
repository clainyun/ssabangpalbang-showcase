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

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "stt_job_attempt",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stt_job_attempt_request",
                        columnNames = {"member_id", "client_request_id"}
                ),
                @UniqueConstraint(
                        name = "uk_stt_job_attempt_number",
                        columnNames = {"stt_job_id", "attempt_no"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SttJobAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stt_job_id", nullable = false)
    private Long sttJobId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "client_request_id", nullable = false)
    private UUID clientRequestId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 10)
    private SttRequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SttStatus status;

    @Column(name = "fail_code", length = 100)
    private String failCode;

    @Column(name = "fail_reason", length = 500)
    private String failReason;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    public static SttJobAttempt initial(
            Long sttJobId,
            Long memberId,
            UUID clientRequestId,
            OffsetDateTime requestedAt
    ) {
        SttJobAttempt attempt = new SttJobAttempt();
        attempt.sttJobId = Objects.requireNonNull(sttJobId);
        attempt.memberId = Objects.requireNonNull(memberId);
        attempt.clientRequestId = Objects.requireNonNull(clientRequestId);
        attempt.attemptNo = 1;
        attempt.requestType = SttRequestType.INITIAL;
        attempt.status = SttStatus.PENDING;
        attempt.requestedAt = Objects.requireNonNull(requestedAt);
        return attempt;
    }

    public static SttJobAttempt retry(
            Long sttJobId,
            Long memberId,
            UUID clientRequestId,
            int attemptNo,
            OffsetDateTime requestedAt
    ) {
        if (attemptNo < 2) {
            throw new IllegalArgumentException(
                    "재처리 attempt 번호는 2 이상이어야 합니다."
            );
        }

        SttJobAttempt attempt = new SttJobAttempt();
        attempt.sttJobId = Objects.requireNonNull(sttJobId);
        attempt.memberId = Objects.requireNonNull(memberId);
        attempt.clientRequestId = Objects.requireNonNull(clientRequestId);
        attempt.attemptNo = attemptNo;
        attempt.requestType = SttRequestType.RETRY;
        attempt.status = SttStatus.PENDING;
        attempt.requestedAt = Objects.requireNonNull(requestedAt);
        return attempt;
    }

    public boolean isTerminal() {
        return status == SttStatus.DONE || status == SttStatus.FAILED;
    }

    public void start(OffsetDateTime startedAt) {
        if (status != SttStatus.PENDING) {
            throw new IllegalStateException(
                    "처리를 시작할 수 없는 STT attempt입니다. attemptNo="
                            + attemptNo
            );
        }

        status = SttStatus.PROCESSING;
        this.startedAt = Objects.requireNonNull(startedAt);
    }

    public void complete(OffsetDateTime finishedAt) {
        status = SttStatus.DONE;
        failCode = null;
        failReason = null;
        this.finishedAt = Objects.requireNonNull(finishedAt);
    }

    public void fail(
            String failCode,
            String failReason,
            OffsetDateTime finishedAt
    ) {
        status = SttStatus.FAILED;
        this.failCode = failCode;
        this.failReason = failReason;
        this.finishedAt = Objects.requireNonNull(finishedAt);
    }
}
