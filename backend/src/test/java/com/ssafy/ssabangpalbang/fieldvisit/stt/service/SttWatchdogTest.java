package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJobAttempt;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobAttemptRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SttWatchdogTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(
            2026, 7, 31, 0, 0, 0, 0, ZoneOffset.UTC
    );

    private final SttJobRepository jobRepository = mock();
    private final SttJobAttemptRepository attemptRepository = mock();
    private final SttWatchdogProcessor watchdog = new SttWatchdogProcessor(
            jobRepository,
            attemptRepository,
            SttDispatchOutboxProcessorTest.properties(3, 3)
    );

    @Test
    void stale_pending_job_becomes_retryable_failure() {
        SttJob job = job();
        job.recordDispatched(NOW.minusMinutes(6));
        SttJobAttempt attempt = attempt();
        stub(job, attempt);

        watchdog.failOne(
                44L,
                SttStatus.PENDING,
                "STT_WORKER_UNAVAILABLE",
                "worker timeout",
                NOW
        );

        assertThat(job.getStatus()).isEqualTo(SttStatus.FAILED);
        assertThat(job.isRetryable()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(SttStatus.FAILED);
    }

    @Test
    void freshly_redispatched_pending_job_is_not_failed() {
        SttJob job = job();
        job.recordDispatched(NOW.minusMinutes(1));
        SttJobAttempt attempt = attempt();
        stub(job, attempt);

        watchdog.failOne(
                44L,
                SttStatus.PENDING,
                "STT_WORKER_UNAVAILABLE",
                "worker timeout",
                NOW
        );

        assertThat(job.getStatus()).isEqualTo(SttStatus.PENDING);
        assertThat(attempt.getStatus()).isEqualTo(SttStatus.PENDING);
    }

    @Test
    void processing_timeout_uses_backend_observation_not_ai_clock() {
        SttJob job = job();
        job.start(
                NOW.plusDays(1),
                NOW.minusMinutes(16)
        );
        SttJobAttempt attempt = attempt();
        attempt.start(NOW.plusDays(1));
        stub(job, attempt);

        watchdog.failOne(
                44L,
                SttStatus.PROCESSING,
                "STT_PROCESSING_TIMEOUT",
                "processing timeout",
                NOW
        );

        assertThat(job.getStatus()).isEqualTo(SttStatus.FAILED);
        assertThat(attempt.getStatus()).isEqualTo(SttStatus.FAILED);
    }

    private void stub(SttJob job, SttJobAttempt attempt) {
        when(jobRepository.findByIdForUpdate(44L))
                .thenReturn(Optional.of(job));
        when(attemptRepository.findTopBySttJobIdOrderByAttemptNoDesc(44L))
                .thenReturn(Optional.of(attempt));
    }

    private SttJob job() {
        SttJob job = SttJob.create(
                "stt-watchdog",
                7L,
                10L,
                100L,
                90L,
                503L,
                UUID.randomUUID(),
                NOW.minusMinutes(10)
        );
        ReflectionTestUtils.setField(job, "id", 44L);
        return job;
    }

    private SttJobAttempt attempt() {
        return SttJobAttempt.initial(
                44L,
                7L,
                UUID.randomUUID(),
                NOW.minusMinutes(10)
        );
    }
}
