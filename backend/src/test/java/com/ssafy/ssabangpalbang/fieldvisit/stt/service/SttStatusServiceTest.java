package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.response.SttStatusResponse;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobAttemptRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttStatusQueryRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttStatusQueryRepository.AudioFileStateView;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttStatusQueryRepository.FieldRecordView;
import com.ssafy.ssabangpalbang.fieldvisit.stt.support.SttIdGenerator;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.auth.LoginMember;
import com.ssafy.ssabangpalbang.member.auth.LoginMemberResolver;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttStatusServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long STUDY_ID = 10L;
    private static final Long CHECKLIST_ITEM_ID = 501L;
    private static final Long AUDIO_FILE_ID = 90L;
    private static final Long FIELD_RECORD_ID = 830L;
    private static final String STT_ID = "stt-status";
    private static final OffsetDateTime REQUESTED_AT = OffsetDateTime.of(
            2026,
            7,
            25,
            14,
            25,
            0,
            0,
            ZoneOffset.ofHours(9)
    );

    @Mock
    private LoginMemberResolver loginMemberResolver;

    @Mock
    private StudyMemberRepository studyMemberRepository;

    @Mock
    private SttJobRepository sttJobRepository;

    @Mock
    private SttJobAttemptRepository sttJobAttemptRepository;

    @Mock
    private SttRequestQueryRepository requestQueryRepository;

    @Mock
    private SttStatusQueryRepository statusQueryRepository;

    @Mock
    private SttCreationTransaction creationTransaction;

    @Mock
    private SttIdGenerator sttIdGenerator;

    @InjectMocks
    private SttService sttService;

    @ParameterizedTest
    @EnumSource(
            value = SttStatus.class,
            names = {"PENDING", "PROCESSING"}
    )
    void 처리_중_상태는_결과_필드를_노출하지_않는다(SttStatus status) {
        SttJob job = stubOwnedJob(status);

        SttStatusResponse response = sttService.getSttStatus(
                STUDY_ID,
                STT_ID
        );

        assertThat(response.status()).isEqualTo(status);
        assertThat(response.sourceId()).isNull();
        assertThat(response.textContent()).isNull();
        assertThat(response.failReason()).isNull();
        assertThat(response.retryable()).isFalse();
        assertThat(response.completedAt()).isNull();
        assertThat(response.requestedAt()).isEqualTo(job.getRequestedAt());
        verifyNoInteractions(
                statusQueryRepository,
                studyMemberRepository,
                requestQueryRepository
        );
    }

    @Test
    void DONE은_정합한_현장_기록의_결과를_반환한다() {
        SttJob job = stubOwnedJob(SttStatus.DONE);
        OffsetDateTime completedAt = REQUESTED_AT.plusSeconds(8);
        ReflectionTestUtils.setField(
                job,
                "fieldRecordId",
                FIELD_RECORD_ID
        );
        ReflectionTestUtils.setField(job, "completedAt", completedAt);
        when(statusQueryRepository.findFieldRecord(FIELD_RECORD_ID))
                .thenReturn(Optional.of(new FieldRecordView(
                        FIELD_RECORD_ID,
                        CHECKLIST_ITEM_ID,
                        "STT",
                        "DONE",
                        "역에서 단지 입구까지 경사가 있습니다."
                )));

        SttStatusResponse response = sttService.getSttStatus(
                STUDY_ID,
                STT_ID
        );

        assertThat(response.status()).isEqualTo(SttStatus.DONE);
        assertThat(response.sourceId()).isEqualTo(FIELD_RECORD_ID);
        assertThat(response.textContent())
                .isEqualTo("역에서 단지 입구까지 경사가 있습니다.");
        assertThat(response.failReason()).isNull();
        assertThat(response.retryable()).isFalse();
        assertThat(response.completedAt()).isEqualTo(completedAt);
    }

    @Test
    void DONE_현장_기록이_완료_조건과_다르면_정합성_오류다() {
        SttJob job = stubOwnedJob(SttStatus.DONE);
        ReflectionTestUtils.setField(
                job,
                "fieldRecordId",
                FIELD_RECORD_ID
        );
        ReflectionTestUtils.setField(
                job,
                "completedAt",
                REQUESTED_AT.plusSeconds(8)
        );
        when(statusQueryRepository.findFieldRecord(FIELD_RECORD_ID))
                .thenReturn(Optional.of(new FieldRecordView(
                        FIELD_RECORD_ID,
                        CHECKLIST_ITEM_ID + 1,
                        "STT",
                        "DONE",
                        "완료 텍스트"
                )));

        assertThatThrownBy(() -> sttService.getSttStatus(
                STUDY_ID,
                STT_ID
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STT DONE 데이터 정합성 오류");
    }

    @Test
    void 재시도_가능_실패와_유효한_원본이면_retryable_true다() {
        SttJob job = stubOwnedJob(SttStatus.FAILED);
        markFailed(job, true);
        when(statusQueryRepository.findAudioFileState(AUDIO_FILE_ID))
                .thenReturn(Optional.of(validAudio()));

        SttStatusResponse response = sttService.getSttStatus(
                STUDY_ID,
                STT_ID
        );

        assertThat(response.status()).isEqualTo(SttStatus.FAILED);
        assertThat(response.failReason())
                .isEqualTo("인식 가능한 발화를 찾지 못했습니다.");
        assertThat(response.retryable()).isTrue();
        assertThat(response.sourceId()).isNull();
        assertThat(response.textContent()).isNull();
        assertThat(response.completedAt()).isNull();
    }

    @Test
    void 삭제된_원본은_retryable_false다() {
        SttJob job = stubOwnedJob(SttStatus.FAILED);
        markFailed(job, true);
        when(statusQueryRepository.findAudioFileState(AUDIO_FILE_ID))
                .thenReturn(Optional.of(new AudioFileStateView(
                        "STT_AUDIO",
                        "COMPLETED",
                        OffsetDateTime.now(ZoneOffset.ofHours(9)),
                        OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(1)
                )));

        assertThat(sttService.getSttStatus(STUDY_ID, STT_ID).retryable())
                .isFalse();
    }

    @Test
    void 만료된_원본은_retryable_false다() {
        SttJob job = stubOwnedJob(SttStatus.FAILED);
        markFailed(job, true);
        when(statusQueryRepository.findAudioFileState(AUDIO_FILE_ID))
                .thenReturn(Optional.of(new AudioFileStateView(
                        "STT_AUDIO",
                        "COMPLETED",
                        null,
                        OffsetDateTime.now(ZoneOffset.ofHours(9)).minusMinutes(1)
                )));

        assertThat(sttService.getSttStatus(STUDY_ID, STT_ID).retryable())
                .isFalse();
    }

    @Test
    void 영구_실패는_원본을_조회하지_않고_retryable_false다() {
        SttJob job = stubOwnedJob(SttStatus.FAILED);
        markFailed(job, false);

        assertThat(sttService.getSttStatus(STUDY_ID, STT_ID).retryable())
                .isFalse();
        verifyNoInteractions(statusQueryRepository);
    }

    @Test
    void 작업의_studyId가_경로와_다르면_400_오류다() {
        stubOwnedJob(SttStatus.PENDING);

        assertBusinessError(
                () -> sttService.getSttStatus(STUDY_ID + 1, STT_ID),
                ErrorCode.FIELD_STT_STUDY_MISMATCH
        );
    }

    @Test
    void 다른_회원의_작업은_403_오류다() {
        activeLoginMember();
        SttJob job = job(SttStatus.PENDING);
        ReflectionTestUtils.setField(job, "memberId", MEMBER_ID + 1);
        when(sttJobRepository.findBySttId(STT_ID))
                .thenReturn(Optional.of(job));

        assertBusinessError(
                () -> sttService.getSttStatus(STUDY_ID, STT_ID),
                ErrorCode.FIELD_STT_STATUS_FORBIDDEN
        );
    }

    @Test
    void 없는_작업은_404_오류다() {
        activeLoginMember();
        when(sttJobRepository.findBySttId(STT_ID))
                .thenReturn(Optional.empty());

        assertBusinessError(
                () -> sttService.getSttStatus(STUDY_ID, STT_ID),
                ErrorCode.FIELD_STT_NOT_FOUND
        );
    }

    @Test
    void 반복_조회는_작업이나_임장_상태를_변경하지_않는다() {
        stubOwnedJob(SttStatus.PROCESSING);

        SttStatusResponse first = sttService.getSttStatus(
                STUDY_ID,
                STT_ID
        );
        SttStatusResponse second = sttService.getSttStatus(
                STUDY_ID,
                STT_ID
        );

        assertThat(second).isEqualTo(first);
        verify(sttJobRepository, times(2)).findBySttId(STT_ID);
        verify(sttJobRepository, never()).save(any());
        verifyNoInteractions(
                studyMemberRepository,
                requestQueryRepository,
                creationTransaction
        );
    }

    private SttJob stubOwnedJob(SttStatus status) {
        activeLoginMember();
        SttJob job = job(status);
        when(sttJobRepository.findBySttId(STT_ID))
                .thenReturn(Optional.of(job));
        return job;
    }

    private void activeLoginMember() {
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(MEMBER_ID, true));
    }

    private SttJob job(SttStatus status) {
        SttJob job = SttJob.create(
                STT_ID,
                MEMBER_ID,
                STUDY_ID,
                100L,
                AUDIO_FILE_ID,
                CHECKLIST_ITEM_ID,
                UUID.randomUUID(),
                REQUESTED_AT
        );
        ReflectionTestUtils.setField(job, "status", status);
        return job;
    }

    private void markFailed(SttJob job, boolean retryable) {
        ReflectionTestUtils.setField(job, "retryable", retryable);
        ReflectionTestUtils.setField(
                job,
                "failReason",
                "인식 가능한 발화를 찾지 못했습니다."
        );
    }

    private AudioFileStateView validAudio() {
        return new AudioFileStateView(
                "STT_AUDIO",
                "COMPLETED",
                null,
                OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(1)
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
