package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.review.repository.MemberReviewRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberRole;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.dto.response.StudyMemberListResponse;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyDetailQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class StudyMemberQueryServiceTest {

    @Mock StudyRepository studyRepository;
    @Mock StudyMemberRepository studyMemberRepository;
    @Mock MemberRepository memberRepository;
    @Mock StudyDetailQueryRepository studyDetailQueryRepository;
    @Mock MemberReviewRepository memberReviewRepository;
    StudyMemberQueryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new StudyMemberQueryService(
                studyRepository,
                studyMemberRepository,
                memberRepository,
                studyDetailQueryRepository,
                memberReviewRepository);
        when(studyDetailQueryRepository.findFieldSessionStatus(any())).thenReturn(Optional.empty());
        when(memberReviewRepository.findRevieweeIdsByStudyIdAndReviewerId(any(), any()))
                .thenReturn(List.of());
        when(memberRepository.findById(any())).thenAnswer(invocation -> {
            Long memberId = invocation.getArgument(0);
            return Optional.of(member(memberId, "요청자", null));
        });
    }

    @Test
    void leaderGetsAllActiveMembersWithLeaderFirstAndDeterministicOrder() {
        Study study = study(StudyStatus.RECRUITING);
        StudyMember leader = studyMember(7L, StudyMemberRole.LEADER, "2026-07-23T00:00:00Z");
        StudyMember laterId = studyMember(12L, StudyMemberRole.MEMBER, "2026-07-21T00:00:00Z");
        StudyMember earlierId = studyMember(9L, StudyMemberRole.MEMBER, "2026-07-21T00:00:00Z");
        mock(study, List.of(laterId, leader, earlierId),
                List.of(member(7L, "리더", null), member(9L, "멤버1", null),
                        member(12L, "멤버2", "image")));
        when(memberReviewRepository.findRevieweeIdsByStudyIdAndReviewerId(10L, 7L))
                .thenReturn(List.of(9L));

        StudyMemberListResponse result = service.getMembers(7L, 10L);

        assertThat(result.isLeader()).isTrue();
        assertThat(result.currentMemberCount()).isEqualTo(3);
        assertThat(result.members()).extracting(StudyMemberListResponse.MemberItem::memberId)
                .containsExactly(7L, 9L, 12L);
        assertThat(result.members()).extracting(StudyMemberListResponse.MemberItem::canKick)
                .containsExactly(false, true, true);
        assertThat(result.members()).extracting(StudyMemberListResponse.MemberItem::reviewedByMe)
                .containsExactly(false, true, false);
        assertThat(result.members().get(0).profileImageUrl()).isNull();
    }

    @Test
    void activeMemberCanReadButCannotKickAnyone() {
        Study study = study(StudyStatus.CLOSED);
        StudyMember leader = studyMember(7L, StudyMemberRole.LEADER, "2026-07-20T00:00:00Z");
        StudyMember member = studyMember(9L, StudyMemberRole.MEMBER, "2026-07-21T00:00:00Z");
        mock(study, List.of(leader, member),
                List.of(member(7L, "리더", null), member(9L, "멤버", null)));

        StudyMemberListResponse result = service.getMembers(9L, 10L);

        assertThat(result.isLeader()).isFalse();
        assertThat(result.members()).allMatch(item -> !item.canKick());
    }

    @ParameterizedTest
    @EnumSource(value = StudyStatus.class, names = {"IN_PROGRESS", "COMPLETED", "CANCELED"})
    void compositionLockedStatusesDisableKickAndRemainReadable(StudyStatus status) {
        Study study = study(status);
        StudyMember leader = studyMember(7L, StudyMemberRole.LEADER, "2026-07-20T00:00:00Z");
        StudyMember member = studyMember(9L, StudyMemberRole.MEMBER, "2026-07-21T00:00:00Z");
        mock(study, List.of(leader, member),
                List.of(member(7L, "리더", null), member(9L, "멤버", null)));

        StudyMemberListResponse result = service.getMembers(7L, 10L);

        assertThat(result.members()).allMatch(item -> !item.canKick());
    }

    @Test
    void oneLeaderIsAValidMemberList() {
        Study study = study(StudyStatus.RECRUITING);
        StudyMember leader = studyMember(7L, StudyMemberRole.LEADER, "2026-07-20T00:00:00Z");
        mock(study, List.of(leader), List.of(member(7L, "리더", null)));

        assertThat(service.getMembers(7L, 10L).members()).hasSize(1);
    }

    @Test
    void missingStudyFails() {
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMembers(7L, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.STUDY_NOT_FOUND));
    }

    @Test
    void outsiderIncludingFormerOrPendingApplicantIsForbidden() {
        Study study = study(StudyStatus.RECRUITING);
        StudyMember leader = studyMember(7L, StudyMemberRole.LEADER, "2026-07-20T00:00:00Z");
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(studyMemberRepository.findByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(leader));

        assertThatThrownBy(() -> service.getMembers(99L, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.STUDY_MEMBER_LIST_FORBIDDEN));
    }

    @Test
    void sortsThreeRegularMembersByJoinedAtAscending() {
        Study study = study(StudyStatus.RECRUITING);
        StudyMember leader = studyMember(7L, StudyMemberRole.LEADER, "2026-07-24T00:00:00Z");
        StudyMember latest = studyMember(12L, StudyMemberRole.MEMBER, "2026-07-23T00:00:00Z");
        StudyMember earliest = studyMember(9L, StudyMemberRole.MEMBER, "2026-07-21T00:00:00Z");
        StudyMember middle = studyMember(10L, StudyMemberRole.MEMBER, "2026-07-22T00:00:00Z");
        mock(study, List.of(latest, middle, leader, earliest), List.of(
                member(7L, "리더", null), member(9L, "첫째", null),
                member(10L, "둘째", null), member(12L, "셋째", null)));

        assertThat(service.getMembers(7L, 10L).members())
                .extracting(StudyMemberListResponse.MemberItem::memberId)
                .containsExactly(7L, 9L, 10L, 12L);
    }

    @ParameterizedTest(name = "{0} 신청자 또는 과거 멤버는 조회할 수 없다")
    @ValueSource(strings = {"PENDING", "REJECTED", "LEFT"})
    void nonActiveRequesterIsForbidden(String requesterState) {
        Study study = study(StudyStatus.RECRUITING);
        StudyMember leader = studyMember(7L, StudyMemberRole.LEADER, "2026-07-20T00:00:00Z");
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(studyMemberRepository.findByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(leader));

        assertThat(requesterState).isNotBlank();
        assertThatThrownBy(() -> service.getMembers(99L, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.STUDY_MEMBER_LIST_FORBIDDEN));
    }

    @Test
    void formerMemberIsExcludedFromActiveMemberResult() {
        Study study = study(StudyStatus.RECRUITING);
        StudyMember leader = studyMember(7L, StudyMemberRole.LEADER, "2026-07-20T00:00:00Z");
        StudyMember active = studyMember(9L, StudyMemberRole.MEMBER, "2026-07-21T00:00:00Z");
        mock(study, List.of(leader, active),
                List.of(member(7L, "리더", null), member(9L, "활성", null)));

        assertThat(service.getMembers(7L, 10L).members())
                .extracting(StudyMemberListResponse.MemberItem::memberId)
                .containsExactly(7L, 9L)
                .doesNotContain(12L);
    }

    @Test
    void withdrawnRequesterCannotReadMemberList() {
        Study study = study(StudyStatus.RECRUITING);
        Member withdrawn = member(7L, "탈퇴 회원", null);
        ReflectionTestUtils.setField(withdrawn, "status", MemberStatus.WITHDRAWN);
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(memberRepository.findById(7L)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.getMembers(7L, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void withdrawnAndDeletedMembersAreExcludedFromResult() {
        Study study = study(StudyStatus.RECRUITING);
        StudyMember leader = studyMember(7L, StudyMemberRole.LEADER, "2026-07-20T00:00:00Z");
        StudyMember withdrawnMembership = studyMember(
                9L, StudyMemberRole.MEMBER, "2026-07-21T00:00:00Z");
        StudyMember deletedMembership = studyMember(
                12L, StudyMemberRole.MEMBER, "2026-07-22T00:00:00Z");
        Member leaderMember = member(7L, "리더", null);
        Member withdrawn = member(9L, "탈퇴 회원", null);
        Member deleted = member(12L, "삭제 회원", null);
        ReflectionTestUtils.setField(withdrawn, "status", MemberStatus.WITHDRAWN);
        ReflectionTestUtils.setField(deleted, "deletedAt", Instant.parse("2026-07-29T00:00:00Z"));
        mock(study, List.of(leader, withdrawnMembership, deletedMembership),
                List.of(leaderMember, withdrawn, deleted));

        StudyMemberListResponse result = service.getMembers(7L, 10L);

        assertThat(result.currentMemberCount()).isEqualTo(1);
        assertThat(result.members())
                .extracting(StudyMemberListResponse.MemberItem::memberId)
                .containsExactly(7L);
    }

    @ParameterizedTest
    @EnumSource(value = StudyStatus.class, names = {"COMPLETED", "CANCELED"})
    void activeRegularMemberCanReadCompletedOrCanceledStudy(StudyStatus status) {
        Study study = study(status);
        StudyMember leader = studyMember(7L, StudyMemberRole.LEADER, "2026-07-20T00:00:00Z");
        StudyMember active = studyMember(9L, StudyMemberRole.MEMBER, "2026-07-21T00:00:00Z");
        mock(study, List.of(leader, active),
                List.of(member(7L, "리더", null), member(9L, "활성", null)));

        StudyMemberListResponse response = service.getMembers(9L, 10L);

        assertThat(response.isLeader()).isFalse();
        assertThat(response.members()).hasSize(2);
    }

    private void mock(Study study, List<StudyMember> studyMembers, List<Member> members) {
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(studyMemberRepository.findByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(studyMembers);
        when(memberRepository.findAllById(org.mockito.ArgumentMatchers.any()))
                .thenReturn(members);
    }

    private Study study(StudyStatus status) {
        Study study = Study.create(1L, 7L, "title", null, "goal", 6, null);
        ReflectionTestUtils.setField(study, "id", 10L);
        ReflectionTestUtils.setField(study, "status", status);
        return study;
    }

    private StudyMember studyMember(Long memberId, StudyMemberRole role, String joinedAt) {
        StudyMember studyMember = StudyMember.createLeader(10L, memberId);
        ReflectionTestUtils.setField(studyMember, "role", role);
        ReflectionTestUtils.setField(studyMember, "joinedAt", Instant.parse(joinedAt));
        return studyMember;
    }

    private Member member(Long id, String nickname, String imageUrl) {
        Member member = new Member(id + "@test.com", "hash", nickname);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "profileImageUrl", imageUrl);
        return member;
    }
}
