package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJobAttempt;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobAttemptRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ssabangpalbang.fieldvisit.stt.reliability",
        name = "enabled",
        havingValue = "true"
)
public class SttWatchdogProcessor {

    private final SttJobRepository sttJobRepository;
    private final SttJobAttemptRepository sttJobAttemptRepository;
    private final SttReliabilityProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failOne(
            Long jobId,
            SttStatus expectedStatus,
            String code,
            String reason,
            OffsetDateTime failedAt
    ) {
        SttJob job = sttJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null
                || job.getStatus() != expectedStatus
                || !isStillStale(job, expectedStatus, failedAt)) {
            return;
        }
        SttJobAttempt attempt = sttJobAttemptRepository
                .findTopBySttJobIdOrderByAttemptNoDesc(jobId)
                .orElse(null);
        if (attempt == null || attempt.isTerminal()) {
            return;
        }
        attempt.fail(code, reason, failedAt);
        job.fail(code, reason, true, failedAt);
    }

    private boolean isStillStale(
            SttJob job,
            SttStatus expectedStatus,
            OffsetDateTime now
    ) {
        if (expectedStatus == SttStatus.PENDING) {
            return job.getLastDispatchedAt() != null
                    && !job.getLastDispatchedAt().isAfter(
                    now.minus(properties.pendingTimeout())
            );
        }
        if (expectedStatus == SttStatus.PROCESSING) {
            return job.getUpdatedAt() != null
                    && !job.getUpdatedAt().isAfter(
                    now.minus(properties.processingTimeout())
            );
        }
        return false;
    }
}
