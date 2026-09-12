package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyPurpose;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.dto.request.StudyUpdateRequest;
import com.ssafy.ssabangpalbang.study.repository.ScheduleRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyApplicationRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyDetailQueryRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import com.ssafy.ssabangpalbang.study.service.port.StudyNotificationPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyUpdateServiceTest {

    @Mock StudyRepository studyRepository;
    @Mock StudyMemberRepository studyMemberRepository;
    @Mock MemberRepository memberRepository;
    @Mock ApartmentRepository apartmentRepository;
    @Mock StudyApplicationRepository studyApplicationRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock StudyDetailQueryRepository studyDetailQueryRepository;
    @Mock StudyNotificationPort studyNotificationPort;
    @Mock Clock clock;

    @InjectMocks StudyService service;

    @Test
    void 스터디장은_목표와_소개를_수정할_수_있다() {
        Study study = study(7L, StudyStatus.RECRUITING);
        arrangeActiveLeader();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));

        var response = service.updateDetails(
                7L,
                10L,
                new StudyUpdateRequest(null, "  새 목표  ", "  새 소개  ")
        );

        assertThat(study.getTitle()).isEqualTo("스터디");
        assertThat(study.getGoal()).isEqualTo("새 목표");
        assertThat(study.getIntro()).isEqualTo("새 소개");
        assertThat(response.title()).isEqualTo("스터디");
        assertThat(response.goal()).isEqualTo("새 목표");
        assertThat(response.intro()).isEqualTo("새 소개");
    }

    @Test
    void 스터디장은_제목만_수정할_수_있다() {
        Study study = study(7L, StudyStatus.RECRUITING);
        arrangeActiveLeader();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));

        var response = service.updateDetails(
                7L,
                10L,
                new StudyUpdateRequest("  새 제목  ", null, null)
        );

        assertThat(study.getTitle()).isEqualTo("새 제목");
        assertThat(study.getGoal()).isEqualTo("기존 목표");
        assertThat(study.getIntro()).isEqualTo("기존 소개");
        assertThat(response.title()).isEqualTo("새 제목");
    }

    @Test
    void 스터디장은_제목과_목표와_소개를_함께_수정할_수_있다() {
        Study study = study(7L, StudyStatus.RECRUITING);
        arrangeActiveLeader();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));

        var response = service.updateDetails(
                7L,
                10L,
                new StudyUpdateRequest("새 제목", "새 목표", "새 소개")
        );

        assertThat(study.getTitle()).isEqualTo("새 제목");
        assertThat(study.getGoal()).isEqualTo("새 목표");
        assertThat(study.getIntro()).isEqualTo("새 소개");
        assertThat(response.title()).isEqualTo("새 제목");
        assertThat(response.goal()).isEqualTo("새 목표");
        assertThat(response.intro()).isEqualTo("새 소개");
    }

    @Test
    void 제목이_null이면_기존_제목을_유지한다() {
        Study study = study(7L, StudyStatus.RECRUITING);
        arrangeActiveLeader();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));

        service.updateDetails(7L, 10L, new StudyUpdateRequest(null, "새 목표", null));

        assertThat(study.getTitle()).isEqualTo("스터디");
        assertThat(study.getGoal()).isEqualTo("새 목표");
    }

    @Test
    void 소개만_빈_문자열로_수정하면_소개를_삭제하고_목표는_유지한다() {
        Study study = study(7L, StudyStatus.CLOSED);
        arrangeActiveLeader();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));

        service.updateDetails(7L, 10L, new StudyUpdateRequest(null, null, "   "));

        assertThat(study.getGoal()).isEqualTo("기존 목표");
        assertThat(study.getIntro()).isEmpty();
    }

    @Test
    void 일반_멤버는_목표와_소개를_수정할_수_없다() {
        Study study = study(7L, StudyStatus.RECRUITING);
        arrangeActiveMember(8L);
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));

        assertError(
                () -> service.updateDetails(
                        8L, 10L, new StudyUpdateRequest(null, "새 목표", "새 소개")),
                ErrorCode.STUDY_UPDATE_FORBIDDEN
        );

        assertThat(study.getGoal()).isEqualTo("기존 목표");
    }

    @Test
    void 완료된_스터디는_스터디장도_수정할_수_없다() {
        Study study = study(7L, StudyStatus.COMPLETED);
        arrangeActiveLeader();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));

        assertError(
                () -> service.updateDetails(
                        7L, 10L, new StudyUpdateRequest(null, "새 목표", null)),
                ErrorCode.STUDY_UPDATE_NOT_ALLOWED
        );
    }

    @Test
    void 수정할_필드가_없으면_거부한다() {
        Study study = study(7L, StudyStatus.RECRUITING);
        arrangeActiveLeader();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));

        assertError(
                () -> service.updateDetails(7L, 10L, new StudyUpdateRequest(null, null, null)),
                ErrorCode.STUDY_UPDATE_EMPTY
        );
    }

    @Test
    void 공백_목표나_길이_제한을_넘은_소개는_거부한다() {
        Study study = study(7L, StudyStatus.RECRUITING);
        arrangeActiveLeader();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));

        assertError(
                () -> service.updateDetails(
                        7L, 10L, new StudyUpdateRequest(null, "   ", null)),
                ErrorCode.INVALID_INPUT_VALUE
        );
        assertError(
                () -> service.updateDetails(
                        7L, 10L, new StudyUpdateRequest(null, null, "가".repeat(1001))),
                ErrorCode.INVALID_INPUT_VALUE
        );

        assertThat(study.getGoal()).isEqualTo("기존 목표");
        assertThat(study.getIntro()).isEqualTo("기존 소개");
    }

    @Test
    void 공백_제목이나_길이_제한을_넘은_제목은_거부한다() {
        Study study = study(7L, StudyStatus.RECRUITING);
        arrangeActiveLeader();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));

        assertError(
                () -> service.updateDetails(
                        7L, 10L, new StudyUpdateRequest("   ", null, null)),
                ErrorCode.INVALID_INPUT_VALUE
        );
        assertError(
                () -> service.updateDetails(
                        7L, 10L, new StudyUpdateRequest("가".repeat(201), null, null)),
                ErrorCode.INVALID_INPUT_VALUE
        );

        assertThat(study.getTitle()).isEqualTo("스터디");
    }

    @Test
    void 목표와_소개의_최대_길이는_허용한다() {
        Study study = study(7L, StudyStatus.RECRUITING);
        arrangeActiveLeader();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));

        service.updateDetails(
                7L,
                10L,
                new StudyUpdateRequest(null, "가".repeat(300), "나".repeat(1000))
        );

        assertThat(study.getGoal()).hasSize(300);
        assertThat(study.getIntro()).hasSize(1000);
    }

    @Test
    void 제목의_최대_길이는_허용한다() {
        Study study = study(7L, StudyStatus.RECRUITING);
        arrangeActiveLeader();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));

        service.updateDetails(
                7L,
                10L,
                new StudyUpdateRequest("가".repeat(200), null, null)
        );

        assertThat(study.getTitle()).hasSize(200);
    }

    @Test
    void 눈에_보이지_않는_유니코드만_있는_목표는_거부한다() {
        Study study = study(7L, StudyStatus.RECRUITING);
        arrangeActiveLeader();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));

        assertError(
                () -> service.updateDetails(
                        7L, 10L, new StudyUpdateRequest(null, " ​", null)),
                ErrorCode.INVALID_INPUT_VALUE
        );
    }

    @Test
    void 취소된_스터디는_스터디장도_수정할_수_없다() {
        Study study = study(7L, StudyStatus.CANCELED);
        arrangeActiveLeader();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));

        assertError(
                () -> service.updateDetails(
                        7L, 10L, new StudyUpdateRequest(null, "새 목표", null)),
                ErrorCode.STUDY_UPDATE_NOT_ALLOWED
        );
    }

    @Test
    void ACTIVE_리더_가입_행이_없으면_수정할_수_없다() {
        Study study = study(7L, StudyStatus.RECRUITING);
        arrangeActiveMember(7L);
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));
        when(studyMemberRepository.findForUpdateByStudyIdAndMemberId(10L, 7L))
                .thenReturn(Optional.empty());

        assertError(
                () -> service.updateDetails(
                        7L, 10L, new StudyUpdateRequest(null, "새 목표", null)),
                ErrorCode.STUDY_UPDATE_FORBIDDEN
        );
    }

    @Test
    void 탈퇴한_회원은_스터디_조회_전에_거부한다() {
        Member member = member(7L);
        ReflectionTestUtils.setField(member, "status", MemberStatus.WITHDRAWN);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));

        assertError(
                () -> service.updateDetails(
                        7L, 10L, new StudyUpdateRequest(null, "새 목표", "새 소개")),
                ErrorCode.MEMBER_NOT_FOUND
        );

        verify(studyRepository, never()).findForUpdateByIdAndDeletedAtIsNull(10L);
    }

    private void arrangeActiveMember(Long memberId) {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member(memberId)));
    }

    private void arrangeActiveLeader() {
        arrangeActiveMember(7L);
        when(studyMemberRepository.findForUpdateByStudyIdAndMemberId(10L, 7L))
                .thenReturn(Optional.of(StudyMember.createLeader(10L, 7L)));
    }

    private Study study(Long leaderId, StudyStatus status) {
        Study study = Study.create(
                15L,
                leaderId,
                "스터디",
                "기존 소개",
                "기존 목표",
                6,
                StudyPurpose.RESIDENCE
        );
        ReflectionTestUtils.setField(study, "id", 10L);
        ReflectionTestUtils.setField(study, "status", status);
        return study;
    }

    private Member member(Long memberId) {
        Member member = new Member("member" + memberId + "@example.com", "hash", "회원");
        ReflectionTestUtils.setField(member, "id", memberId);
        return member;
    }

    private void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected)
                );
    }
}
