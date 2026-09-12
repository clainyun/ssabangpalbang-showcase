package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttDispatchOutbox;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJobAttempt;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttDispatchOutboxRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobAttemptRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression(
        "${ssabangpalbang.fieldvisit.stt.reliability.enabled:false} "
                + "and ${ssabangpalbang.fieldvisit.stt.kafka.enabled:false}"
)
public class SttDispatchOutboxProcessor {

    private static final String FAILURE_CODE = "KAFKA_DISPATCH_FAILED";
    private static final String FAILURE_REASON =
            "STT request could not be delivered after retries";

    private final SttDispatchOutboxRepository outboxRepository;
    private final SttJobRepository sttJobRepository;
    private final SttJobAttemptRepository sttJobAttemptRepository;
    private final SttDispatchPort dispatchPort;
    private final SttReliabilityProperties properties;
    private final Clock clock;

    @Transactional
    public void process(Long outboxId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        SttDispatchOutbox outbox = outboxRepository
                .findByIdForUpdate(outboxId)
                .orElse(null);
        if (outbox == null || !outbox.isReady(now)) {
            return;
        }

        SttJob job = sttJobRepository
                .findByIdForUpdate(outbox.getSttJobId())
                .orElse(null);
        SttJobAttempt attempt = job == null
                ? null
                : sttJobAttemptRepository
                .findTopBySttJobIdOrderByAttemptNoDesc(job.getId())
                .orElse(null);
        if (job == null
                || job.getStatus() != SttStatus.PENDING
                || attempt == null
                || attempt.getAttemptNo() != outbox.getAttemptNo()
                || attempt.isTerminal()) {
            outbox.markObsolete(now);
            return;
        }

        try {
            dispatchPort.dispatch(outbox.toCommand());
        } catch (RuntimeException failure) {
            if (Thread.currentThread().isInterrupted()) {
                throw failure;
            }
            boolean exhausted = outbox.recordFailure(
                    failure,
                    properties.dispatchMaxAttempts(),
                    properties.dispatchRetryDelay(outbox.getAttemptCount() + 1),
                    now
            );
            if (exhausted) {
                failCurrentAttempt(job, attempt, now);
            }
            return;
        }
        job.recordDispatched(now);
        outbox.markPublished(now);
    }

    private void failCurrentAttempt(
            SttJob job,
            SttJobAttempt attempt,
            OffsetDateTime failedAt
    ) {
        if (job.getStatus() != SttStatus.PENDING || attempt.isTerminal()) {
            return;
        }
        attempt.fail(FAILURE_CODE, FAILURE_REASON, failedAt);
        job.fail(FAILURE_CODE, FAILURE_REASON, true, failedAt);
    }
}
