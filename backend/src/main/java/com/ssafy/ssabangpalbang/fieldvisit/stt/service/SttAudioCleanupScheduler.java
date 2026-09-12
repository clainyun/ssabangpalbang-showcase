package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttAudioCleanupStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttAudioCleanupJobRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "${ssabangpalbang.fieldvisit.stt.reliability.enabled:false} "
                + "and ${ssabangpalbang.media.gateway.enabled:false}"
)
public class SttAudioCleanupScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(SttAudioCleanupScheduler.class);

    private final SttAudioCleanupJobRepository cleanupJobRepository;
    private final SttAudioCleanupProcessor processor;
    private final SttReliabilityProperties properties;
    private final Clock clock;

    @Scheduled(
            fixedDelayString =
                    "${ssabangpalbang.fieldvisit.stt.reliability.cleanup-poll-interval:10s}"
    )
    public void deleteReadyAudio() {
        cleanupJobRepository.findReadyIds(
                        SttAudioCleanupStatus.PENDING,
                        OffsetDateTime.now(clock),
                        PageRequest.of(0, properties.batchSize())
                )
                .forEach(this::processSafely);
    }

    private void processSafely(Long cleanupJobId) {
        try {
            processor.process(cleanupJobId);
        } catch (RuntimeException failure) {
            log.error(
                    "Unexpected STT audio cleanup failure. cleanupJobId={}",
                    cleanupJobId,
                    failure
            );
        }
    }
}
