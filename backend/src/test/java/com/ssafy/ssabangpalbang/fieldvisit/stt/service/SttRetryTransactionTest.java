package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJobAttempt;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttDispatchOutbox;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchCommand;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttDispatchOutboxRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobAttemptRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository.AudioFileView;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttRetryTransactionTest {

    private static final Long JOB_ID = 44L;
    private static final Long MEMBER_ID = 7L;
    private static final Long STUDY_ID = 10L;
    private static final Long AUDIO_FILE_ID = 90L;
    private static final String STT_ID = "stt-retry";
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
    private static final OffsetDateTime RETRY_REQUESTED_AT =
            INITIAL_REQUESTED_AT.plusMinutes(5);
    private static final UUID RETRY_REQUEST_ID =
            UUID.fromString("55591972-492e-4c29-81bd-eb203f37be49");

    @Mock
    private SttJobRepository sttJobRepository;

    @Mock
    private SttJobAttemptRepository sttJobAttemptRepository;

    @Mock
    private SttRequestQueryRepository requestQueryRepository;

    @Mock
    private SttDispatchOutboxRepository dispatchOutboxRepository;

    @InjectMocks
    private SttRetryTransaction retryTransaction;

    @Test
    void 실패한_작업에_RETRY_attempt를_추가하고_같은_sttId로_발행한다() {
        SttJob job = failedJob(true);
        job.recordDispatched(INITIAL_REQUESTED_AT.plusSeconds(30));
        SttJobAttempt initialAttempt = failedInitialAttempt();
        OffsetDateTime expiresAt = RETRY_REQUESTED_AT.plusDays(1);
        stubNewRequest(job, initialAttempt);
        when(requestQueryRepository.findAudioFileForUpdate(AUDIO_FILE_ID))
                .thenReturn(Optional.of(validAudio(expiresAt)));

        SttRetryTransaction.TransactionResult result =
                retryTransaction.retry(command());

        assertThat(result.accepted()).isTrue();
        assertThat(result.retryRequestedAt()).isEqualTo(RETRY_REQUESTED_AT);
        assertThat(job.getStatus()).isEqualTo(SttStatus.PENDING);
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(job.isRetryable()).isFalse();
        assertThat(job.getFailCode()).isNull();
        assertThat(job.getFailReason()).isNull();
        assertThat(job.getLastDispatchedAt()).isNull();
        assertThat(job.getSessionId()).isEqualTo(100L);
        assertThat(job.getChecklistItemId()).isEqualTo(503L);
        assertThat(job.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(expiresAt).isEqualTo(RETRY_REQUESTED_AT.plusDays(1));

        ArgumentCaptor<SttJobAttempt> attemptCaptor =
                ArgumentCaptor.forClass(SttJobAttempt.class);
        verify(sttJobAttemptRepository).saveAndFlush(
                attemptCaptor.capture()
        );
        assertThat(attemptCaptor.getValue().getAttemptNo()).isEqualTo(2);
        assertThat(attemptCaptor.getValue().getRequestType().name())
                .isEqualTo("RETRY");
        assertThat(attemptCaptor.getValue().getClientRequestId())
                .isEqualTo(RETRY_REQUEST_ID);

        ArgumentCaptor<SttDispatchOutbox> outboxCaptor =
                ArgumentCaptor.forClass(SttDispatchOutbox.class);
        verify(dispatchOutboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().toCommand()).isEqualTo(
                new SttDispatchCommand(
                        STT_ID,
                        2,
                        AUDIO_FILE_ID,
                        "stt/audio.webm",
                        "audio/webm",
                        "ko-KR"
                )
        );
    }

    @Test
    void 진행_중이면_새_attempt_없이_최신_요청_시각을_반환한다() {
        SttJob job = job();
        SttJobAttempt initialAttempt = SttJobAttempt.initial(
                JOB_ID,
                MEMBER_ID,
                UUID.randomUUID(),
                INITIAL_REQUESTED_AT
        );
        stubNewRequest(job, initialAttempt);

        SttRetryTransaction.TransactionResult result =
                retryTransaction.retry(command());

        assertThat(result.accepted()).isFalse();
        assertThat(result.retryRequestedAt())
                .isEqualTo(INITIAL_REQUESTED_AT);
        verify(sttJobAttemptRepository, never()).saveAndFlush(any());
        verifyNoInteractions(requestQueryRepository, dispatchOutboxRepository);
    }

    @Test
    void 동일_RETRY_clientRequestId는_기존_수락_시각을_유지한다() {
        SttJob job = job();
        SttJobAttempt retryAttempt = SttJobAttempt.retry(
                JOB_ID,
                MEMBER_ID,
                RETRY_REQUEST_ID,
                2,
                RETRY_REQUESTED_AT
        );
        when(sttJobAttemptRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID,
                RETRY_REQUEST_ID
        )).thenReturn(Optional.of(retryAttempt));
        when(sttJobRepository.findById(JOB_ID))
                .thenReturn(Optional.of(job));

        SttRetryTransaction.TransactionResult result =
                retryTransaction.retry(command());

        assertThat(result.accepted()).isFalse();
        assertThat(result.retryRequestedAt()).isEqualTo(RETRY_REQUESTED_AT);
        verify(sttJobRepository, never()).findBySttIdForUpdate(any());
        verifyNoInteractions(requestQueryRepository, dispatchOutboxRepository);
    }

    @Test
    void INITIAL_clientRequestId를_재처리에_재사용하면_400이다() {
        SttJob job = job();
        SttJobAttempt initialAttempt = SttJobAttempt.initial(
                JOB_ID,
                MEMBER_ID,
                RETRY_REQUEST_ID,
                INITIAL_REQUESTED_AT
        );
        when(sttJobAttemptRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID,
                RETRY_REQUEST_ID
        )).thenReturn(Optional.of(initialAttempt));
        when(sttJobRepository.findById(JOB_ID))
                .thenReturn(Optional.of(job));

        assertBusinessError(
                () -> retryTransaction.retry(command()),
                ErrorCode.INVALID_INPUT_VALUE
        );
    }

    @Test
    void 다른_job의_RETRY_clientRequestId를_재사용하면_400이다() {
        SttJob otherJob = failedJob(true);
        ReflectionTestUtils.setField(otherJob, "sttId", "stt-other");
        SttJobAttempt retryAttempt = SttJobAttempt.retry(
                JOB_ID,
                MEMBER_ID,
                RETRY_REQUEST_ID,
                2,
                RETRY_REQUESTED_AT
        );
        when(sttJobAttemptRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID,
                RETRY_REQUEST_ID
        )).thenReturn(Optional.of(retryAttempt));
        when(sttJobRepository.findById(JOB_ID))
                .thenReturn(Optional.of(otherJob));

        assertBusinessError(
                () -> retryTransaction.retry(command()),
                ErrorCode.INVALID_INPUT_VALUE
        );
    }

    @Test
    void DONE과_영구_실패는_409로_거부한다() {
        SttJob done = job();
        done.complete(830L, RETRY_REQUESTED_AT);
        ReflectionTestUtils.setField(done, "retryable", true);
        stubNewRequest(done, failedInitialAttempt());
        assertBusinessError(
                () -> retryTransaction.retry(command()),
                ErrorCode.FIELD_STT_RETRY_NOT_ALLOWED
        );

        SttJob permanentFailure = failedJob(false);
        stubNewRequest(permanentFailure, failedInitialAttempt());
        assertBusinessError(
                () -> retryTransaction.retry(command()),
                ErrorCode.FIELD_STT_RETRY_NOT_ALLOWED
        );
    }

    @Test
    void 없는_삭제된_만료된_원본은_410으로_거부한다() {
        SttJob missing = failedJob(true);
        stubNewRequest(missing, failedInitialAttempt());
        when(requestQueryRepository.findAudioFileForUpdate(AUDIO_FILE_ID))
                .thenReturn(Optional.empty());
        assertBusinessError(
                () -> retryTransaction.retry(command()),
                ErrorCode.FIELD_STT_AUDIO_EXPIRED
        );

        SttJob deleted = failedJob(true);
        stubNewRequest(deleted, failedInitialAttempt());
        when(requestQueryRepository.findAudioFileForUpdate(AUDIO_FILE_ID))
                .thenReturn(Optional.of(new AudioFileView(
                        AUDIO_FILE_ID,
                        MEMBER_ID,
                        STUDY_ID,
                        "STT_AUDIO",
                        "DELETED",
                        RETRY_REQUESTED_AT.minusSeconds(1),
                        RETRY_REQUESTED_AT.plusDays(1),
                        "stt/audio.webm",
                        "audio/webm"
                )));
        assertBusinessError(
                () -> retryTransaction.retry(command()),
                ErrorCode.FIELD_STT_AUDIO_EXPIRED
        );

        SttJob expired = failedJob(true);
        stubNewRequest(expired, failedInitialAttempt());
        when(requestQueryRepository.findAudioFileForUpdate(AUDIO_FILE_ID))
                .thenReturn(Optional.of(validAudio(
                        RETRY_REQUESTED_AT
                )));
        assertBusinessError(
                () -> retryTransaction.retry(command()),
                ErrorCode.FIELD_STT_AUDIO_EXPIRED
        );
    }

    @Test
    void 원본_용도나_업로드_상태가_다르면_409로_거부한다() {
        SttJob job = failedJob(true);
        stubNewRequest(job, failedInitialAttempt());
        when(requestQueryRepository.findAudioFileForUpdate(AUDIO_FILE_ID))
                .thenReturn(Optional.of(new AudioFileView(
                        AUDIO_FILE_ID,
                        MEMBER_ID,
                        STUDY_ID,
                        "FIELD_PHOTO",
                        "COMPLETED",
                        null,
                        RETRY_REQUESTED_AT.plusDays(1),
                        "stt/audio.webm",
                        "audio/webm"
                )));

        assertBusinessError(
                () -> retryTransaction.retry(command()),
                ErrorCode.FIELD_STT_RETRY_NOT_ALLOWED
        );
    }

    @Test
    void 경로_study와_작성자를_검증한다() {
        SttJob job = failedJob(true);
        stubJobLookup(job);
        assertBusinessError(
                () -> retryTransaction.retry(new SttRetryCommand(
                        MEMBER_ID,
                        STUDY_ID + 1,
                        STT_ID,
                        RETRY_REQUEST_ID,
                        RETRY_REQUESTED_AT
                )),
                ErrorCode.FIELD_STT_STUDY_MISMATCH
        );

        SttJob otherMemberJob = failedJob(true);
        ReflectionTestUtils.setField(
                otherMemberJob,
                "memberId",
                MEMBER_ID + 1
        );
        stubJobLookup(otherMemberJob);
        assertBusinessError(
                () -> retryTransaction.retry(command()),
                ErrorCode.FIELD_STT_RETRY_FORBIDDEN
        );
    }

    @Test
    void 없는_job은_404다() {
        when(sttJobAttemptRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID,
                RETRY_REQUEST_ID
        )).thenReturn(Optional.empty());
        when(sttJobRepository.findBySttIdForUpdate(STT_ID))
                .thenReturn(Optional.empty());

        assertBusinessError(
                () -> retryTransaction.retry(command()),
                ErrorCode.FIELD_STT_NOT_FOUND
        );
    }

    private void stubNewRequest(
            SttJob job,
            SttJobAttempt latestAttempt
    ) {
        stubJobLookup(job);
        when(sttJobAttemptRepository
                .findTopBySttJobIdOrderByAttemptNoDesc(JOB_ID))
                .thenReturn(Optional.of(latestAttempt));
    }

    private void stubJobLookup(SttJob job) {
        when(sttJobAttemptRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID,
                RETRY_REQUEST_ID
        )).thenReturn(Optional.empty());
        when(sttJobRepository.findBySttIdForUpdate(STT_ID))
                .thenReturn(Optional.of(job));
    }

    private SttRetryCommand command() {
        return new SttRetryCommand(
                MEMBER_ID,
                STUDY_ID,
                STT_ID,
                RETRY_REQUEST_ID,
                RETRY_REQUESTED_AT
        );
    }

    private SttJob failedJob(boolean retryable) {
        SttJob job = job();
        job.fail(
                "AI_TEMPORARY_ERROR",
                "일시적인 처리 실패",
                retryable,
                INITIAL_REQUESTED_AT.plusMinutes(1)
        );
        return job;
    }

    private SttJob job() {
        SttJob job = SttJob.create(
                STT_ID,
                MEMBER_ID,
                STUDY_ID,
                100L,
                AUDIO_FILE_ID,
                503L,
                UUID.randomUUID(),
                INITIAL_REQUESTED_AT
        );
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        return job;
    }

    private SttJobAttempt failedInitialAttempt() {
        SttJobAttempt attempt = SttJobAttempt.initial(
                JOB_ID,
                MEMBER_ID,
                UUID.randomUUID(),
                INITIAL_REQUESTED_AT
        );
        attempt.fail(
                "AI_TEMPORARY_ERROR",
                "일시적인 처리 실패",
                INITIAL_REQUESTED_AT.plusMinutes(1)
        );
        return attempt;
    }

    private AudioFileView validAudio(OffsetDateTime expiresAt) {
        return new AudioFileView(
                AUDIO_FILE_ID,
                MEMBER_ID,
                STUDY_ID,
                "STT_AUDIO",
                "COMPLETED",
                null,
                expiresAt,
                "stt/audio.webm",
                "audio/webm"
        );
    }

    private void assertBusinessError(
            Runnable action,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expectedErrorCode)
                );
    }
}
