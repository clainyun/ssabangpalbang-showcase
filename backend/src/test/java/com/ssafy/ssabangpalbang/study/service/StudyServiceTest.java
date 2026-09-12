package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberRole;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.dto.request.StudyCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.response.StudyCreateResponse;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyServiceTest {

    @Mock
    private StudyRepository studyRepository;
    @Mock
    private StudyMemberRepository studyMemberRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ApartmentRepository apartmentRepository;

    @Mock
    private com.ssafy.ssabangpalbang.study.repository.StudyApplicationRepository studyApplicationRepository;
    @Mock
    private com.ssafy.ssabangpalbang.study.repository.ScheduleRepository scheduleRepository;
    @Mock
    private com.ssafy.ssabangpalbang.study.repository.StudyDetailQueryRepository studyDetailQueryRepository;
    @Mock
    private java.time.Clock clock;

    @InjectMocks
    private StudyService studyService;

    @Test
    void createsStudyAndActiveLeaderMembership() {
        stubReferences();
        when(studyRepository.save(any(Study.class))).thenAnswer(invocation -> {
            Study study = invocation.getArgument(0);
            ReflectionTestUtils.setField(study, "id", 10L);
            ReflectionTestUtils.setField(
                    study, "createdAt", Instant.parse("2026-07-24T09:00:00Z"));
            return study;
        });

        StudyCreateResponse response = studyService.create(7L, request(6, "RESIDENCE"));

        verify(studyRepository).save(any(Study.class));
        ArgumentCaptor<StudyMember> captor = ArgumentCaptor.forClass(StudyMember.class);
        verify(studyMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getStudyId()).isEqualTo(10L);
        assertThat(captor.getValue().getMemberId()).isEqualTo(7L);
        assertThat(captor.getValue().getRole()).isEqualTo(StudyMemberRole.LEADER);
        assertThat(captor.getValue().getStatus()).isEqualTo(StudyMemberStatus.ACTIVE);
        assertThat(response).isInstanceOf(StudyCreateResponse.class);
        assertThat(response.currentMemberCount()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("RECRUITING");
        assertThat(response.createdAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    }

    @Test
    void rejectsMissingMemberBeforeApartmentLookup() {
        when(memberRepository.findById(7L)).thenReturn(Optional.empty());

        assertError(() -> studyService.create(7L, request(6, "RESIDENCE")),
                ErrorCode.MEMBER_NOT_FOUND);
        verify(apartmentRepository, never()).findById(any());
        verify(studyRepository, never()).save(any());
    }

    @Test
    void rejectsInactiveOrDeletedMember() {
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(MemberStatus.WITHDRAWN, null)));
        assertError(() -> studyService.create(7L, request(6, "RESIDENCE")),
                ErrorCode.MEMBER_NOT_FOUND);

        when(memberRepository.findById(7L)).thenReturn(Optional.of(
                member(MemberStatus.ACTIVE, Instant.parse("2026-07-24T09:00:00Z"))));
        assertError(() -> studyService.create(7L, request(6, "RESIDENCE")),
                ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void rejectsMissingApartmentWithoutSavingStudy() {
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(MemberStatus.ACTIVE, null)));
        when(apartmentRepository.findById(15L)).thenReturn(Optional.empty());

        assertError(() -> studyService.create(7L, request(6, "RESIDENCE")),
                ErrorCode.APARTMENT_NOT_FOUND);
        verify(studyRepository, never()).save(any());
    }

    @Test
    void rejectsCapacityOutsideRange() {
        assertError(() -> studyService.create(7L, request(0, "RESIDENCE")),
                ErrorCode.STUDY_CAPACITY_INVALID);
        assertError(() -> studyService.create(7L, request(21, "RESIDENCE")),
                ErrorCode.STUDY_CAPACITY_INVALID);
    }

    @Test
    void acceptsCapacityBoundaries() {
        stubReferences();
        stubSavedStudy();
        assertThat(studyService.create(7L, request(1, "STUDY"))).isNotNull();
        assertThat(studyService.create(7L, request(20, "INVESTMENT"))).isNotNull();
    }

    @Test
    void closesRecruitmentWhenCapacityIsOne() {
        stubReferences();
        stubSavedStudy();

        StudyCreateResponse response = studyService.create(7L, request(1, "STUDY"));

        assertThat(response.status()).isEqualTo("CLOSED");
    }

    @Test
    void rejectsUnknownPurpose() {
        assertError(() -> studyService.create(7L, request(6, "UNKNOWN")),
                ErrorCode.STUDY_PURPOSE_INVALID);
    }

    private void stubReferences() {
        when(memberRepository.findById(7L))
                .thenReturn(Optional.of(member(MemberStatus.ACTIVE, null)));
        when(apartmentRepository.findById(15L)).thenReturn(Optional.of(apartment()));
    }

    private void stubSavedStudy() {
        when(studyRepository.save(any(Study.class))).thenAnswer(invocation -> {
            Study study = invocation.getArgument(0);
            ReflectionTestUtils.setField(study, "id", 10L);
            ReflectionTestUtils.setField(
                    study, "createdAt", Instant.parse("2026-07-24T09:00:00Z"));
            return study;
        });
    }

    private StudyCreateRequest request(int capacity, String purpose) {
        return new StudyCreateRequest(
                15L, "옥수동 주말 임장", "소개", "목표", capacity, purpose
        );
    }

    private Member member(MemberStatus status, Instant deletedAt) {
        Member member = BeanUtils.instantiateClass(Member.class);
        ReflectionTestUtils.setField(member, "id", 7L);
        ReflectionTestUtils.setField(member, "nickname", "집보는다람쥐");
        ReflectionTestUtils.setField(member, "selectedCharacterId", "JIPKONG");
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(member, "deletedAt", deletedAt);
        return member;
    }

    private Apartment apartment() {
        Apartment apartment = BeanUtils.instantiateClass(Apartment.class);
        ReflectionTestUtils.setField(apartment, "id", 15L);
        ReflectionTestUtils.setField(apartment, "name", "래미안 옥수 리버젠");
        ReflectionTestUtils.setField(apartment, "address", "서울특별시 성동구 매봉길 15");
        return apartment;
    }

    private void assertError(ThrowingCallable callable, ErrorCode errorCode) {
        assertThatThrownBy(callable::call)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call();
    }
}
