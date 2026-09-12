package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitCloseRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.integration.ReportRequestedEvent;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FieldVisitCloseServiceTest {

    private static final Long STUDY_ID = 7L;
    private static final Long LEADER_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-07-25T07:00:00Z");

    private FieldVisitAccessService accessService;
    private FieldSessionRepository sessionRepository;
    private FieldParticipantRepository participantRepository;
    private ReportRepository reportRepository;
    private ApplicationEventPublisher eventPublisher;
    private FieldVisitCloseService service;
    private Study study;

    @BeforeEach
    void setUp() {
        accessService = mock(FieldVisitAccessService.class);
        sessionRepository = mock(FieldSessionRepository.class);
        participantRepository = mock(FieldParticipantRepository.class);
        reportRepository = mock(ReportRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new FieldVisitCloseService(
                accessService,
                sessionRepository,
                participantRepository,
                new FieldVisitSessionCloser(eventPublisher, reportRepository),
                reportRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        study = study();
        when(accessService.requireStudy(STUDY_ID)).thenReturn(study);
        when(reportRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.empty());
    }

    @Test
    void 스터디장_마감은_미종료자와_세션을_강제_종료하고_이벤트를_발행한다() {
        FieldSession session = session();
        FieldParticipant first = participant(301L, LEADER_ID);
        FieldParticipant second = participant(302L, 43L);
        stubSession(session, List.of(first, second));
        Report report = report(48L);
        when(reportRepository.findByStudyId(STUDY_ID))
                .thenReturn(Optional.of(report));

        var result = service.close(STUDY_ID, LEADER_ID, request(true));

        assertThat(first.getStatus()).isEqualTo(FieldParticipantStatus.ENDED);
        assertThat(second.getStatus()).isEqualTo(FieldParticipantStatus.ENDED);
        assertThat(first.getEndReason()).isEqualTo("LEADER_FORCED");
        assertThat(session.getStatus()).isEqualTo(FieldSessionStatus.ENDED);
        assertThat(session.getEndedById()).isEqualTo(LEADER_ID);
        assertThat(session.getEndReason()).isEqualTo("LEADER_FORCED");
        assertThat(result.body().forcedEndedCount()).isEqualTo(2);
        assertThat(result.body().reportId()).isEqualTo(48L);
        assertThat(result.body().reportStatus()).isEqualTo("PENDING");
        verify(reportRepository).saveAndFlush(any(Report.class));
        verify(eventPublisher).publishEvent(any(ReportRequestedEvent.class));
    }

    @Test
    void 이미_개인_종료한_참여자는_강제_종료_목록과_변경에서_제외된다() {
        FieldSession session = session();
        FieldParticipant alreadyEnded = participant(301L, LEADER_ID);
        Instant firstEndedAt = NOW.minusSeconds(120);
        alreadyEnded.endBySelf(firstEndedAt, 3480);
        FieldParticipant inProgress = participant(302L, 43L);
        stubSession(session, List.of(inProgress));

        var result = service.close(STUDY_ID, LEADER_ID, request(true));

        assertThat(alreadyEnded.getEndedAt()).isEqualTo(firstEndedAt);
        assertThat(alreadyEnded.getEndReason()).isEqualTo("SELF_ENDED");
        assertThat(alreadyEnded.getStayDurationSec()).isEqualTo(3480);
        assertThat(result.body().forcedEndedParticipants())
                .extracting(p -> p.participantId())
                .containsExactly(302L);
    }

    @Test
    void 미종료자가_없어도_세션을_종료하고_이벤트를_발행한다() {
        FieldSession session = session();
        stubSession(session, List.of());

        var result = service.close(STUDY_ID, LEADER_ID, request(true));

        assertThat(session.getStatus()).isEqualTo(FieldSessionStatus.ENDED);
        assertThat(result.body().forcedEndedParticipants()).isEmpty();
        assertThat(result.body().forcedEndedCount()).isZero();
        verify(eventPublisher).publishEvent(any(ReportRequestedEvent.class));
    }

    @Test
    void 미종료자가_있고_확인하지_않으면_실제_인원수를_담아_거부한다() {
        FieldSession session = session();
        stubSession(session, List.of(
                participant(301L, LEADER_ID),
                participant(302L, 43L)
        ));

        assertThatThrownBy(() -> service.close(
                STUDY_ID, LEADER_ID, request(false)
        )).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(
                    ErrorCode.FIELD_VISIT_CLOSE_CONFIRMATION_REQUIRED
            );
            assertThat(exception.getData())
                    .containsEntry("unfinishedParticipantCount", 2);
        });
    }

    @Test
    void 미종료자가_없으면_closeConfirmed_false여도_마감한다() {
        FieldSession session = session();
        stubSession(session, List.of());

        var result = service.close(STUDY_ID, LEADER_ID, request(false));

        assertThat(result.responseCode())
                .isEqualTo(FieldVisitResponseCode.FIELD_VISIT_CLOSE_SUCCESS);
        assertThat(session.getStatus()).isEqualTo(FieldSessionStatus.ENDED);
    }

    @Test
    void 스터디장이_아니면_마감할_수_없다() {
        assertThatThrownBy(() -> service.close(
                STUDY_ID, 99L, request(true)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.FIELD_VISIT_CLOSE_FORBIDDEN));
    }

    @Test
    void 이미_종료한_세션의_재호출은_상태와_이벤트를_바꾸지_않는다() {
        FieldSession session = session();
        Instant firstEndedAt = NOW.minusSeconds(60);
        session.endByLeader(firstEndedAt, LEADER_ID);
        when(sessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));

        var result = service.close(STUDY_ID, LEADER_ID, request(true));

        assertThat(result.responseCode())
                .isEqualTo(FieldVisitResponseCode.FIELD_VISIT_ALREADY_CLOSED);
        assertThat(result.body().forcedEndedCount()).isZero();
        assertThat(session.getEndedAt()).isEqualTo(firstEndedAt);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 전원_자율_종료된_세션은_종료_주체가_null이다() {
        FieldSession session = session();
        session.endByAllParticipants(NOW.minusSeconds(60));
        when(sessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));

        var result = service.close(STUDY_ID, LEADER_ID, request(true));

        assertThat(result.body().session().endedByMemberId()).isNull();
        assertThat(result.body().session().endReason()).isEqualTo("ALL_ENDED");
    }

    @Test
    void 기존_리포트가_있으면_ID와_상태를_반환한다() {
        FieldSession session = session();
        session.endByLeader(NOW.minusSeconds(60), LEADER_ID);
        when(sessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));
        Report report = mock(Report.class);
        when(report.getId()).thenReturn(48L);
        when(report.getStatus()).thenReturn(ReportStatus.IN_PROGRESS);
        when(reportRepository.findByStudyId(STUDY_ID))
                .thenReturn(Optional.of(report));

        var result = service.close(STUDY_ID, LEADER_ID, request(true));

        assertThat(result.body().reportId()).isEqualTo(48L);
        assertThat(result.body().reportStatus()).isEqualTo("IN_PROGRESS");
    }

    private void stubSession(
            FieldSession session,
            List<FieldParticipant> inProgress
    ) {
        when(sessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));
        when(participantRepository.findInProgressBySessionIdForUpdate(100L))
                .thenReturn(inProgress);
    }

    private static FieldVisitCloseRequestBody request(Boolean confirmed) {
        return new FieldVisitCloseRequestBody(confirmed, "request-1");
    }

    private static Study study() {
        Study study = Study.create(
                25L,
                LEADER_ID,
                "스터디",
                null,
                "목표",
                5,
                StudyPurpose.INVESTMENT
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

    private static FieldParticipant participant(Long id, Long memberId) {
        FieldParticipant participant = FieldParticipant.start(
                100L, memberId, NOW.minusSeconds(3600)
        );
        ReflectionTestUtils.setField(participant, "id", id);
        return participant;
    }

    private static Report report(Long id) {
        Report report = Report.create(STUDY_ID, 100L, 25L);
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }
}
