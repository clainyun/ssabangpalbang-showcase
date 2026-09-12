package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttAudioCleanupJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttAudioCleanupJobRepository;
import com.ssafy.ssabangpalbang.media.gateway.MediaGatewayException;
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
                + "and ${ssabangpalbang.media.gateway.enabled:false}"
)
public class SttAudioCleanupProcessor {

    private final SttAudioCleanupJobRepository cleanupJobRepository;
    private final SttAudioDeletionPort deletionPort;
    private final SttReliabilityProperties properties;
    private final Clock clock;

    @Transactional
    public void process(Long cleanupJobId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        SttAudioCleanupJob cleanup = cleanupJobRepository
                .findByIdForUpdate(cleanupJobId)
                .orElse(null);
        if (cleanup == null || !cleanup.isReady(now)) {
            return;
        }

        try {
            deletionPort.deleteAudio(
                    cleanup.getAudioFileId(),
                    cleanup.getObjectKey()
            );
            cleanup.markCompleted(now);
        } catch (MediaGatewayException failure) {
            if (failure.getFailure() == MediaGatewayException.Failure.NOT_FOUND) {
                cleanup.markCompleted(now);
                return;
            }
            recordFailure(cleanup, failure, now);
        } catch (RuntimeException failure) {
            recordFailure(cleanup, failure, now);
        }
    }

    private void recordFailure(
            SttAudioCleanupJob cleanup,
            RuntimeException failure,
            OffsetDateTime now
    ) {
        cleanup.recordFailure(
                failure,
                properties.cleanupMaxAttempts(),
                properties.cleanupRetryDelay(cleanup.getAttemptCount() + 1),
                now
        );
    }
}
