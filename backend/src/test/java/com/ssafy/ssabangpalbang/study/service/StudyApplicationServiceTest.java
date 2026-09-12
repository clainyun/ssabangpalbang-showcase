package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyApplication;
import com.ssafy.ssabangpalbang.study.domain.StudyApplicationStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyPurpose;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.dto.request.StudyApplicationCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.response.StudyApplicationCreateResponse;
import com.ssafy.ssabangpalbang.study.repository.ScheduleRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyApplicationRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyDetailQueryRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import com.ssafy.ssabangpalbang.study.service.port.StudyNotificationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudyApplicationServiceTest {

    @Mock private StudyRepository studyRepository;
    @Mock private StudyMemberRepository studyMemberRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private ApartmentRepository apartmentRepository;
    @Mock private StudyApplicationRepository applicationRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private StudyDetailQueryRepository detailQueryRepository;
    @Mock private StudyNotificationPort studyNotificationPort;

    private StudyService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new StudyService(
                studyRepository,
                studyMemberRepository,
                memberRepository,
                apartmentRepository,
                applicationRepository,
                scheduleRepository,
                detailQueryRepository,
                studyNotificationPort,
                java.time.Clock.systemUTC()
        );
    }

    @Test
    void createsPendingApplicationWithTrimmedIntro() {
        stubEligibleApplicantAndStudy();
        when(applicationRepository.saveAndFlush(any(StudyApplication.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        StudyApplicationCreateResponse response = service.applyToStudy(
                10L,
                7L,
                new StudyApplicationCreateRequest("  실거주를 고려 중입니다.  ", "RESIDENCE")
        );

        assertThat(response.applicationId()).isEqualTo(25L);
        assertThat(response.studyId()).isEqualTo(10L);
        assertThat(response.intro()).isEqualTo("실거주를 고려 중입니다.");
        assertThat(response.purpose()).isEqualTo("RESIDENCE");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.createdAt().getOffset().getTotalSeconds()).isEqualTo(9 * 60 * 60);
        assertThat(response.applicant().memberId()).isEqualTo(7L);
        assertThat(response.applicant().nickname()).isEqualTo("신청자");
        assertThat(response.applicant().selectedCharacterId()).isEqualTo("PALBANG");
        verify(applicationRepository).saveAndFlush(any(StudyApplication.class));
        verify(studyMemberRepository, never()).save(any(StudyMember.class));

        ArgumentCaptor<StudyNotificationPort.ApplicationSubmittedNotification> captor =
                ArgumentCaptor.forClass(StudyNotificationPort.ApplicationSubmittedNotification.class);
        verify(studyNotificationPort).notifyApplicationSubmitted(captor.capture());
        StudyNotificationPort.ApplicationSubmittedNotification notification = captor.getValue();
        assertThat(notification.recipientId()).isEqualTo(99L);
        assertThat(notification.actorId()).isEqualTo(7L);
        assertThat(notification.studyId()).isEqualTo(10L);
        assertThat(notification.applicationId()).isEqualTo(25L);
        assertThat(notification.applicantNickname()).isEqualTo("신청자");
        assertThat(notification.serviceNotificationAgreed()).isTrue();
    }

    @Test
    void rejectsMissingApplicantBeforeLookingUpStudy() {
        when(memberRepository.findById(7L)).thenReturn(Optional.empty());

        assertError(ErrorCode.MEMBER_NOT_FOUND, () -> service.applyToStudy(
                10L, 7L, request("RESIDENCE")));

        verify(studyRepository, never()).findForUpdateByIdAndDeletedAtIsNull(any());
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsWithdrawnApplicantBeforeLookingUpStudy() {
        Member withdrawn = applicant();
        ReflectionTestUtils.setField(withdrawn, "status", MemberStatus.WITHDRAWN);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(withdrawn));

        assertError(ErrorCode.MEMBER_NOT_FOUND, () -> service.applyToStudy(
                10L, 7L, request("RESIDENCE")));

        verify(studyRepository, never()).findForUpdateByIdAndDeletedAtIsNull(any());
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDeletedApplicantBeforeLookingUpStudy() {
        Member deleted = applicant();
        ReflectionTestUtils.setField(deleted, "deletedAt", Instant.now());
        when(memberRepository.findById(7L)).thenReturn(Optional.of(deleted));

        assertError(ErrorCode.MEMBER_NOT_FOUND, () -> service.applyToStudy(
                10L, 7L, request("RESIDENCE")));

        verify(studyRepository, never()).findForUpdateByIdAndDeletedAtIsNull(any());
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMissingStudy() {
        when(memberRepository.findById(7L)).thenReturn(Optional.of(applicant()));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.empty());

        assertError(ErrorCode.STUDY_NOT_FOUND, () -> service.applyToStudy(
                10L, 7L, request("RESIDENCE")));

        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = StudyStatus.class, names = "RECRUITING", mode = EnumSource.Mode.EXCLUDE)
    void rejectsEveryNonRecruitingStatus(StudyStatus status) {
        when(memberRepository.findById(7L)).thenReturn(Optional.of(applicant()));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(99L, 3, status)));

        assertError(ErrorCode.STUDY_APPLICATION_NOT_ALLOWED, () -> service.applyToStudy(
                10L, 7L, request("RESIDENCE")));
    }

    @Test
    void rejectsLeaderAndActiveMember() {
        when(memberRepository.findById(7L)).thenReturn(Optional.of(applicant()));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(7L, 3, StudyStatus.RECRUITING)));

        assertError(ErrorCode.STUDY_APPLICATION_NOT_ALLOWED, () -> service.applyToStudy(
                10L, 7L, request("RESIDENCE")));

        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(99L, 3, StudyStatus.RECRUITING)));
        when(studyMemberRepository.findByStudyIdAndMemberId(10L, 7L))
                .thenReturn(Optional.of(memberWithStatus(StudyMemberStatus.ACTIVE)));

        assertError(ErrorCode.STUDY_ALREADY_MEMBER, () -> service.applyToStudy(
                10L, 7L, request("RESIDENCE")));
    }

    @Test
    void removedFormerMemberCanContinueApplication() {
        stubEligibleApplicantAndStudy();
        when(studyMemberRepository.findByStudyIdAndMemberId(10L, 7L))
                .thenReturn(Optional.of(memberWithStatus(StudyMemberStatus.REMOVED)));
        when(applicationRepository.saveAndFlush(any(StudyApplication.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        StudyApplicationCreateResponse response = service.applyToStudy(
                10L, 7L, request("STUDY"));

        assertThat(response.status()).isEqualTo("PENDING");
        verify(applicationRepository).findByStudyIdAndApplicantId(10L, 7L);
        verify(applicationRepository).saveAndFlush(any(StudyApplication.class));
    }

    @ParameterizedTest
    @EnumSource(StudyApplicationStatus.class)
    void rejectsExistingApplicationInEveryStatus(StudyApplicationStatus status) {
        stubEligibleApplicantAndStudy();
        StudyApplication existing = StudyApplication.create(
                10L, 7L, "소개", StudyPurpose.RESIDENCE);
        ReflectionTestUtils.setField(existing, "status", status);
        when(applicationRepository.findByStudyIdAndApplicantId(10L, 7L))
                .thenReturn(Optional.of(existing));

        assertError(ErrorCode.STUDY_APPLICATION_ALREADY_EXISTS, () -> service.applyToStudy(
                10L, 7L, request("RESIDENCE")));

        verify(studyMemberRepository, never())
                .countByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE);
    }

    @Test
    void rejectsFullCapacity() {
        stubEligibleApplicantAndStudy();
        when(studyMemberRepository.countByStudyIdAndStatus(
                10L, StudyMemberStatus.ACTIVE)).thenReturn(3L);

        assertError(ErrorCode.STUDY_CAPACITY_FULL, () -> service.applyToStudy(
                10L, 7L, request("RESIDENCE")));
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMemberCountGreaterThanCapacity() {
        stubEligibleApplicantAndStudy();
        when(studyMemberRepository.countByStudyIdAndStatus(
                10L, StudyMemberStatus.ACTIVE)).thenReturn(4L);

        assertError(ErrorCode.STUDY_CAPACITY_FULL, () -> service.applyToStudy(
                10L, 7L, request("RESIDENCE")));
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidPurposeBeforeAccessingDomainState() {
        assertError(ErrorCode.STUDY_APPLICATION_PURPOSE_INVALID, () -> service.applyToStudy(
                10L, 7L, request("residence")));
        verify(memberRepository, never()).findById(any());
        verify(studyRepository, never()).findForUpdateByIdAndDeletedAtIsNull(any());
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void translatesApplicationUniqueConstraintViolationToAlreadyExists() {
        stubEligibleApplicantAndStudy();
        DataIntegrityViolationException violation = constraintViolation(
                "study_application_study_id_applicant_id_key");
        when(applicationRepository.saveAndFlush(any(StudyApplication.class)))
                .thenThrow(violation);

        assertError(ErrorCode.STUDY_APPLICATION_ALREADY_EXISTS, () -> service.applyToStudy(
                10L, 7L, request("STUDY")));
    }

    @Test
    void rethrowsUnrelatedDataIntegrityViolation() {
        stubEligibleApplicantAndStudy();
        DataIntegrityViolationException violation =
                constraintViolation("study_application_study_id_fkey");
        when(applicationRepository.saveAndFlush(any(StudyApplication.class)))
                .thenThrow(violation);

        assertThatThrownBy(() -> service.applyToStudy(10L, 7L, request("STUDY")))
                .isSameAs(violation);
    }

    private void stubEligibleApplicantAndStudy() {
        when(memberRepository.findById(7L)).thenReturn(Optional.of(applicant()));
        when(memberRepository.findById(99L)).thenReturn(Optional.of(leader()));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(99L, 3, StudyStatus.RECRUITING)));
        when(studyMemberRepository.findByStudyIdAndMemberId(10L, 7L))
                .thenReturn(Optional.empty());
        when(applicationRepository.findByStudyIdAndApplicantId(10L, 7L))
                .thenReturn(Optional.empty());
        when(studyMemberRepository.countByStudyIdAndStatus(
                10L, StudyMemberStatus.ACTIVE)).thenReturn(1L);
    }

    private Member applicant() {
        Member member = new Member("apply@example.com", "hash", "신청자");
        ReflectionTestUtils.setField(member, "id", 7L);
        return member;
    }

    private Member leader() {
        Member member = new Member("leader@example.com", "hash", "스터디장");
        ReflectionTestUtils.setField(member, "id", 99L);
        return member;
    }

    private Study study(Long leaderId, int capacity, StudyStatus status) {
        Study study = Study.create(
                1L, leaderId, "스터디", "소개", "목표", capacity, StudyPurpose.STUDY);
        ReflectionTestUtils.setField(study, "id", 10L);
        ReflectionTestUtils.setField(study, "status", status);
        return study;
    }

    private StudyMember memberWithStatus(StudyMemberStatus status) {
        StudyMember member = StudyMember.createLeader(10L, 7L);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }

    private StudyApplication persisted(StudyApplication application) {
        ReflectionTestUtils.setField(application, "id", 25L);
        ReflectionTestUtils.setField(
                application, "createdAt", Instant.parse("2026-07-24T09:10:00Z"));
        return application;
    }

    private StudyApplicationCreateRequest request(String purpose) {
        return new StudyApplicationCreateRequest("소개", purpose);
    }

    private DataIntegrityViolationException constraintViolation(String constraintName) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "constraint violation",
                new SQLException("constraint violation"),
                "insert into study_application",
                constraintName
        );
        return new DataIntegrityViolationException("constraint violation", cause);
    }

    private void assertError(ErrorCode expected, Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
