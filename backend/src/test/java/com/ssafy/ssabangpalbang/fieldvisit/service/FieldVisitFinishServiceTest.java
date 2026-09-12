package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitFinishRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.integration.ReportRequestedEvent;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FieldVisitFinishServiceTest {

    private static final Long STUDY_ID = 7L;
    private static final Long MEMBER_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-07-25T07:00:00Z");

    private FieldVisitAccessService accessService;
    private FieldSessionRepository sessionRepository;
    private FieldParticipantRepository participantRepository;
    private ChecklistProgressCounter checklistProgressCounter;
    private ReportRepository reportRepository;
    private ApplicationEventPublisher eventPublisher;
    private FieldVisitFinishService service;
    private Study study;

    @BeforeEach
    void setUp() {
        accessService = mock(FieldVisitAccessService.class);
        sessionRepository = mock(FieldSessionRepository.class);
        participantRepository = mock(FieldParticipantRepository.class);
        checklistProgressCounter = mock(ChecklistProgressCounter.class);
        reportRepository = mock(ReportRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new FieldVisitFinishService(
                accessService,
                sessionRepository,
                participantRepository,
                checklistProgressCounter,
                new FieldVisitSessionCloser(eventPublisher, reportRepository),
                reportRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        study = study();
        when(accessService.requireStudy(STUDY_ID)).thenReturn(study);
        when(checklistProgressCounter.count(anyLong(), anyLong()))
                .thenReturn(ChecklistProgressCounter.Progress.empty());
        when(reportRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.empty());
    }

    @Test
    void 참여자_한_명이_종료해도_다른_참여자가_남으면_세션은_계속된다() {
        FieldSession session = session();
        FieldParticipant participant = participant(MEMBER_ID);
        stubParticipation(session, participant);
        when(participantRepository.countBySessionIdAndStatus(
                100L, FieldParticipantStatus.IN_PROGRESS
        )).thenReturn(1L);

        var result = service.finish(STUDY_ID, MEMBER_ID, request(true));

        assertThat(participant.getStatus()).isEqualTo(FieldParticipantStatus.ENDED);
        assertThat(participant.getEndReason()).isEqualTo("SELF_ENDED");
        assertThat(result.responseCode())
                .isEqualTo(FieldVisitResponseCode.FIELD_VISIT_FINISH_SUCCESS);
        assertThat(result.body().sessionEnded()).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 마지막_참여자가_종료하면_세션을_종료하고_이벤트를_한_번_발행한다() {
        FieldSession session = session();
        FieldParticipant participant = participant(MEMBER_ID);
        stubParticipation(session, participant);
        when(participantRepository.countBySessionIdAndStatus(
                100L, FieldParticipantStatus.IN_PROGRESS
        )).thenReturn(0L);
        Report report = report(48L);
        when(reportRepository.findByStudyId(STUDY_ID))
                .thenReturn(Optional.of(report));

        var result = service.finish(STUDY_ID, MEMBER_ID, request(true));

        assertThat(session.getStatus()).isEqualTo(FieldSessionStatus.ENDED);
        assertThat(session.getEndReason()).isEqualTo("ALL_ENDED");
        assertThat(session.getEndedById()).isNull();
        assertThat(result.responseCode()).isEqualTo(
                FieldVisitResponseCode.FIELD_VISIT_FINISH_AND_SESSION_END_SUCCESS
        );
        assertThat(result.body().sessionEnded()).isTrue();
        assertThat(result.body().reportTriggered()).isTrue();
        assertThat(result.body().reportId()).isEqualTo(48L);
        verify(reportRepository).saveAndFlush(any(Report.class));
        verify(eventPublisher).publishEvent(any(ReportRequestedEvent.class));
    }

    @Test
    void 이미_종료한_참여자의_종료_정보는_재호출해도_바뀌지_않는다() {
        FieldSession session = session();
        FieldParticipant participant = participant(MEMBER_ID);
        Instant firstEndedAt = NOW.minusSeconds(30);
        participant.endBySelf(firstEndedAt, 3570);
        stubParticipation(session, participant);

        var result = service.finish(STUDY_ID, MEMBER_ID, request(true));

        assertThat(participant.getEndedAt()).isEqualTo(firstEndedAt);
        assertThat(participant.getStayDurationSec()).isEqualTo(3570);
        assertThat(participant.getEndReason()).isEqualTo("SELF_ENDED");
        assertThat(result.responseCode()).isEqualTo(
                FieldVisitResponseCode.FIELD_PARTICIPANT_ALREADY_ENDED
        );
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 종료된_세션의_진행_참여자도_409가_아닌_200_결과를_반환한다() {
        FieldSession session = session();
        session.endByLeader(NOW.minusSeconds(10), 99L);
        FieldParticipant participant = participant(MEMBER_ID);
        stubParticipation(session, participant);

        var result = service.finish(STUDY_ID, MEMBER_ID, request(true));

        assertThat(result.responseCode()).isEqualTo(
                FieldVisitResponseCode.FIELD_PARTICIPANT_ALREADY_ENDED
        );
        assertThat(result.body().participant().status()).isEqualTo("ENDED");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 전원_종료된_세션의_진행_참여자는_강제_종료로_정합성을_맞춘다() {
        FieldSession session = session();
        session.endByAllParticipants(NOW.minusSeconds(10));
        FieldParticipant participant = participant(MEMBER_ID);
        stubParticipation(session, participant);

        var result = service.finish(STUDY_ID, MEMBER_ID, request(true));

        assertThat(result.responseCode()).isEqualTo(
                FieldVisitResponseCode.FIELD_PARTICIPANT_ALREADY_ENDED
        );
        assertThat(participant.getStatus()).isEqualTo(FieldParticipantStatus.ENDED);
        assertThat(participant.getEndReason()).isEqualTo("LEADER_FORCED");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void finishConfirmed_false는_확인_필요_오류다() {
        assertThatThrownBy(() -> service.finish(
                STUDY_ID, MEMBER_ID, request(false)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(
                        ErrorCode.FIELD_VISIT_FINISH_CONFIRMATION_REQUIRED
                ));
    }

    @Test
    void finishConfirmed_null도_확인_필요_오류다() {
        assertThatThrownBy(() -> service.finish(
                STUDY_ID, MEMBER_ID, request(null)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(
                        ErrorCode.FIELD_VISIT_FINISH_CONFIRMATION_REQUIRED
                ));
    }

    @Test
    void 세션_참여자가_아니면_접근_거부다() {
        FieldSession session = session();
        when(sessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdAndMemberIdForUpdate(
                100L, MEMBER_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.finish(
                STUDY_ID, MEMBER_ID, request(true)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.FIELD_VISIT_FINISH_FORBIDDEN));
    }

    @Test
    void 세션이_없으면_FIELD_VISIT_NOT_FOUND다() {
        when(sessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.finish(
                STUDY_ID, MEMBER_ID, request(true)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.FIELD_VISIT_NOT_FOUND));
    }

    @Test
    void 체크리스트가_없으면_진행_수치는_모두_0이다() {
        FieldSession session = session();
        FieldParticipant participant = participant(MEMBER_ID);
        stubParticipation(session, participant);
        when(participantRepository.countBySessionIdAndStatus(
                100L, FieldParticipantStatus.IN_PROGRESS
        )).thenReturn(1L);

        var result = service.finish(STUDY_ID, MEMBER_ID, request(true));

        assertThat(result.body().checklist())
                .isEqualTo(new com.ssafy.ssabangpalbang.fieldvisit.dto.response
                        .FieldVisitFinishResponse.ChecklistBody(0, 0, 0));
    }

    @Test
    void 시작_시각이_종료_시각보다_미래면_체류시간을_0으로_보정한다() {
        FieldSession session = session();
        FieldParticipant participant = FieldParticipant.start(
                100L, MEMBER_ID, NOW.plusSeconds(60)
        );
        ReflectionTestUtils.setField(participant, "id", 301L);
        stubParticipation(session, participant);
        when(participantRepository.countBySessionIdAndStatus(
                100L, FieldParticipantStatus.IN_PROGRESS
        )).thenReturn(1L);

        service.finish(STUDY_ID, MEMBER_ID, request(true));

        assertThat(participant.getStayDurationSec()).isZero();
    }

    private void stubParticipation(
            FieldSession session,
            FieldParticipant participant
    ) {
        when(sessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdAndMemberIdForUpdate(
                100L, MEMBER_ID
        )).thenReturn(Optional.of(participant));
    }

    private static FieldVisitFinishRequestBody request(Boolean confirmed) {
        return new FieldVisitFinishRequestBody(confirmed, "request-1");
    }

    private static Study study() {
        Study study = Study.create(
                25L, 99L, "스터디", null, "목표", 5, StudyPurpose.INVESTMENT
        );
        ReflectionTestUtils.setField(study, "id", STUDY_ID);
        return study;
    }

    private static FieldSession session() {
        FieldSession session = FieldSession.start(
                STUDY_ID, NOW.minusSeconds(3600)
        );
        ReflectionTestUtils.setField(session, "id", 100L);
        return session;
    }

    private static FieldParticipant participant(Long memberId) {
        FieldParticipant participant = FieldParticipant.start(
                100L, memberId, NOW.minusSeconds(3600)
        );
        ReflectionTestUtils.setField(participant, "id", 301L);
        return participant;
    }

    private static Report report(Long id) {
        Report report = Report.create(STUDY_ID, 100L, 25L);
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }
}
