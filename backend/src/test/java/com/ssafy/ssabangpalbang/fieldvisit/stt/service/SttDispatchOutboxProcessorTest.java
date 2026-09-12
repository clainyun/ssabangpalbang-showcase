package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttDispatchOutbox;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttDispatchOutboxStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJobAttempt;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchCommand;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttDispatchOutboxRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobAttemptRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SttDispatchOutboxProcessorTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(
            2026, 7, 31, 0, 0, 0, 0, ZoneOffset.UTC
    );

    private final SttDispatchOutboxRepository outboxRepository = mock();
    private final SttJobRepository sttJobRepository = mock();
    private final SttJobAttemptRepository attemptRepository = mock();
    private final SttDispatchPort dispatchPort = mock();

    @Test
    void acknowledged_message_marks_outbox_published() {
        SttDispatchOutbox outbox = outbox();
        SttJob job = stubCurrentAttempt();
        when(outboxRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(outbox));

        processor(3).process(1L);

        verify(dispatchPort).dispatch(outbox.toCommand());
        assertThat(outbox.getStatus())
                .isEqualTo(SttDispatchOutboxStatus.PUBLISHED);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getPublishedAt()).isEqualTo(NOW);
        assertThat(job.getLastDispatchedAt()).isEqualTo(NOW);
    }

    @Test
    void transient_failure_is_retried_with_backoff() {
        SttDispatchOutbox outbox = outbox();
        stubCurrentAttempt();
        when(outboxRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(outbox));
        doThrow(new IllegalStateException("broker unavailable"))
                .when(dispatchPort)
                .dispatch(outbox.toCommand());

        processor(3).process(1L);

        assertThat(outbox.getStatus())
                .isEqualTo(SttDispatchOutboxStatus.PENDING);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt())
                .isEqualTo(NOW.plusSeconds(1));
        assertThat(outbox.getLastError()).isEqualTo("broker unavailable");
    }

    @Test
    void exhausted_dispatch_fails_current_attempt_as_retryable() {
        SttDispatchOutbox outbox = outbox();
        SttJob job = pendingJob();
        SttJobAttempt attempt = SttJobAttempt.initial(
                44L,
                7L,
                UUID.randomUUID(),
                NOW.minusMinutes(1)
        );
        when(outboxRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(outbox));
        when(sttJobRepository.findByIdForUpdate(44L))
                .thenReturn(Optional.of(job));
        when(attemptRepository.findTopBySttJobIdOrderByAttemptNoDesc(44L))
                .thenReturn(Optional.of(attempt));
        doThrow(new IllegalStateException("broker unavailable"))
                .when(dispatchPort)
                .dispatch(outbox.toCommand());

        processor(1).process(1L);

        assertThat(outbox.getStatus())
                .isEqualTo(SttDispatchOutboxStatus.FAILED);
        assertThat(job.getStatus()).isEqualTo(SttStatus.FAILED);
        assertThat(job.isRetryable()).isTrue();
        assertThat(job.getFailCode()).isEqualTo("KAFKA_DISPATCH_FAILED");
        assertThat(attempt.getStatus()).isEqualTo(SttStatus.FAILED);
    }

    @Test
    void obsolete_attempt_is_not_published() {
        SttDispatchOutbox outbox = outbox();
        SttJob job = pendingJob();
        SttJobAttempt newerAttempt = SttJobAttempt.retry(
                44L,
                7L,
                UUID.randomUUID(),
                2,
                NOW.minusSeconds(1)
        );
        when(outboxRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(outbox));
        when(sttJobRepository.findByIdForUpdate(44L))
                .thenReturn(Optional.of(job));
        when(attemptRepository.findTopBySttJobIdOrderByAttemptNoDesc(44L))
                .thenReturn(Optional.of(newerAttempt));

        processor(3).process(1L);

        assertThat(outbox.getStatus())
                .isEqualTo(SttDispatchOutboxStatus.FAILED);
        org.mockito.Mockito.verifyNoInteractions(dispatchPort);
    }

    private SttDispatchOutboxProcessor processor(int maxAttempts) {
        return new SttDispatchOutboxProcessor(
                outboxRepository,
                sttJobRepository,
                attemptRepository,
                dispatchPort,
                properties(maxAttempts, 3),
                Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)
        );
    }

    private SttDispatchOutbox outbox() {
        SttDispatchOutbox outbox = SttDispatchOutbox.pending(
                44L,
                new SttDispatchCommand(
                        "stt-outbox",
                        1,
                        90L,
                        "stt/audio.webm",
                        "audio/webm",
                        "ko-KR"
                ),
                NOW.minusSeconds(1)
        );
        ReflectionTestUtils.setField(outbox, "id", 1L);
        return outbox;
    }

    private SttJob pendingJob() {
        SttJob job = SttJob.create(
                "stt-outbox",
                7L,
                10L,
                100L,
                90L,
                503L,
                UUID.randomUUID(),
                NOW.minusMinutes(1)
        );
        ReflectionTestUtils.setField(job, "id", 44L);
        return job;
    }

    private SttJob stubCurrentAttempt() {
        SttJob job = pendingJob();
        SttJobAttempt attempt = SttJobAttempt.initial(
                44L,
                7L,
                UUID.randomUUID(),
                NOW.minusMinutes(1)
        );
        when(sttJobRepository.findByIdForUpdate(44L))
                .thenReturn(Optional.of(job));
        when(attemptRepository.findTopBySttJobIdOrderByAttemptNoDesc(44L))
                .thenReturn(Optional.of(attempt));
        return job;
    }

    static SttReliabilityProperties properties(
            int dispatchMaxAttempts,
            int cleanupMaxAttempts
    ) {
        return new SttReliabilityProperties(
                true,
                Duration.ofSeconds(1),
                50,
                dispatchMaxAttempts,
                Duration.ofSeconds(1),
                Duration.ofMinutes(1),
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                Duration.ofMinutes(15),
                Duration.ofSeconds(10),
                cleanupMaxAttempts,
                Duration.ofSeconds(30),
                Duration.ofHours(1)
        );
    }
}
