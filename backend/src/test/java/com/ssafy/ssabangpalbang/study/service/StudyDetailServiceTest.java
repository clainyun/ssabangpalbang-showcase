package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.*;
import com.ssafy.ssabangpalbang.study.dto.response.StudyDetailResponse;
import com.ssafy.ssabangpalbang.study.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyDetailServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-17T09:00:00Z");

    @Mock StudyRepository studyRepository;
    @Mock StudyMemberRepository studyMemberRepository;
    @Mock MemberRepository memberRepository;
    @Mock ApartmentRepository apartmentRepository;
    @Mock StudyApplicationRepository applicationRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock StudyDetailQueryRepository queryRepository;
    @Mock Clock clock;
    @InjectMocks StudyService service;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void rejectsMissingOrDeletedStudyThroughSoftDeleteQuery() {
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getDetail(7L, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STUDY_NOT_FOUND));
        verify(studyRepository).findByIdAndDeletedAtIsNull(10L);
    }

    @Test
    void nonMemberGetsRecruitingFieldsOnlyAndNoPrivateQueries() {
        stubCommon(StudyStatus.RECRUITING, 1, null);
        when(applicationRepository.findByStudyIdAndApplicantId(10L, 20L))
                .thenReturn(Optional.empty());

        StudyDetailResponse response = service.getDetail(20L, 10L);

        assertThat(response.myParticipationStatus()).isEqualTo("NONE");
        assertThat(response.isMember()).isFalse();
        assertThat(response.canApply()).isTrue();
        assertThat(response.goal()).isNull();
        assertThat(response.memberSummary()).isNull();
        assertThat(response.unreadChatCount()).isNull();
        assertThat(response.fieldVisitStatus()).isNull();
        assertThat(response.canStartFieldVisit()).isFalse();
        verifyNoInteractions(queryRepository);
    }

    @Test
    void pendingApplicantCannotApply() {
        stubCommon(StudyStatus.RECRUITING, 1, null);
        StudyApplication application = entity(StudyApplication.class);
        ReflectionTestUtils.setField(application, "status", StudyApplicationStatus.PENDING);
        when(applicationRepository.findByStudyIdAndApplicantId(10L, 20L))
                .thenReturn(Optional.of(application));

        StudyDetailResponse response = service.getDetail(20L, 10L);
        assertThat(response.myParticipationStatus()).isEqualTo("PENDING");
        assertThat(response.canApply()).isFalse();
    }

    @Test
    void activeLeaderIsBothLeaderAndMemberWithPermissions() {
        StudyMember leaderMembership = studyMember(7L, StudyMemberRole.LEADER, 1);
        stubCommon(StudyStatus.CLOSED, 1, leaderMembership);
        when(studyMemberRepository.findByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(leaderMembership));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(7L)));
        when(queryRepository.countUnreadChat(10L, 7L)).thenReturn(3L);
        when(queryRepository.findFieldSessionReport(10L)).thenReturn(Optional.empty());

        StudyDetailResponse response = service.getDetail(7L, 10L);
        assertThat(response.isLeader()).isTrue();
        assertThat(response.isMember()).isTrue();
        assertThat(response.myParticipationStatus()).isEqualTo("APPROVED");
        assertThat(response.fieldVisitStatus()).isEqualTo("NOT_STARTED");
        assertThat(response.permissions().canManageApplications()).isTrue();
        assertThat(response.permissions().canUseChat()).isTrue();
        assertThat(response.canStartFieldVisit()).isFalse();
    }

    @Test
    void canStartFieldVisitIsFalseOneSecondBeforeScheduleStartAt() {
        StudyMember membership = studyMember(20L, StudyMemberRole.MEMBER, 1);
        stubCommon(StudyStatus.CLOSED, 2, membership);
        stubSchedule(NOW.plusSeconds(1));
        when(studyMemberRepository.findByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(membership));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(20L)));
        when(queryRepository.findFieldSessionReport(10L)).thenReturn(Optional.empty());

        assertThat(service.getDetail(20L, 10L).canStartFieldVisit()).isFalse();
    }

    @Test
    void canStartFieldVisitIsTrueAtExactScheduleStartAt() {
        StudyMember membership = studyMember(20L, StudyMemberRole.MEMBER, 1);
        stubCommon(StudyStatus.CLOSED, 2, membership);
        stubSchedule(NOW);
        when(studyMemberRepository.findByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(membership));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(20L)));
        when(queryRepository.findFieldSessionReport(10L)).thenReturn(Optional.empty());

        assertThat(service.getDetail(20L, 10L).canStartFieldVisit()).isTrue();
    }

    @Test
    void canStartFieldVisitIsTrueOneSecondAfterScheduleStartAt() {
        StudyMember membership = studyMember(20L, StudyMemberRole.MEMBER, 1);
        stubCommon(StudyStatus.CLOSED, 2, membership);
        stubSchedule(NOW.minusSeconds(1));
        when(studyMemberRepository.findByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(membership));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(20L)));
        when(queryRepository.findFieldSessionReport(10L)).thenReturn(Optional.empty());

        assertThat(service.getDetail(20L, 10L).canStartFieldVisit()).isTrue();
    }

    @Test
    void canStartFieldVisitRemainsFalseForNonMemberAfterScheduleStartAt() {
        stubCommon(StudyStatus.CLOSED, 1, null);
        stubSchedule(NOW.minusSeconds(60));
        when(applicationRepository.findByStudyIdAndApplicantId(10L, 20L))
                .thenReturn(Optional.empty());

        assertThat(service.getDetail(20L, 10L).canStartFieldVisit()).isFalse();
    }

    @Test
    void canStartFieldVisitRemainsFalseWhenFieldVisitEndedAfterScheduleStartAt() {
        StudyMember membership = studyMember(20L, StudyMemberRole.MEMBER, 1);
        stubCommon(StudyStatus.CLOSED, 2, membership);
        stubSchedule(NOW.minusSeconds(60));
        when(studyMemberRepository.findByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(membership));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(20L)));
        when(queryRepository.findFieldSessionReport(10L)).thenReturn(Optional.of(
                new StudyDetailQueryRepository.FieldSessionReportRow(
                        100L, "ENDED", 48L, "PENDING"
                )
        ));

        StudyDetailResponse response = service.getDetail(20L, 10L);
        assertThat(response.canStartFieldVisit()).isFalse();
        assertThat(response.fieldSessionId()).isEqualTo(100L);
        assertThat(response.report()).isEqualTo(
                new StudyDetailResponse.ReportSummary(48L, "PENDING")
        );
    }

    @Test
    void canStartFieldVisitRemainsFalseForRecruitingEvenAfterScheduleStartAt() {
        StudyMember membership = studyMember(20L, StudyMemberRole.MEMBER, 1);
        stubCommon(StudyStatus.RECRUITING, 2, membership);
        stubSchedule(NOW.minusSeconds(60));
        when(studyMemberRepository.findByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(membership));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(20L)));
        when(queryRepository.findFieldSessionReport(10L)).thenReturn(Optional.empty());

        assertThat(service.getDetail(20L, 10L).canStartFieldVisit()).isFalse();
    }

    @Test
    void canceledMemberIsReadOnlyAndCannotUseChat() {
        StudyMember membership = studyMember(20L, StudyMemberRole.MEMBER, 2);
        stubCommon(StudyStatus.CANCELED, 1, membership);
        when(studyMemberRepository.findByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(membership));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(20L)));
        when(queryRepository.findFieldSessionReport(10L)).thenReturn(Optional.of(
                new StudyDetailQueryRepository.FieldSessionReportRow(
                        100L, "ENDED", 48L, "DONE"
                )
        ));

        StudyDetailResponse response = service.getDetail(20L, 10L);
        assertThat(response.readOnly()).isTrue();
        assertThat(response.permissions().canUseChat()).isFalse();
        assertThat(response.permissions().canUseFieldVisit()).isFalse();
    }

    @Test
    void scheduleUsesSeoulOffsetAndAllowsNullEndAt() {
        Schedule schedule = entity(Schedule.class);
        ReflectionTestUtils.setField(schedule, "id", 5L);
        ReflectionTestUtils.setField(schedule, "startAt", Instant.parse("2026-07-27T06:00:00Z"));
        ReflectionTestUtils.setField(schedule, "endAt", null);
        stubCommon(StudyStatus.RECRUITING, 1, null);
        when(scheduleRepository.findByStudyIdAndStatus(10L, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.of(schedule));
        when(applicationRepository.findByStudyIdAndApplicantId(10L, 20L))
                .thenReturn(Optional.empty());

        StudyDetailResponse response = service.getDetail(20L, 10L);
        assertThat(response.nextSchedule().startAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(response.nextSchedule().endAt()).isNull();
    }

    @Test
    void memberSummaryPutsLeaderFirst() {
        StudyMember member = studyMember(20L, StudyMemberRole.MEMBER, 1);
        StudyMember leader = studyMember(7L, StudyMemberRole.LEADER, 2);
        stubCommon(StudyStatus.CLOSED, 2, member);
        when(studyMemberRepository.findByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(member, leader));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(20L), member(7L)));
        when(queryRepository.findFieldSessionReport(10L)).thenReturn(Optional.empty());

        StudyDetailResponse response = service.getDetail(20L, 10L);

        assertThat(response.memberSummary())
                .extracting(StudyDetailResponse.MemberSummary::role)
                .containsExactly("LEADER", "MEMBER");
    }

    @Test
    void cannotApplyWhenCapacityIsFull() {
        stubCommon(StudyStatus.RECRUITING, 6, null);
        when(applicationRepository.findByStudyIdAndApplicantId(10L, 20L))
                .thenReturn(Optional.empty());

        assertThat(service.getDetail(20L, 10L).canApply()).isFalse();
    }

    @Test
    void leaderCannotApplyAndKeepsInProgressFieldStatus() {
        StudyMember leaderMembership = studyMember(7L, StudyMemberRole.LEADER, 1);
        stubCommon(StudyStatus.RECRUITING, 1, leaderMembership);
        when(studyMemberRepository.findByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(leaderMembership));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(7L)));
        when(queryRepository.findFieldSessionReport(10L)).thenReturn(Optional.of(
                new StudyDetailQueryRepository.FieldSessionReportRow(
                        100L, "IN_PROGRESS", null, null
                )
        ));

        StudyDetailResponse response = service.getDetail(7L, 10L);

        assertThat(response.canApply()).isFalse();
        assertThat(response.fieldVisitStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void activeMemberCannotApplyEvenWithoutApplicationRecord() {
        StudyMember membership = studyMember(20L, StudyMemberRole.MEMBER, 1);
        stubCommon(StudyStatus.RECRUITING, 2, membership);
        when(studyMemberRepository.findByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(membership));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(20L)));
        when(queryRepository.findFieldSessionReport(10L)).thenReturn(Optional.empty());

        StudyDetailResponse response = service.getDetail(20L, 10L);

        assertThat(response.isMember()).isTrue();
        assertThat(response.canApply()).isFalse();
        verify(applicationRepository, never()).findByStudyIdAndApplicantId(anyLong(), anyLong());
    }

    private void stubCommon(StudyStatus status, long count, StudyMember membership) {
        Study study = entity(Study.class);
        ReflectionTestUtils.setField(study, "id", 10L);
        ReflectionTestUtils.setField(study, "apartmentId", 15L);
        ReflectionTestUtils.setField(study, "leaderId", 7L);
        ReflectionTestUtils.setField(study, "title", "스터디");
        ReflectionTestUtils.setField(study, "intro", "소개");
        ReflectionTestUtils.setField(study, "goal", "목표");
        ReflectionTestUtils.setField(study, "purpose", StudyPurpose.RESIDENCE);
        ReflectionTestUtils.setField(study, "status", status);
        ReflectionTestUtils.setField(study, "capacity", 6);
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(studyMemberRepository.findByStudyIdAndMemberId(10L, membership == null ? 20L : membership.getMemberId()))
                .thenReturn(Optional.ofNullable(membership));
        when(studyMemberRepository.countByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(count);
        when(scheduleRepository.findByStudyIdAndStatus(10L, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.empty());
        Apartment apartment = entity(Apartment.class);
        ReflectionTestUtils.setField(apartment, "id", 15L);
        ReflectionTestUtils.setField(apartment, "name", "아파트");
        when(apartmentRepository.findById(15L)).thenReturn(Optional.of(apartment));
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L)));
    }

    private void stubSchedule(Instant startAt) {
        Schedule schedule = entity(Schedule.class);
        ReflectionTestUtils.setField(schedule, "id", 5L);
        ReflectionTestUtils.setField(schedule, "studyId", 10L);
        ReflectionTestUtils.setField(schedule, "startAt", startAt);
        ReflectionTestUtils.setField(schedule, "endAt", startAt.plusSeconds(7200));
        ReflectionTestUtils.setField(schedule, "status", ScheduleStatus.SCHEDULED);
        when(scheduleRepository.findByStudyIdAndStatus(10L, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.of(schedule));
    }

    private StudyMember studyMember(Long memberId, StudyMemberRole role, int order) {
        StudyMember sm = entity(StudyMember.class);
        ReflectionTestUtils.setField(sm, "studyId", 10L);
        ReflectionTestUtils.setField(sm, "memberId", memberId);
        ReflectionTestUtils.setField(sm, "role", role);
        ReflectionTestUtils.setField(sm, "status", StudyMemberStatus.ACTIVE);
        ReflectionTestUtils.setField(sm, "joinedAt", Instant.ofEpochSecond(order));
        return sm;
    }

    private Member member(Long id) {
        Member member = entity(Member.class);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "nickname", "회원" + id);
        ReflectionTestUtils.setField(member, "selectedCharacterId", "PALBANG");
        return member;
    }

    private <T> T entity(Class<T> type) {
        return BeanUtils.instantiateClass(type);
    }
}
