package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttAudioCleanupJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttAudioCleanupStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttAudioCleanupJobRepository;
import com.ssafy.ssabangpalbang.media.gateway.MediaGatewayException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SttAudioCleanupProcessorTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(
            2026, 7, 31, 0, 0, 0, 0, ZoneOffset.UTC
    );

    private final SttAudioCleanupJobRepository cleanupRepository = mock();
    private final SttAudioDeletionPort deletionPort = mock();

    @Test
    void already_deleted_audio_is_idempotent_success() {
        SttAudioCleanupJob cleanup = cleanup();
        when(cleanupRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(cleanup));
        doThrow(new MediaGatewayException(
                MediaGatewayException.Failure.NOT_FOUND
        )).when(deletionPort).deleteAudio(90L, "stt/audio.webm");

        processor(3).process(1L);

        assertThat(cleanup.getStatus())
                .isEqualTo(SttAudioCleanupStatus.COMPLETED);
        assertThat(cleanup.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void cleanup_failure_retries_and_eventually_stops() {
        SttAudioCleanupJob retrying = cleanup();
        when(cleanupRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(retrying));
        doThrow(new MediaGatewayException(
                MediaGatewayException.Failure.UNAVAILABLE
        )).when(deletionPort).deleteAudio(90L, "stt/audio.webm");

        processor(2).process(1L);

        assertThat(retrying.getStatus())
                .isEqualTo(SttAudioCleanupStatus.PENDING);
        assertThat(retrying.getNextAttemptAt())
                .isEqualTo(NOW.plusSeconds(30));

        SttAudioCleanupJob exhausted = cleanup();
        when(cleanupRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(exhausted));
        processor(1).process(2L);
        assertThat(exhausted.getStatus())
                .isEqualTo(SttAudioCleanupStatus.FAILED);
        assertThat(exhausted.getLastError()).contains("UNAVAILABLE");
    }

    private SttAudioCleanupProcessor processor(int maxAttempts) {
        return new SttAudioCleanupProcessor(
                cleanupRepository,
                deletionPort,
                SttDispatchOutboxProcessorTest.properties(3, maxAttempts),
                Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)
        );
    }

    private SttAudioCleanupJob cleanup() {
        return SttAudioCleanupJob.pending(
                90L,
                "stt/audio.webm",
                NOW.minusSeconds(1)
        );
    }
}
