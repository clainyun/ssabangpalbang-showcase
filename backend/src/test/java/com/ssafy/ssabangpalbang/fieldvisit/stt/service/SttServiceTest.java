package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJobAttempt;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.request.SttCreateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobAttemptRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository.AudioFileView;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository.ChecklistItemView;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository.FieldSessionView;
import com.ssafy.ssabangpalbang.fieldvisit.stt.response.SttResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.stt.support.SttIdGenerator;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.auth.LoginMember;
import com.ssafy.ssabangpalbang.member.auth.LoginMemberResolver;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long STUDY_ID = 10L;
    private static final Long SESSION_ID = 100L;
    private static final Long AUDIO_FILE_ID = 90L;
    private static final Long CHECKLIST_ITEM_ID = 501L;
    private static final String CLIENT_REQUEST_ID =
            "81197c8f-780b-40c2-abf6-b83473de9c82";
    private static final UUID CLIENT_REQUEST_UUID =
            UUID.fromString(CLIENT_REQUEST_ID);

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
    private SttCreationTransaction creationTransaction;

    @Mock
    private SttIdGenerator sttIdGenerator;

    @InjectMocks
    private SttService sttService;

    @Test
    void 신규_요청을_PENDING_작업으로_접수한다() {
        stubNoExistingRequest();
        stubValidContext();
        when(sttIdGenerator.generate()).thenReturn("stt-new");
        when(creationTransaction.create(any())).thenAnswer(invocation -> {
            SttCreatePersistenceCommand command = invocation.getArgument(0);
            return job(
                    command.sttId(),
                    command.clientRequestId(),
                    command.requestedAt()
            );
        });

        SttService.CreateResult result = sttService.createStt(
                STUDY_ID,
                validRequest()
        );

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(result.responseCode())
                .isEqualTo(SttResponseCode.FIELD_STT_ACCEPTED);
        assertThat(result.response().sttId()).isEqualTo("stt-new");
        assertThat(result.response().status().name()).isEqualTo("PENDING");
        assertThat(result.response().sourceId()).isNull();

        ArgumentCaptor<SttCreatePersistenceCommand> commandCaptor =
                ArgumentCaptor.forClass(SttCreatePersistenceCommand.class);
        verify(creationTransaction).create(commandCaptor.capture());
        assertThat(commandCaptor.getValue().sessionId()).isEqualTo(SESSION_ID);
        assertThat(commandCaptor.getValue().objectKey())
                .isEqualTo("stt/audio.webm");
        assertThat(commandCaptor.getValue().contentType())
                .isEqualTo("audio/webm");
    }

    @Test
    void 세션이_끝난_뒤에도_동일_요청은_기존_작업을_반환한다() {
        activeLoginMember();
        SttJob existing = job(
                "stt-existing",
                CLIENT_REQUEST_UUID,
                OffsetDateTime.now(ZoneOffset.ofHours(9)).minusMinutes(3)
        );
        SttJobAttempt attempt = SttJobAttempt.initial(
                44L,
                MEMBER_ID,
                CLIENT_REQUEST_UUID,
                existing.getRequestedAt()
        );
        when(sttJobAttemptRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID,
                CLIENT_REQUEST_UUID
        )).thenReturn(Optional.of(attempt));
        when(sttJobRepository.findById(44L)).thenReturn(Optional.of(existing));

        SttService.CreateResult result = sttService.createStt(
                STUDY_ID,
                validRequest()
        );

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.responseCode())
                .isEqualTo(SttResponseCode.FIELD_STT_ALREADY_REQUESTED);
        assertThat(result.response().requestedAt())
                .isEqualTo(existing.getRequestedAt());
        verifyNoInteractions(studyMemberRepository, requestQueryRepository);
        verify(creationTransaction, never()).create(any());
    }

    @Test
    void 동일한_clientRequestId의_fingerprint가_다르면_거부한다() {
        activeLoginMember();
        SttJob existing = job(
                "stt-existing",
                CLIENT_REQUEST_UUID,
                OffsetDateTime.now(ZoneOffset.ofHours(9))
        );
        SttJobAttempt attempt = SttJobAttempt.initial(
                44L,
                MEMBER_ID,
                CLIENT_REQUEST_UUID,
                existing.getRequestedAt()
        );
        when(sttJobAttemptRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID,
                CLIENT_REQUEST_UUID
        )).thenReturn(Optional.of(attempt));
        when(sttJobRepository.findById(44L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> sttService.createStt(
                STUDY_ID,
                new SttCreateRequest(
                        AUDIO_FILE_ID,
                        CHECKLIST_ITEM_ID + 1,
                        CLIENT_REQUEST_ID
                )
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                ErrorCode.FIELD_STT_IDEMPOTENCY_KEY_REUSED
                        ));

        verifyNoInteractions(studyMemberRepository, requestQueryRepository);
        verify(creationTransaction, never()).create(any());
    }

    @Test
    void 같은_음성의_동일_fingerprint는_새_요청_키여도_기존_작업을_반환한다() {
        activeLoginMember();
        when(sttJobAttemptRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID,
                CLIENT_REQUEST_UUID
        )).thenReturn(Optional.empty());
        SttJob existing = job(
                "stt-existing",
                UUID.randomUUID(),
                OffsetDateTime.now(ZoneOffset.ofHours(9))
        );
        when(sttJobRepository.findByAudioFileId(AUDIO_FILE_ID))
                .thenReturn(Optional.of(existing));

        SttService.CreateResult result = sttService.createStt(
                STUDY_ID,
                validRequest()
        );

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.OK);
        verifyNoInteractions(studyMemberRepository, requestQueryRepository);
        verify(creationTransaction, never()).create(any());
    }

    @Test
    void 타인이_사용한_audioFileId는_작업_존재를_노출하지_않고_403을_반환한다() {
        activeLoginMember();
        when(sttJobAttemptRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID,
                CLIENT_REQUEST_UUID
        )).thenReturn(Optional.empty());
        SttJob otherMemberJob = SttJob.create(
                "stt-other",
                MEMBER_ID + 1,
                STUDY_ID,
                SESSION_ID,
                AUDIO_FILE_ID,
                CHECKLIST_ITEM_ID,
                UUID.randomUUID(),
                OffsetDateTime.now(ZoneOffset.ofHours(9))
        );
        when(sttJobRepository.findByAudioFileId(AUDIO_FILE_ID))
                .thenReturn(Optional.of(otherMemberJob));

        assertThatThrownBy(() -> sttService.createStt(
                STUDY_ID,
                validRequest()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.FIELD_STT_REQUEST_FORBIDDEN));

        verifyNoInteractions(studyMemberRepository, requestQueryRepository);
        verify(creationTransaction, never()).create(any());
    }

    @Test
    void Java가_허용하는_축약_UUID도_명세상_거부한다() {
        activeLoginMember();

        assertThatThrownBy(() -> sttService.createStt(
                STUDY_ID,
                new SttCreateRequest(
                        AUDIO_FILE_ID,
                        CHECKLIST_ITEM_ID,
                        "1-1-1-1-1"
                )
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                    assertThat(exception.getData())
                            .containsEntry("field", "clientRequestId");
                });

        verifyNoInteractions(
                sttJobAttemptRepository,
                sttJobRepository,
                requestQueryRepository
        );
    }

    @Test
    void 비활성_회원은_신규_작업을_만들_수_없다() {
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(MEMBER_ID, false));

        assertThatThrownBy(() -> sttService.createStt(
                STUDY_ID,
                validRequest()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));

        verifyNoInteractions(
                sttJobAttemptRepository,
                sttJobRepository,
                requestQueryRepository
        );
    }

    @Test
    void 종료된_참여자는_신규_작업을_만들_수_없다() {
        stubNoExistingRequest();
        stubActiveStudyMember();
        when(requestQueryRepository.findFieldSessionByStudyId(STUDY_ID))
                .thenReturn(Optional.of(new FieldSessionView(
                        SESSION_ID,
                        "IN_PROGRESS"
                )));
        when(requestQueryRepository.findParticipantStatus(
                SESSION_ID,
                MEMBER_ID
        )).thenReturn(Optional.of("ENDED"));

        assertThatThrownBy(() -> sttService.createStt(
                STUDY_ID,
                validRequest()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(
                        ErrorCode.FIELD_PARTICIPANT_ALREADY_ENDED
                ));

        verify(creationTransaction, never()).create(any());
    }

    @Test
    void 다른_회원의_체크리스트_항목은_거부한다() {
        stubNoExistingRequest();
        stubSessionAndParticipant();
        when(requestQueryRepository.findChecklistItem(CHECKLIST_ITEM_ID))
                .thenReturn(Optional.of(new ChecklistItemView(
                        CHECKLIST_ITEM_ID,
                        MEMBER_ID + 1,
                        SESSION_ID,
                        STUDY_ID
                )));

        assertThatThrownBy(() -> sttService.createStt(
                STUDY_ID,
                validRequest()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(
                        ErrorCode.FIELD_STT_REQUEST_FORBIDDEN
                ));

        verify(requestQueryRepository, never()).findAudioFile(anyLong());
        verify(creationTransaction, never()).create(any());
    }

    @Test
    void 존재하지_않는_체크리스트_항목은_404로_거부한다() {
        stubNoExistingRequest();
        stubSessionAndParticipant();
        when(requestQueryRepository.findChecklistItem(CHECKLIST_ITEM_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sttService.createStt(
                STUDY_ID,
                validRequest()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHECKLIST_ITEM_NOT_FOUND));

        verify(requestQueryRepository, never()).findAudioFile(anyLong());
        verify(creationTransaction, never()).create(any());
    }

    @Test
    void 존재하지_않는_음성_파일은_404로_거부한다() {
        stubNoExistingRequest();
        stubValidChecklistContext();
        when(requestQueryRepository.findAudioFile(AUDIO_FILE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sttService.createStt(
                STUDY_ID,
                validRequest()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.MEDIA_FILE_NOT_FOUND));

        verify(creationTransaction, never()).create(any());
    }

    @Test
    void 타인_소유_음성은_403으로_거부한다() {
        stubNoExistingRequest();
        stubValidChecklistContext();
        AudioFileView forbiddenFile = new AudioFileView(
                AUDIO_FILE_ID,
                MEMBER_ID + 1,
                STUDY_ID,
                "STT_AUDIO",
                "COMPLETED",
                null,
                OffsetDateTime.now(ZoneOffset.ofHours(9)).plusHours(1),
                "stt/audio.webm",
                "audio/webm"
        );
        when(requestQueryRepository.findAudioFile(AUDIO_FILE_ID))
                .thenReturn(Optional.of(forbiddenFile));

        assertThatThrownBy(() -> sttService.createStt(
                STUDY_ID,
                validRequest()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.FIELD_STT_REQUEST_FORBIDDEN));

        verify(creationTransaction, never()).create(any());
    }

    @Test
    void 용도가_다르거나_삭제된_음성은_400으로_거부한다() {
        stubNoExistingRequest();
        stubValidChecklistContext();
        AudioFileView invalidFile = new AudioFileView(
                AUDIO_FILE_ID,
                MEMBER_ID,
                STUDY_ID,
                "FIELD_PHOTO",
                "COMPLETED",
                OffsetDateTime.now(ZoneOffset.ofHours(9)),
                OffsetDateTime.now(ZoneOffset.ofHours(9)).plusHours(1),
                "stt/audio.webm",
                "audio/webm"
        );
        when(requestQueryRepository.findAudioFile(AUDIO_FILE_ID))
                .thenReturn(Optional.of(invalidFile));

        assertThatThrownBy(() -> sttService.createStt(
                STUDY_ID,
                validRequest()
        )).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.FIELD_STT_AUDIO_INVALID);
            assertThat(exception.getData())
                    .containsEntry("requiredFileUsage", "STT_AUDIO");
        });

        verify(creationTransaction, never()).create(any());
    }

    @Test
    void 만료된_음성은_410으로_거부한다() {
        stubNoExistingRequest();
        stubValidChecklistContext();
        when(requestQueryRepository.findAudioFile(AUDIO_FILE_ID))
                .thenReturn(Optional.of(audioFile(
                        OffsetDateTime.now(ZoneOffset.ofHours(9)).minusSeconds(1)
                )));

        assertThatThrownBy(() -> sttService.createStt(
                STUDY_ID,
                validRequest()
        )).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.FIELD_STT_AUDIO_EXPIRED);
            assertThat(exception.getData())
                    .containsEntry("audioFileId", AUDIO_FILE_ID);
        });

        verify(creationTransaction, never()).create(any());
    }

    @Test
    void UNIQUE_경합_뒤_동일_요청을_재조회해_200을_반환한다() {
        activeLoginMember();
        when(sttJobAttemptRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID,
                CLIENT_REQUEST_UUID
        )).thenReturn(Optional.empty(), Optional.of(SttJobAttempt.initial(
                44L,
                MEMBER_ID,
                CLIENT_REQUEST_UUID,
                OffsetDateTime.now(ZoneOffset.ofHours(9))
        )));
        when(sttJobRepository.findByAudioFileId(AUDIO_FILE_ID))
                .thenReturn(Optional.empty());
        stubValidContextWithoutLogin();
        when(sttIdGenerator.generate()).thenReturn("stt-loser");
        when(creationTransaction.create(any()))
                .thenThrow(new DataIntegrityViolationException("unique"));
        SttJob winner = job(
                "stt-winner",
                CLIENT_REQUEST_UUID,
                OffsetDateTime.now(ZoneOffset.ofHours(9))
        );
        when(sttJobRepository.findById(44L)).thenReturn(Optional.of(winner));

        SttService.CreateResult result = sttService.createStt(
                STUDY_ID,
                validRequest()
        );

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.response().sttId()).isEqualTo("stt-winner");
    }

    private void stubNoExistingRequest() {
        activeLoginMember();
        when(sttJobAttemptRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID,
                CLIENT_REQUEST_UUID
        )).thenReturn(Optional.empty());
        when(sttJobRepository.findByAudioFileId(AUDIO_FILE_ID))
                .thenReturn(Optional.empty());
    }

    private void activeLoginMember() {
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(MEMBER_ID, true));
    }

    private void stubValidContext() {
        stubValidContextWithoutLogin();
    }

    private void stubValidContextWithoutLogin() {
        stubValidChecklistContext();
        when(requestQueryRepository.findAudioFile(AUDIO_FILE_ID))
                .thenReturn(Optional.of(audioFile(
                        OffsetDateTime.now(ZoneOffset.ofHours(9)).plusHours(1)
                )));
    }

    private void stubValidChecklistContext() {
        stubSessionAndParticipant();
        when(requestQueryRepository.findChecklistItem(CHECKLIST_ITEM_ID))
                .thenReturn(Optional.of(new ChecklistItemView(
                        CHECKLIST_ITEM_ID,
                        MEMBER_ID,
                        SESSION_ID,
                        STUDY_ID
                )));
    }

    private void stubSessionAndParticipant() {
        stubActiveStudyMember();
        when(requestQueryRepository.findFieldSessionByStudyId(STUDY_ID))
                .thenReturn(Optional.of(new FieldSessionView(
                        SESSION_ID,
                        "IN_PROGRESS"
                )));
        when(requestQueryRepository.findParticipantStatus(
                SESSION_ID,
                MEMBER_ID
        )).thenReturn(Optional.of("IN_PROGRESS"));
    }

    private void stubActiveStudyMember() {
        StudyMember studyMember = mock(StudyMember.class);
        when(studyMember.getStatus()).thenReturn(StudyMemberStatus.ACTIVE);
        when(studyMemberRepository.findByStudyIdAndMemberId(
                STUDY_ID,
                MEMBER_ID
        )).thenReturn(Optional.of(studyMember));
    }

    private SttCreateRequest validRequest() {
        return new SttCreateRequest(
                AUDIO_FILE_ID,
                CHECKLIST_ITEM_ID,
                CLIENT_REQUEST_ID
        );
    }

    private AudioFileView audioFile(OffsetDateTime expiresAt) {
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

    private SttJob job(
            String sttId,
            UUID clientRequestId,
            OffsetDateTime requestedAt
    ) {
        return SttJob.create(
                sttId,
                MEMBER_ID,
                STUDY_ID,
                SESSION_ID,
                AUDIO_FILE_ID,
                CHECKLIST_ITEM_ID,
                clientRequestId,
                requestedAt
        );
    }
}
