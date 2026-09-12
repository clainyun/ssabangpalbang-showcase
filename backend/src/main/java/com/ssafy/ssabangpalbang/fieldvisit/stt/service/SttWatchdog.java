package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ssabangpalbang.fieldvisit.stt.reliability",
        name = "enabled",
        havingValue = "true"
)
public class SttWatchdog {

    private static final Logger log = LoggerFactory.getLogger(SttWatchdog.class);
    private static final String PENDING_CODE = "STT_WORKER_UNAVAILABLE";
    private static final String PROCESSING_CODE = "STT_PROCESSING_TIMEOUT";

    private final SttJobRepository sttJobRepository;
    private final SttWatchdogProcessor processor;
    private final SttReliabilityProperties properties;
    private final Clock clock;

    @Scheduled(
            fixedDelayString =
                    "${ssabangpalbang.fieldvisit.stt.reliability.watchdog-interval:30s}"
    )
    public void failStaleJobs() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        PageRequest page = PageRequest.of(0, properties.batchSize());
        List<Long> pendingIds = sttJobRepository.findStalePendingIds(
                now.minus(properties.pendingTimeout()),
                page
        );
        List<Long> processingIds = sttJobRepository.findStaleProcessingIds(
                now.minus(properties.processingTimeout()),
                page
        );
        pendingIds.forEach(id -> failSafely(
                id,
                SttStatus.PENDING,
                PENDING_CODE,
                "STT worker did not start the dispatched request in time",
                now
        ));
        processingIds.forEach(id -> failSafely(
                id,
                SttStatus.PROCESSING,
                PROCESSING_CODE,
                "STT processing exceeded the configured timeout",
                now
        ));
    }

    private void failSafely(
            Long jobId,
            SttStatus expectedStatus,
            String code,
            String reason,
            OffsetDateTime failedAt
    ) {
        try {
            processor.failOne(
                    jobId,
                    expectedStatus,
                    code,
                    reason,
                    failedAt
            );
        } catch (RuntimeException failure) {
            log.error("STT watchdog failed. jobId={}", jobId, failure);
        }
    }
}
