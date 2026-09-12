package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitFinishCancelRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FieldVisitFinishCancelServiceTest {

    private static final Long STUDY_ID = 7L;
    private static final Long MEMBER_ID = 42L;
    private static final Long SESSION_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-07-25T07:00:00Z");

    private FieldVisitAccessService accessService;
    private FieldSessionRepository sessionRepository;
    private FieldParticipantRepository participantRepository;
    private FieldVisitFinishCancelService service;

    @BeforeEach
    void setUp() {
        accessService = mock(FieldVisitAccessService.class);
        sessionRepository = mock(FieldSessionRepository.class);
        participantRepository = mock(FieldParticipantRepository.class);
        service = new FieldVisitFinishCancelService(
                accessService,
                sessionRepository,
                participantRepository
        );
        when(accessService.requireStudy(STUDY_ID)).thenReturn(study());
    }

    @Test
    void 본인이_직접_종료한_참여는_다시_진행_중으로_되돌린다() {
        FieldSession session = session();
        FieldParticipant participant = participant();
        participant.endBySelf(NOW.minusSeconds(60), 3540);
        stubParticipation(session, participant);

        var result = service.cancel(STUDY_ID, MEMBER_ID, request());

        assertThat(participant.getStatus())
                .isEqualTo(FieldParticipantStatus.IN_PROGRESS);
        assertThat(participant.getEndedAt()).isNull();
        assertThat(participant.getEndReason()).isNull();
        assertThat(participant.getStayDurationSec()).isNull();
        assertThat(result.responseCode())
                .isEqualTo(FieldVisitResponseCode.FIELD_VISIT_FINISH_CANCEL_SUCCESS);
        assertThat(result.body().participant().status()).isEqualTo("IN_PROGRESS");
        assertThat(result.body().participant().endedAt()).isNull();
        assertThat(result.body().sessionId()).isEqualTo(SESSION_ID);
        verify(participantRepository).save(participant);
    }

    @Test
    void 이미_진행_중인_참여는_아무것도_바꾸지_않고_멱등_성공이다() {
        FieldSession session = session();
        FieldParticipant participant = participant();
        stubParticipation(session, participant);

        var result = service.cancel(STUDY_ID, MEMBER_ID, request());

        assertThat(result.responseCode()).isEqualTo(
                FieldVisitResponseCode.FIELD_PARTICIPANT_ALREADY_IN_PROGRESS
        );
        assertThat(participant.getStatus())
                .isEqualTo(FieldParticipantStatus.IN_PROGRESS);
        verify(participantRepository, never()).save(ArgumentMatchers.any());
    }

    @Test
    void 세션이_이미_종료되면_되돌릴_수_없다() {
        FieldSession session = session();
        session.endByAllParticipants(NOW.minusSeconds(10));
        FieldParticipant participant = participant();
        participant.endBySelf(NOW.minusSeconds(20), 3580);
        stubParticipation(session, participant);

        assertThatThrownBy(() -> service.cancel(STUDY_ID, MEMBER_ID, request()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.FIELD_VISIT_ALREADY_ENDED));
        verify(participantRepository, never()).save(ArgumentMatchers.any());
    }

    @Test
    void 본인이_직접_종료한_것이_아니면_되돌릴_수_없다() {
        FieldSession session = session();
        FieldParticipant participant = participant();
        participant.endByLeader(NOW.minusSeconds(20), 3580);
        stubParticipation(session, participant);

        assertThatThrownBy(() -> service.cancel(STUDY_ID, MEMBER_ID, request()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ErrorCode.FIELD_VISIT_FINISH_CANCEL_NOT_ALLOWED
                                ));
        verify(participantRepository, never()).save(ArgumentMatchers.any());
    }

    @Test
    void 세션_참여자가_아니면_접근_거부다() {
        FieldSession session = session();
        when(sessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdAndMemberIdForUpdate(
                SESSION_ID, MEMBER_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(STUDY_ID, MEMBER_ID, request()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ErrorCode.FIELD_VISIT_FINISH_CANCEL_FORBIDDEN
                                ));
    }

    @Test
    void ACTIVE_멤버가_아니면_접근_거부다() {
        doThrow(new BusinessException(
                ErrorCode.FIELD_VISIT_FINISH_CANCEL_FORBIDDEN
        )).when(accessService).requireActiveStudyMember(
                STUDY_ID, MEMBER_ID, ErrorCode.FIELD_VISIT_FINISH_CANCEL_FORBIDDEN
        );

        assertThatThrownBy(() -> service.cancel(STUDY_ID, MEMBER_ID, request()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ErrorCode.FIELD_VISIT_FINISH_CANCEL_FORBIDDEN
                                ));
    }

    @Test
    void 세션이_없으면_FIELD_VISIT_NOT_FOUND다() {
        when(sessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(STUDY_ID, MEMBER_ID, request()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.FIELD_VISIT_NOT_FOUND));
    }

    private void stubParticipation(
            FieldSession session,
            FieldParticipant participant
    ) {
        when(sessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdAndMemberIdForUpdate(
                SESSION_ID, MEMBER_ID
        )).thenReturn(Optional.of(participant));
    }

    private static FieldVisitFinishCancelRequestBody request() {
        return new FieldVisitFinishCancelRequestBody("cancel-request-1");
    }

    private static Study study() {
        Study study = Study.create(
                25L, 99L, "스터디", null, "목표", 5, StudyPurpose.INVESTMENT
        );
        ReflectionTestUtils.setField(study, "id", STUDY_ID);
        return study;
    }

    private static FieldSession session() {
        FieldSession session = FieldSession.start(STUDY_ID, NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        return session;
    }

    private static FieldParticipant participant() {
        FieldParticipant participant = FieldParticipant.start(
                SESSION_ID, MEMBER_ID, NOW.minusSeconds(3600)
        );
        ReflectionTestUtils.setField(participant, "id", 301L);
        return participant;
    }
}
