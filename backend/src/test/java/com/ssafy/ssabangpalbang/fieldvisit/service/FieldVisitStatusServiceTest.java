package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitCloseVoteStatusBody;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistAnswerRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldVisitCandidateRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordCountRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.domain.Schedule;
import com.ssafy.ssabangpalbang.study.domain.ScheduleStatus;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.ScheduleRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FieldVisitStatusServiceTest {

    private static final Long STUDY_ID = 7L;
    private static final Long MEMBER_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-06-17T09:00:00Z");

    @Mock private StudyRepository studyRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private StudyMemberRepository studyMemberRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private FieldSessionRepository fieldSessionRepository;
    @Mock private FieldVisitCandidateRepository fieldVisitCandidateRepository;
    @Mock private FieldParticipantRepository fieldParticipantRepository;
    @Mock private ChecklistRepository checklistRepository;
    @Mock private ChecklistItemRepository checklistItemRepository;
    @Mock private ChecklistAnswerRepository checklistAnswerRepository;
    @Mock private FieldRecordCountRepository fieldRecordCountRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private FieldVisitCloseVoteService closeVoteService;
    @Mock private Clock clock;

    @InjectMocks
    private FieldVisitStatusService service;

    private Study study;

    @BeforeEach
    void setUp() {
        study = Study.create(25L, MEMBER_ID, "t", null, "g", 4, null);
        ReflectionTestUtils.setField(study, "id", STUDY_ID);
        study.closeRecruitment(NOW);
        when(clock.instant()).thenReturn(NOW);
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(activeMember()));
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.of(StudyMember.createLeader(STUDY_ID, MEMBER_ID)));
        when(reportRepository.existsByStudyId(STUDY_ID)).thenReturn(false);
        when(closeVoteService.buildStatus(any(), any()))
                .thenReturn(new FieldVisitCloseVoteStatusBody(0, 0, 0, false, false));
    }

    @Test
    void 세션_없으면_NOT_STARTED이고_canStart_true다() {
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.empty());
        when(scheduleRepository.findByStudyIdAndStatus(STUDY_ID, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.of(Schedule.create(
                        STUDY_ID, NOW.minusSeconds(60), NOW.plusSeconds(60), "gate"
                )));

        FieldVisitStatusService.StatusResult result =
                service.getStatus(STUDY_ID, MEMBER_ID);

        assertThat(result.responseCode())
                .isEqualTo(FieldVisitResponseCode.FIELD_VISIT_STATUS_SUCCESS);
        assertThat(result.body().status()).isEqualTo("NOT_STARTED");
        assertThat(result.body().session()).isNull();
        assertThat(result.body().participant()).isNull();
        assertThat(result.body().permissions().canStart()).isTrue();
        assertThat(result.body().permissions().canFinish()).isFalse();
    }

    @Test
    void 세션_진행중_본인_미시작이면_canStart_true다() {
        ReflectionTestUtils.setField(study, "status", StudyStatus.IN_PROGRESS);
        FieldSession session = FieldSession.start(STUDY_ID, NOW.minusSeconds(100));
        ReflectionTestUtils.setField(session, "id", 100L);
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberId(100L, MEMBER_ID))
                .thenReturn(Optional.empty());
        when(fieldVisitCandidateRepository.existsBySessionIdAndMemberId(
                100L, MEMBER_ID
        )).thenReturn(true);
        when(scheduleRepository.findByStudyIdAndStatus(STUDY_ID, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.of(Schedule.create(
                        STUDY_ID, NOW.minusSeconds(60), NOW.plusSeconds(60), "gate"
                )));

        FieldVisitStatusService.StatusResult result =
                service.getStatus(STUDY_ID, MEMBER_ID);

        assertThat(result.body().status()).isEqualTo("IN_PROGRESS");
        assertThat(result.body().participant()).isNull();
        assertThat(result.body().permissions().canStart()).isTrue();
        assertThat(result.body().session().elapsedSeconds()).isEqualTo(100);
    }

    @Test
    void 세션이_있으면_상태별_참여자_수를_함께_내려준다() {
        ReflectionTestUtils.setField(study, "status", StudyStatus.IN_PROGRESS);
        FieldSession session = FieldSession.start(STUDY_ID, NOW.minusSeconds(100));
        ReflectionTestUtils.setField(session, "id", 100L);
        FieldParticipant participant = FieldParticipant.start(100L, MEMBER_ID, NOW.minusSeconds(30));
        ReflectionTestUtils.setField(participant, "id", 301L);
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberId(100L, MEMBER_ID))
                .thenReturn(Optional.of(participant));
        when(fieldParticipantRepository.countBySessionIdAndStatus(
                100L, FieldParticipantStatus.IN_PROGRESS
        )).thenReturn(2L);
        when(fieldParticipantRepository.countBySessionIdAndStatus(
                100L, FieldParticipantStatus.ENDED
        )).thenReturn(1L);
        when(scheduleRepository.findByStudyIdAndStatus(STUDY_ID, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.empty());
        when(checklistRepository.findBySessionIdAndMemberId(100L, MEMBER_ID))
                .thenReturn(Optional.empty());

        FieldVisitStatusService.StatusResult result =
                service.getStatus(STUDY_ID, MEMBER_ID);

        assertThat(result.body().session().activeParticipantCount()).isEqualTo(2);
        assertThat(result.body().session().endedParticipantCount()).isEqualTo(1);
    }

    @Test
    void 약속_시각_1초_전이면_canStart_false다() {
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.empty());
        when(scheduleRepository.findByStudyIdAndStatus(STUDY_ID, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.of(Schedule.create(
                        STUDY_ID, NOW.plusSeconds(1), NOW.plusSeconds(3600), "gate"
                )));

        FieldVisitStatusService.StatusResult result =
                service.getStatus(STUDY_ID, MEMBER_ID);

        assertThat(result.body().permissions().canStart()).isFalse();
    }

    @Test
    void 약속_시각_정각이면_canStart_true다() {
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.empty());
        when(scheduleRepository.findByStudyIdAndStatus(STUDY_ID, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.of(Schedule.create(
                        STUDY_ID, NOW, NOW.plusSeconds(3600), "gate"
                )));

        FieldVisitStatusService.StatusResult result =
                service.getStatus(STUDY_ID, MEMBER_ID);

        assertThat(result.body().permissions().canStart()).isTrue();
    }

    @Test
    void 약속_시각_1초_후면_canStart_true다() {
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.empty());
        when(scheduleRepository.findByStudyIdAndStatus(STUDY_ID, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.of(Schedule.create(
                        STUDY_ID, NOW.minusSeconds(1), NOW.plusSeconds(3600), "gate"
                )));

        FieldVisitStatusService.StatusResult result =
                service.getStatus(STUDY_ID, MEMBER_ID);

        assertThat(result.body().permissions().canStart()).isTrue();
    }

    @Test
    void 세션_시작_후_고정_후보가_아니면_canStart_false다() {
        ReflectionTestUtils.setField(study, "status", StudyStatus.IN_PROGRESS);
        FieldSession session = FieldSession.start(STUDY_ID, Instant.now());
        ReflectionTestUtils.setField(session, "id", 100L);
        when(fieldSessionRepository.findByStudyId(STUDY_ID))
                .thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberId(100L, MEMBER_ID))
                .thenReturn(Optional.empty());
        when(fieldVisitCandidateRepository.existsBySessionIdAndMemberId(
                100L, MEMBER_ID
        )).thenReturn(false);

        FieldVisitStatusService.StatusResult result =
                service.getStatus(STUDY_ID, MEMBER_ID);

        assertThat(result.body().permissions().canStart()).isFalse();
    }

    @Test
    void 본인_진행중이면_쓰기_권한과_canFinish가_true다() {
        ReflectionTestUtils.setField(study, "status", StudyStatus.IN_PROGRESS);
        FieldSession session = FieldSession.start(STUDY_ID, NOW);
        ReflectionTestUtils.setField(session, "id", 100L);
        FieldParticipant participant = FieldParticipant.start(
                100L, MEMBER_ID, NOW.minusSeconds(30)
        );
        ReflectionTestUtils.setField(participant, "id", 301L);
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberId(100L, MEMBER_ID))
                .thenReturn(Optional.of(participant));
        when(scheduleRepository.findByStudyIdAndStatus(STUDY_ID, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.empty());
        when(checklistRepository.findBySessionIdAndMemberId(100L, MEMBER_ID))
                .thenReturn(Optional.empty());

        FieldVisitStatusService.StatusResult result =
                service.getStatus(STUDY_ID, MEMBER_ID);

        assertThat(result.body().permissions().canStart()).isFalse();
        assertThat(result.body().permissions().canEditChecklist()).isTrue();
        assertThat(result.body().permissions().canCreateRecord()).isTrue();
        assertThat(result.body().permissions().canFinish()).isTrue();
        assertThat(result.body().permissions().canCloseSession()).isTrue();
        assertThat(result.body().participant().stayDurationSec()).isEqualTo(30);
    }

    @Test
    void 비멤버는_ACCESS_DENIED다() {
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatus(STUDY_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_ACCESS_DENIED);
    }

    @Test
    void 탈퇴한_회원은_ACTIVE_리더_관계가_남아도_ACCESS_DENIED다() {
        Member withdrawn = activeMember();
        withdrawn.withdraw(Instant.now());
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.getStatus(STUDY_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_ACCESS_DENIED);
    }

    @Test
    void 세션_ENDED면_canStart_false다() {
        FieldSession session = FieldSession.start(STUDY_ID, Instant.now());
        ReflectionTestUtils.setField(session, "id", 100L);
        ReflectionTestUtils.setField(session, "status", FieldSessionStatus.ENDED);
        ReflectionTestUtils.setField(session, "endedAt", Instant.now());
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberId(100L, MEMBER_ID))
                .thenReturn(Optional.empty());
        when(scheduleRepository.findByStudyIdAndStatus(STUDY_ID, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.empty());

        FieldVisitStatusService.StatusResult result =
                service.getStatus(STUDY_ID, MEMBER_ID);

        assertThat(result.body().status()).isEqualTo("ENDED");
        assertThat(result.body().permissions().canStart()).isFalse();
    }

    @Test
    void 종료_참여자는_저장된_stayDuration을_반환한다() {
        ReflectionTestUtils.setField(study, "status", StudyStatus.IN_PROGRESS);
        FieldSession session = FieldSession.start(STUDY_ID, Instant.now());
        ReflectionTestUtils.setField(session, "id", 100L);
        FieldParticipant participant = FieldParticipant.start(100L, MEMBER_ID, Instant.now());
        ReflectionTestUtils.setField(participant, "id", 301L);
        ReflectionTestUtils.setField(participant, "status", FieldParticipantStatus.ENDED);
        ReflectionTestUtils.setField(participant, "endedAt", Instant.now());
        ReflectionTestUtils.setField(participant, "endReason", "SELF_ENDED");
        ReflectionTestUtils.setField(participant, "stayDurationSec", 2760);
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberId(100L, MEMBER_ID))
                .thenReturn(Optional.of(participant));
        when(scheduleRepository.findByStudyIdAndStatus(STUDY_ID, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.empty());
        when(checklistRepository.findBySessionIdAndMemberId(100L, MEMBER_ID))
                .thenReturn(Optional.empty());

        FieldVisitStatusService.StatusResult result =
                service.getStatus(STUDY_ID, MEMBER_ID);

        assertThat(result.body().participant().stayDurationSec()).isEqualTo(2760);
        assertThat(result.body().participant().endReason()).isEqualTo("SELF_ENDED");
        assertThat(result.body().permissions().canFinish()).isFalse();
        assertThat(result.body().permissions().canEditChecklist()).isFalse();
    }

    @Test
    void 취소된_스터디는_진행중_세션과_참여자가_있어도_모든_쓰기_권한이_false다() {
        ReflectionTestUtils.setField(study, "status", StudyStatus.CANCELED);
        FieldSession session = FieldSession.start(STUDY_ID, Instant.now());
        ReflectionTestUtils.setField(session, "id", 100L);
        FieldParticipant participant = FieldParticipant.start(
                100L, MEMBER_ID, Instant.now()
        );
        ReflectionTestUtils.setField(participant, "id", 301L);
        when(fieldSessionRepository.findByStudyId(STUDY_ID))
                .thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberId(
                100L, MEMBER_ID
        )).thenReturn(Optional.of(participant));
        when(fieldVisitCandidateRepository.existsBySessionIdAndMemberId(
                100L, MEMBER_ID
        )).thenReturn(true);
        when(checklistRepository.findBySessionIdAndMemberId(100L, MEMBER_ID))
                .thenReturn(Optional.empty());

        FieldVisitStatusService.StatusResult result =
                service.getStatus(STUDY_ID, MEMBER_ID);

        assertThat(result.body().permissions().canStart()).isFalse();
        assertThat(result.body().permissions().canEditChecklist()).isFalse();
        assertThat(result.body().permissions().canCreateRecord()).isFalse();
        assertThat(result.body().permissions().canFinish()).isFalse();
        assertThat(result.body().permissions().canCloseSession()).isFalse();
    }

    private Member activeMember() {
        Member member = new Member("member@example.com", "hash", "member");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }
}
