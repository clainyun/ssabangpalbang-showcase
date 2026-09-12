package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyApplication;
import com.ssafy.ssabangpalbang.study.domain.StudyApplicationStatus;
import com.ssafy.ssabangpalbang.study.dto.request.ApartmentStudySort;
import com.ssafy.ssabangpalbang.study.dto.response.ApartmentStudyResponse;
import com.ssafy.ssabangpalbang.study.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApartmentStudyServiceTest {
    @Mock StudyRepository studyRepository;
    @Mock StudyMemberRepository studyMemberRepository;
    @Mock MemberRepository memberRepository;
    @Mock ApartmentRepository apartmentRepository;
    @Mock StudyApplicationRepository applicationRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock StudyDetailQueryRepository queryRepository;
    @Mock java.time.Clock clock;
    @InjectMocks StudyService service;

    @Test
    void rejectsMissingApartmentBeforeListQuery() {
        when(apartmentRepository.existsById(15L)).thenReturn(false);

        assertThatThrownBy(() -> service.getRecruitingStudies(
                7L, 15L, ApartmentStudySort.SCHEDULE_ASC, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.APARTMENT_NOT_FOUND));
        verifyNoInteractions(studyRepository);
    }

    @Test
    void rejectsMissingOrInactiveMemberBeforeListQuery() {
        when(apartmentRepository.existsById(15L)).thenReturn(true);
        when(memberRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRecruitingStudies(
                7L, 15L, ApartmentStudySort.SCHEDULE_ASC, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
        verifyNoInteractions(studyRepository);
    }

    @Test
    void emptyPageSkipsAllBatchQueries() {
        stubViewer();
        when(studyRepository.findRecruitingOrderByScheduleAsc(eq(15L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<ApartmentStudyResponse> response = service.getRecruitingStudies(
                7L, 15L, ApartmentStudySort.SCHEDULE_ASC, 0, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        verify(memberRepository, never()).findAllById(any());
        verifyNoInteractions(studyMemberRepository, applicationRepository);
    }

    @Test
    void mapsRowsAndUsesOneBatchQueryPerRelation() {
        stubViewer();
        RecruitingStudyRow first = row(10L, 20L, 6, 4, 3);
        RecruitingStudyRow second = row(11L, 21L, 4, 1, 0);
        RecruitingStudyRow third = row(12L, 22L, 5, 2, 0);
        when(studyRepository.findRecruitingOrderByScheduleAsc(eq(15L), any()))
                .thenReturn(new PageImpl<>(List.of(first, second, third)));
        when(memberRepository.findAllById(any()))
                .thenReturn(List.of(leader(20L, true), leader(21L, false), leader(22L, true)));
        when(studyMemberRepository.findByStudyIdInAndMemberIdAndStatus(
                any(), eq(7L), eq(StudyMemberStatus.ACTIVE))).thenReturn(List.of());
        when(applicationRepository.findByStudyIdInAndApplicantId(any(), eq(7L)))
                .thenReturn(List.of());

        PageResponse<ApartmentStudyResponse> response = service.getRecruitingStudies(
                7L, 15L, ApartmentStudySort.SCHEDULE_ASC, 0, 20);

        assertThat(response.content()).hasSize(3);
        assertThat(response.content().get(0).remainingCapacity()).isEqualTo(2);
        assertThat(response.content().get(0).schedule().startAt().getOffset().getTotalSeconds())
                .isEqualTo(9 * 3600);
        assertThat(response.content().get(0).schedule().dDay()).isEqualTo(3);
        assertThat(response.content().get(1).schedule()).isNull();
        assertThat(response.content().get(1).leader().ageGroup()).isNull();
        assertThat(response.content().get(0).applicationStatus()).isEqualTo("NONE");
        assertThat(response.content().get(0).canApply()).isTrue();
        verify(memberRepository, times(1)).findAllById(any());
        verify(studyMemberRepository, times(1))
                .findByStudyIdInAndMemberIdAndStatus(any(), eq(7L), eq(StudyMemberStatus.ACTIVE));
        verify(applicationRepository, times(1))
                .findByStudyIdInAndApplicantId(any(), eq(7L));
    }

    @Test
    void todayScheduleHasZeroDDayAndNullEndAt() {
        stubViewer();
        RecruitingStudyRow row = row(10L, 20L, 6, 1, 0);
        Instant tonight = LocalDate.now(ZoneId.of("Asia/Seoul"))
                .atTime(23, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
        when(row.getScheduleId()).thenReturn(110L);
        when(row.getStartAt()).thenReturn(tonight);
        when(row.getEndAt()).thenReturn(null);
        when(studyRepository.findRecruitingOrderByScheduleAsc(eq(15L), any()))
                .thenReturn(new PageImpl<>(List.of(row)));
        when(memberRepository.findAllById(any())).thenReturn(List.of(leader(20L, true)));
        when(studyMemberRepository.findByStudyIdInAndMemberIdAndStatus(any(), anyLong(), any()))
                .thenReturn(List.of());
        when(applicationRepository.findByStudyIdInAndApplicantId(any(), anyLong()))
                .thenReturn(List.of());

        ApartmentStudyResponse.ScheduleSummary schedule = service.getRecruitingStudies(
                7L, 15L, ApartmentStudySort.SCHEDULE_ASC, 0, 20).content().get(0).schedule();

        assertThat(schedule.dDay()).isZero();
        assertThat(schedule.endAt()).isNull();
    }

    @Test
    void applicationAndMembershipStatusesControlCanApply() {
        stubViewer();
        RecruitingStudyRow pendingRow = row(10L, 20L, 6, 1, 0);
        RecruitingStudyRow rejectedRow = row(11L, 21L, 6, 1, 0);
        RecruitingStudyRow leaderRow = row(12L, 7L, 6, 1, 0);
        when(studyRepository.findRecruitingOrderByScheduleAsc(eq(15L), any()))
                .thenReturn(new PageImpl<>(List.of(pendingRow, rejectedRow, leaderRow)));
        when(memberRepository.findAllById(any()))
                .thenReturn(List.of(leader(20L, true), leader(21L, true), leader(7L, true)));
        when(studyMemberRepository.findByStudyIdInAndMemberIdAndStatus(any(), anyLong(), any()))
                .thenReturn(List.of(membership(12L, 7L)));
        when(applicationRepository.findByStudyIdInAndApplicantId(any(), eq(7L)))
                .thenReturn(List.of(
                        application(10L, StudyApplicationStatus.PENDING),
                        application(11L, StudyApplicationStatus.REJECTED)
                ));

        List<ApartmentStudyResponse> content = service.getRecruitingStudies(
                7L, 15L, ApartmentStudySort.SCHEDULE_ASC, 0, 20).content();

        assertThat(content).extracting(ApartmentStudyResponse::applicationStatus)
                .containsExactly("PENDING", "REJECTED", "APPROVED");
        assertThat(content).extracting(ApartmentStudyResponse::canApply)
                .containsExactly(false, false, false);
        assertThat(content.get(2).isLeader()).isTrue();
        assertThat(content.get(2).isMember()).isTrue();
    }

    @Test
    void fullStudyCannotBeAppliedToEvenIfRepositoryReturnsIt() {
        stubViewer();
        RecruitingStudyRow full = row(10L, 20L, 6, 6, 0);
        when(studyRepository.findRecruitingOrderByScheduleAsc(eq(15L), any()))
                .thenReturn(new PageImpl<>(List.of(full)));
        when(memberRepository.findAllById(any())).thenReturn(List.of(leader(20L, true)));
        when(studyMemberRepository.findByStudyIdInAndMemberIdAndStatus(any(), anyLong(), any()))
                .thenReturn(List.of());
        when(applicationRepository.findByStudyIdInAndApplicantId(any(), anyLong()))
                .thenReturn(List.of());

        ApartmentStudyResponse response = service.getRecruitingStudies(
                7L, 15L, ApartmentStudySort.SCHEDULE_ASC, 0, 20).content().get(0);

        assertThat(response.canApply()).isFalse();
    }

    @Test
    void responsePolicyRejectsMemberWithoutApplicationHistory() {
        RecruitingStudyRow recruiting = row(10L, 20L, 6, 1, 0);

        ApartmentStudyResponse response = ApartmentStudyResponse.from(
                recruiting, leader(20L, true), "NONE", true, 7L);

        assertThat(response.isMember()).isTrue();
        assertThat(response.applicationStatus()).isEqualTo("NONE");
        assertThat(response.canApply()).isFalse();
    }

    private void stubViewer() {
        when(apartmentRepository.existsById(15L)).thenReturn(true);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(leader(7L, true)));
    }

    private RecruitingStudyRow row(
            long studyId, long leaderId, int capacity, int count, int daysAhead) {
        RecruitingStudyRow row = mock(RecruitingStudyRow.class);
        when(row.getStudyId()).thenReturn(studyId);
        when(row.getLeaderId()).thenReturn(leaderId);
        when(row.getTitle()).thenReturn("스터디");
        when(row.getIntro()).thenReturn("소개");
        when(row.getGoal()).thenReturn("목표");
        when(row.getPurpose()).thenReturn("RESIDENCE");
        when(row.getCapacity()).thenReturn(capacity);
        when(row.getCurrentMemberCount()).thenReturn(count);
        when(row.getRemainingCapacity()).thenReturn(capacity - count);
        if (daysAhead > 0) {
            Instant start = LocalDate.now(ZoneId.of("Asia/Seoul"))
                    .plusDays(daysAhead).atTime(23, 0)
                    .atZone(ZoneId.of("Asia/Seoul")).toInstant();
            when(row.getScheduleId()).thenReturn(studyId + 100);
            when(row.getStartAt()).thenReturn(start);
            when(row.getEndAt()).thenReturn(null);
            when(row.getMeetingPlace()).thenReturn("만남 장소");
        } else {
            when(row.getScheduleId()).thenReturn(null);
        }
        return row;
    }

    private Member leader(long id, boolean agePublic) {
        Member member = BeanUtils.instantiateClass(Member.class);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "nickname", "리더" + id);
        ReflectionTestUtils.setField(member, "selectedCharacterId", "PALBANG");
        ReflectionTestUtils.setField(member, "ageGroup", "THIRTIES");
        ReflectionTestUtils.setField(member, "ageGroupPublicAgreed", agePublic);
        ReflectionTestUtils.setField(member, "status", MemberStatus.ACTIVE);
        return member;
    }

    private StudyApplication application(long studyId, StudyApplicationStatus status) {
        StudyApplication application = BeanUtils.instantiateClass(StudyApplication.class);
        ReflectionTestUtils.setField(application, "studyId", studyId);
        ReflectionTestUtils.setField(application, "applicantId", 7L);
        ReflectionTestUtils.setField(application, "status", status);
        return application;
    }

    private StudyMember membership(long studyId, long memberId) {
        StudyMember membership = BeanUtils.instantiateClass(StudyMember.class);
        ReflectionTestUtils.setField(membership, "studyId", studyId);
        ReflectionTestUtils.setField(membership, "memberId", memberId);
        ReflectionTestUtils.setField(membership, "status", StudyMemberStatus.ACTIVE);
        return membership;
    }
}
