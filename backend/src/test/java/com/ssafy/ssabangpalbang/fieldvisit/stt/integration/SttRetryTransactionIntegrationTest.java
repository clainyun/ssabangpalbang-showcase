package com.ssafy.ssabangpalbang.fieldvisit.stt.integration;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJobAttempt;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobAttemptRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttDispatchOutboxRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository.AudioFileView;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttRetryCommand;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttRetryTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class SttRetryTransactionIntegrationTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long STUDY_ID = 10L;
    private static final Long AUDIO_FILE_ID = 90L;
    private static final String STT_ID = "stt-concurrent-retry";
    private static final OffsetDateTime INITIAL_REQUESTED_AT =
            OffsetDateTime.of(
                    2026,
                    7,
                    25,
                    14,
                    25,
                    0,
                    0,
                    ZoneOffset.ofHours(9)
            );

    @Autowired
    private SttRetryTransaction retryTransaction;

    @Autowired
    private SttJobRepository sttJobRepository;

    @Autowired
    private SttJobAttemptRepository sttJobAttemptRepository;

    @MockitoBean
    private SttRequestQueryRepository requestQueryRepository;

    @Autowired
    private SttDispatchOutboxRepository dispatchOutboxRepository;

    private SttJob job;

    @BeforeEach
    void setUp() {
        job = sttJobRepository.saveAndFlush(SttJob.create(
                STT_ID,
                MEMBER_ID,
                STUDY_ID,
                100L,
                AUDIO_FILE_ID,
                503L,
                UUID.randomUUID(),
                INITIAL_REQUESTED_AT
        ));
        job.fail(
                "AI_TEMPORARY_ERROR",
                "일시적인 처리 실패",
                true,
                INITIAL_REQUESTED_AT.plusMinutes(1)
        );
        sttJobRepository.saveAndFlush(job);

        SttJobAttempt initialAttempt = SttJobAttempt.initial(
                job.getId(),
                MEMBER_ID,
                UUID.randomUUID(),
                INITIAL_REQUESTED_AT
        );
        initialAttempt.fail(
                "AI_TEMPORARY_ERROR",
                "일시적인 처리 실패",
                INITIAL_REQUESTED_AT.plusMinutes(1)
        );
        sttJobAttemptRepository.saveAndFlush(initialAttempt);

        when(requestQueryRepository.findAudioFileForUpdate(AUDIO_FILE_ID))
                .thenReturn(java.util.Optional.of(new AudioFileView(
                        AUDIO_FILE_ID,
                        MEMBER_ID,
                        STUDY_ID,
                        "STT_AUDIO",
                        "COMPLETED",
                        null,
                        INITIAL_REQUESTED_AT.plusDays(1),
                        "stt/audio.webm",
                        "audio/webm"
                )));
    }

    @AfterEach
    void cleanUp() {
        dispatchOutboxRepository.deleteAll();
        sttJobAttemptRepository.deleteAll();
        sttJobRepository.deleteAll();
    }

    @Test
    void 두_동시_요청은_attempt와_retryCount를_한_번만_증가시킨다()
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<SttRetryTransaction.TransactionResult> first =
                    executor.submit(() -> retryWhenReleased(
                            UUID.randomUUID(),
                            ready,
                            start
                    ));
            Future<SttRetryTransaction.TransactionResult> second =
                    executor.submit(() -> retryWhenReleased(
                            UUID.randomUUID(),
                            ready,
                            start
                    ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<SttRetryTransaction.TransactionResult> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );

            assertThat(results)
                    .extracting(SttRetryTransaction.TransactionResult::accepted)
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        SttJob reloaded = sttJobRepository.findBySttId(STT_ID).orElseThrow();
        assertThat(reloaded.getRetryCount()).isEqualTo(1);
        assertThat(sttJobAttemptRepository.count()).isEqualTo(2);
        assertThat(sttJobAttemptRepository
                .findTopBySttJobIdOrderByAttemptNoDesc(job.getId()))
                .get()
                .extracting(SttJobAttempt::getAttemptNo)
                .isEqualTo(2);
        assertThat(dispatchOutboxRepository.count()).isEqualTo(1);
    }

    @Test
    void 동일_clientRequestId_재전송은_같은_attempt와_시각을_반환한다() {
        UUID clientRequestId = UUID.randomUUID();
        OffsetDateTime retryRequestedAt = INITIAL_REQUESTED_AT.plusMinutes(5);
        SttRetryCommand command = command(
                clientRequestId,
                retryRequestedAt
        );

        SttRetryTransaction.TransactionResult first =
                retryTransaction.retry(command);
        SttRetryTransaction.TransactionResult second =
                retryTransaction.retry(command);

        assertThat(first.accepted()).isTrue();
        assertThat(second.accepted()).isFalse();
        assertThat(second.retryRequestedAt())
                .isEqualTo(first.retryRequestedAt());
        assertThat(sttJobAttemptRepository.count()).isEqualTo(2);
        assertThat(sttJobRepository.findBySttId(STT_ID).orElseThrow()
                .getRetryCount()).isEqualTo(1);
        assertThat(dispatchOutboxRepository.count()).isEqualTo(1);
    }

    private SttRetryTransaction.TransactionResult retryWhenReleased(
            UUID clientRequestId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 실행 시작 대기 시간 초과");
        }
        return retryTransaction.retry(command(
                clientRequestId,
                INITIAL_REQUESTED_AT.plusMinutes(5)
        ));
    }

    private SttRetryCommand command(
            UUID clientRequestId,
            OffsetDateTime retryRequestedAt
    ) {
        return new SttRetryCommand(
                MEMBER_ID,
                STUDY_ID,
                STT_ID,
                clientRequestId,
                retryRequestedAt
        );
    }
}
