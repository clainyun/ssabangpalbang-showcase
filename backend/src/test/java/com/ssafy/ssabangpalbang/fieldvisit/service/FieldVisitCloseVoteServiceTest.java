package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitCloseVote;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitCloseVoteStatusBody;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldVisitCloseVoteRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FieldVisitCloseVoteServiceTest {

    private static final Long STUDY_ID = 7L;
    private static final Long MEMBER_ID = 42L;
    private static final Long SESSION_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-06-17T09:00:00Z");

    @Mock private FieldVisitAccessService accessService;
    @Mock private FieldSessionRepository fieldSessionRepository;
    @Mock private FieldParticipantRepository fieldParticipantRepository;
    @Mock private FieldVisitCloseVoteRepository closeVoteRepository;
    @Mock private FieldVisitSessionCloser sessionCloser;
    @Mock private ReportRepository reportRepository;
    @Mock private Clock clock;

    @InjectMocks
    private FieldVisitCloseVoteService service;

    private Study study;
    private FieldSession session;

    @BeforeEach
    void setUp() {
        study = Study.create(25L, MEMBER_ID, "t", null, "g", 4, null);
        ReflectionTestUtils.setField(study, "id", STUDY_ID);
        ReflectionTestUtils.setField(study, "status",
                com.ssafy.ssabangpalbang.study.domain.StudyStatus.IN_PROGRESS);
        session = FieldSession.start(STUDY_ID, NOW.minusSeconds(100));
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        when(clock.instant()).thenReturn(NOW);
        when(accessService.requireStudy(STUDY_ID)).thenReturn(study);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1",
            "2, 2",
            "3, 2",
            "4, 3",
            "5, 3"
    })
    void requiredVotes_계산이_정확하다(long started, int required) {
        assertThat(FieldVisitCloseVoteService.requiredVotes(started))
                .isEqualTo(required);
    }

    @Test
    void 비멤버는_투표할_수_없다() {
        doThrow(new BusinessException(ErrorCode.FIELD_VISIT_CLOSE_VOTE_FORBIDDEN))
                .when(accessService)
                .requireActiveStudyMember(
                        STUDY_ID,
                        MEMBER_ID,
                        ErrorCode.FIELD_VISIT_CLOSE_VOTE_FORBIDDEN
                );

        assertThatThrownBy(() -> service.vote(STUDY_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_CLOSE_VOTE_FORBIDDEN);
        verify(fieldSessionRepository, never()).findByStudyIdForUpdate(any());
    }

    @Test
    void participant가_없으면_투표할_수_없다() {
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberIdForUpdate(
                SESSION_ID, MEMBER_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.vote(STUDY_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_CLOSE_VOTE_FORBIDDEN);
        verify(closeVoteRepository, never()).saveAndFlush(any());
    }

    @Test
    void 시작_참여자_1명_1표면_세션을_종료한다() {
        FieldParticipant participant = participant(301L, MEMBER_ID);
        stubVotePath(participant, 1L, 1L, List.of(participant), true);

        FieldVisitCloseVoteService.VoteResult result = service.vote(STUDY_ID, MEMBER_ID);

        assertThat(result.responseCode()).isEqualTo(
                FieldVisitResponseCode.FIELD_VISIT_CLOSE_VOTE_AND_SESSION_END_SUCCESS
        );
        assertThat(result.body().sessionEnded()).isTrue();
        assertThat(result.body().sessionEndReason()).isEqualTo("MAJORITY_FORCED");
        assertThat(result.body().requiredVoteCount()).isEqualTo(1);
        assertThat(result.body().reportTriggered()).isTrue();
        assertThat(participant.getStatus()).isEqualTo(FieldParticipantStatus.ENDED);
        assertThat(participant.getEndReason()).isEqualTo("MAJORITY_FORCED");
        verify(sessionCloser).endByMajority(session, study, NOW);
    }

    @Test
    void 시작_참여자_2명_1표면_세션을_유지한다() {
        FieldParticipant participant = participant(301L, MEMBER_ID);
        stubVotePath(participant, 2L, 1L, List.of(), false);

        FieldVisitCloseVoteService.VoteResult result = service.vote(STUDY_ID, MEMBER_ID);

        assertThat(result.responseCode()).isEqualTo(
                FieldVisitResponseCode.FIELD_VISIT_CLOSE_VOTE_SUCCESS
        );
        assertThat(result.body().sessionEnded()).isFalse();
        assertThat(result.body().voteCount()).isEqualTo(1);
        assertThat(result.body().requiredVoteCount()).isEqualTo(2);
        verify(sessionCloser, never()).endByMajority(any(), any(), any());
    }

    @Test
    void 시작_참여자_2명_2표면_세션을_종료한다() {
        FieldParticipant participant = participant(301L, MEMBER_ID);
        stubVotePath(participant, 2L, 2L, List.of(participant), true);

        FieldVisitCloseVoteService.VoteResult result = service.vote(STUDY_ID, MEMBER_ID);

        assertThat(result.body().sessionEnded()).isTrue();
        assertThat(result.body().requiredVoteCount()).isEqualTo(2);
        verify(sessionCloser).endByMajority(eq(session), eq(study), eq(NOW));
    }

    @Test
    void 시작_참여자_3명_1표면_세션을_유지한다() {
        FieldParticipant participant = participant(301L, MEMBER_ID);
        stubVotePath(participant, 3L, 1L, List.of(), false);

        FieldVisitCloseVoteService.VoteResult result = service.vote(STUDY_ID, MEMBER_ID);

        assertThat(result.body().sessionEnded()).isFalse();
        assertThat(result.body().requiredVoteCount()).isEqualTo(2);
        verify(sessionCloser, never()).endByMajority(any(), any(), any());
    }

    @Test
    void 시작_참여자_3명_2표면_세션을_종료한다() {
        FieldParticipant participant = participant(301L, MEMBER_ID);
        stubVotePath(participant, 3L, 2L, List.of(), true);

        FieldVisitCloseVoteService.VoteResult result = service.vote(STUDY_ID, MEMBER_ID);

        assertThat(result.body().sessionEnded()).isTrue();
        assertThat(result.body().requiredVoteCount()).isEqualTo(2);
        verify(sessionCloser).endByMajority(eq(session), eq(study), eq(NOW));
    }

    @Test
    void 시작_참여자_4명_2표면_세션을_유지한다() {
        FieldParticipant participant = participant(301L, MEMBER_ID);
        stubVotePath(participant, 4L, 2L, List.of(), false);

        FieldVisitCloseVoteService.VoteResult result = service.vote(STUDY_ID, MEMBER_ID);

        assertThat(result.body().sessionEnded()).isFalse();
        assertThat(result.body().requiredVoteCount()).isEqualTo(3);
    }

    @Test
    void 시작_참여자_4명_3표면_세션을_종료한다() {
        FieldParticipant participant = participant(301L, MEMBER_ID);
        stubVotePath(participant, 4L, 3L, List.of(participant), true);

        FieldVisitCloseVoteService.VoteResult result = service.vote(STUDY_ID, MEMBER_ID);

        assertThat(result.body().sessionEnded()).isTrue();
        assertThat(result.body().requiredVoteCount()).isEqualTo(3);
        verify(sessionCloser).endByMajority(session, study, NOW);
    }

    @Test
    void ENDED_참여자도_session_진행중이면_투표한다() {
        FieldParticipant participant = participant(301L, MEMBER_ID);
        ReflectionTestUtils.setField(participant, "status", FieldParticipantStatus.ENDED);
        stubVotePath(participant, 2L, 1L, List.of(), false);

        FieldVisitCloseVoteService.VoteResult result = service.vote(STUDY_ID, MEMBER_ID);

        assertThat(result.body().hasVoted()).isTrue();
        ArgumentCaptor<FieldVisitCloseVote> captor =
                ArgumentCaptor.forClass(FieldVisitCloseVote.class);
        verify(closeVoteRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getParticipantId()).isEqualTo(301L);
    }

    @Test
    void 중복_투표는_표를_늘리지_않는다() {
        FieldParticipant participant = participant(301L, MEMBER_ID);
        stubOpenSession();
        when(fieldParticipantRepository.findBySessionIdAndMemberIdForUpdate(
                SESSION_ID, MEMBER_ID
        )).thenReturn(Optional.of(participant));
        when(closeVoteRepository.existsBySessionIdAndParticipantId(SESSION_ID, 301L))
                .thenReturn(true);
        when(fieldParticipantRepository.countBySessionId(SESSION_ID)).thenReturn(2L);
        when(closeVoteRepository.countBySessionId(SESSION_ID)).thenReturn(1L);

        FieldVisitCloseVoteService.VoteResult result = service.vote(STUDY_ID, MEMBER_ID);

        assertThat(result.body().voteCount()).isEqualTo(1);
        verify(closeVoteRepository, never()).saveAndFlush(any());
        verify(sessionCloser, never()).endByMajority(any(), any(), any());
    }

    @Test
    void 이미_종료된_세션은_추가_투표_없이_상태를_반환한다() {
        ReflectionTestUtils.setField(session, "status", FieldSessionStatus.ENDED);
        ReflectionTestUtils.setField(session, "endReason", "LEADER_FORCED");
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));
        FieldParticipant participant = participant(301L, MEMBER_ID);
        when(fieldParticipantRepository.findBySessionIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(participant));
        when(fieldParticipantRepository.countBySessionId(SESSION_ID)).thenReturn(2L);
        when(closeVoteRepository.countBySessionId(SESSION_ID)).thenReturn(1L);
        when(closeVoteRepository.existsBySessionIdAndParticipantId(SESSION_ID, 301L))
                .thenReturn(true);

        FieldVisitCloseVoteService.VoteResult result = service.vote(STUDY_ID, MEMBER_ID);

        assertThat(result.responseCode()).isEqualTo(
                FieldVisitResponseCode.FIELD_VISIT_ALREADY_CLOSED
        );
        assertThat(result.body().sessionEnded()).isTrue();
        assertThat(result.body().canVote()).isFalse();
        verify(closeVoteRepository, never()).saveAndFlush(any());
    }

    @Test
    void buildStatus_세션_없으면_모두_0_false다() {
        FieldVisitCloseVoteStatusBody body = service.buildStatus(null, null);

        assertThat(body.startedParticipantCount()).isZero();
        assertThat(body.voteCount()).isZero();
        assertThat(body.requiredVoteCount()).isZero();
        assertThat(body.hasVoted()).isFalse();
        assertThat(body.canVote()).isFalse();
    }

    @Test
    void buildStatus_participant_없으면_조회만_가능하고_투표_불가다() {
        when(fieldParticipantRepository.countBySessionId(SESSION_ID)).thenReturn(3L);
        when(closeVoteRepository.countBySessionId(SESSION_ID)).thenReturn(1L);

        FieldVisitCloseVoteStatusBody body = service.buildStatus(session, null);

        assertThat(body.startedParticipantCount()).isEqualTo(3);
        assertThat(body.voteCount()).isEqualTo(1);
        assertThat(body.requiredVoteCount()).isEqualTo(2);
        assertThat(body.hasVoted()).isFalse();
        assertThat(body.canVote()).isFalse();
    }

    @Test
    void buildStatus_미투표_참여자는_canVote_true다() {
        FieldParticipant participant = participant(301L, MEMBER_ID);
        when(fieldParticipantRepository.countBySessionId(SESSION_ID)).thenReturn(2L);
        when(closeVoteRepository.countBySessionId(SESSION_ID)).thenReturn(0L);
        when(closeVoteRepository.existsBySessionIdAndParticipantId(SESSION_ID, 301L))
                .thenReturn(false);

        FieldVisitCloseVoteStatusBody body = service.buildStatus(session, participant);

        assertThat(body.canVote()).isTrue();
        assertThat(body.hasVoted()).isFalse();
        assertThat(body.requiredVoteCount()).isEqualTo(2);
    }

    @Test
    void buildStatus_이미_투표하면_canVote_false다() {
        FieldParticipant participant = participant(301L, MEMBER_ID);
        when(fieldParticipantRepository.countBySessionId(SESSION_ID)).thenReturn(2L);
        when(closeVoteRepository.countBySessionId(SESSION_ID)).thenReturn(1L);
        when(closeVoteRepository.existsBySessionIdAndParticipantId(SESSION_ID, 301L))
                .thenReturn(true);

        FieldVisitCloseVoteStatusBody body = service.buildStatus(session, participant);

        assertThat(body.canVote()).isFalse();
        assertThat(body.hasVoted()).isTrue();
    }

    private void stubVotePath(
            FieldParticipant participant,
            long startedCount,
            long voteCount,
            List<FieldParticipant> inProgress,
            boolean endSession
    ) {
        stubOpenSession();
        when(fieldParticipantRepository.findBySessionIdAndMemberIdForUpdate(
                SESSION_ID, MEMBER_ID
        )).thenReturn(Optional.of(participant));
        when(closeVoteRepository.existsBySessionIdAndParticipantId(
                SESSION_ID, participant.getId()
        )).thenReturn(false);
        when(closeVoteRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fieldParticipantRepository.countBySessionId(SESSION_ID)).thenReturn(startedCount);
        when(closeVoteRepository.countBySessionId(SESSION_ID)).thenReturn(voteCount);
        when(fieldParticipantRepository.findInProgressBySessionIdForUpdate(SESSION_ID))
                .thenReturn(inProgress);
        if (endSession) {
            Report report = Report.create(STUDY_ID, SESSION_ID, 25L);
            ReflectionTestUtils.setField(report, "id", 48L);
            when(reportRepository.findByStudyId(STUDY_ID))
                    .thenReturn(Optional.of(report));
            when(sessionCloser.endByMajority(session, study, NOW)).thenAnswer(inv -> {
                session.endByMajority(NOW);
                return true;
            });
        }
    }

    private void stubOpenSession() {
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));
    }

    private FieldParticipant participant(Long id, Long memberId) {
        FieldParticipant participant = FieldParticipant.start(
                SESSION_ID, memberId, NOW.minusSeconds(50)
        );
        ReflectionTestUtils.setField(participant, "id", id);
        return participant;
    }
}
