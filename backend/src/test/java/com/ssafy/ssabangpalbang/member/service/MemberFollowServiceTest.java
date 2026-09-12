package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Follow;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFollowResult;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFollowingListResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberUnfollowResult;
import com.ssafy.ssabangpalbang.member.event.MemberFollowPushRequestedEvent;
import com.ssafy.ssabangpalbang.member.repository.FollowRepository;
import com.ssafy.ssabangpalbang.member.repository.FollowingMemberRow;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberFollowServiceTest {

    private MemberRepository memberRepository;
    private FollowRepository followRepository;
    private NotificationRepository notificationRepository;
    private ApplicationEventPublisher eventPublisher;
    private MemberFollowService memberFollowService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        followRepository = mock(FollowRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        PlatformTransactionManager transactionManager = mock(
                PlatformTransactionManager.class
        );
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));
        memberFollowService = new MemberFollowService(
                memberRepository,
                followRepository,
                notificationRepository,
                eventPublisher,
                transactionManager
        );
    }

    @Test
    void 신규_팔로우를_생성하고_명세_응답을_반환한다() {
        stubActiveMembers();
        when(followRepository.findByFollowerIdAndFollowingId(1L, 15L))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> {
            Follow follow = invocation.getArgument(0, Follow.class);
            ReflectionTestUtils.setField(
                    follow,
                    "id",
                    31L
            );
            ReflectionTestUtils.setField(
                    follow,
                    "createdAt",
                    Instant.parse("2026-07-25T01:30:00Z")
            );
            return follow;
        }).when(followRepository).saveAndFlush(any(Follow.class));
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);
                    ReflectionTestUtils.setField(notification, "id", 81L);
                    return notification;
                });
        when(memberRepository.countPublicProfileFollowings(1L))
                .thenReturn(6L);

        MemberFollowResult result = memberFollowService.follow(1L, 15L);

        assertThat(result.responseCode()).isEqualTo(
                MemberResponseCode.FOLLOWED
        );
        assertThat(result.response().memberId()).isEqualTo(15L);
        assertThat(result.response().nickname()).isEqualTo("임장초보");
        assertThat(result.response().selectedCharacterId())
                .isEqualTo("PALBANG_DOG");
        assertThat(result.response().isFollowing()).isTrue();
        assertThat(result.response().canSendMessage()).isTrue();
        assertThat(result.response().followingCount()).isEqualTo(6L);
        assertThat(result.response().followedAt()).isEqualTo(
                OffsetDateTime.parse("2026-07-25T10:30:00+09:00")
        );
        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getRecipientId()).isEqualTo(15L);
        assertThat(notificationCaptor.getValue().getActorId()).isEqualTo(1L);
        assertThat(notificationCaptor.getValue().getCategory().name()).isEqualTo("COMMUNITY");
        assertThat(notificationCaptor.getValue().getType()).isEqualTo("MEMBER_FOLLOWED");
        assertThat(notificationCaptor.getValue().getTargetScreen()).isEqualTo("MEMBER_PROFILE");
        assertThat(notificationCaptor.getValue().getTargetId()).isEqualTo(1L);
        assertThat(notificationCaptor.getValue().getIdempotencyKey()).isEqualTo("MEMBER_FOLLOWED:31");
        ArgumentCaptor<MemberFollowPushRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(MemberFollowPushRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().notificationId()).isEqualTo(81L);
        assertThat(eventCaptor.getValue().serviceNotificationAgreed()).isTrue();
    }

    @Test
    void 이미_팔로우_중이면_행을_추가하지_않고_기존_시각을_반환한다() {
        stubActiveMembers();
        Follow existing = follow(1L, 15L, "2026-07-20T09:20:00Z");
        when(followRepository.findByFollowerIdAndFollowingId(1L, 15L))
                .thenReturn(Optional.of(existing));
        when(memberRepository.countPublicProfileFollowings(1L))
                .thenReturn(6L);

        MemberFollowResult result = memberFollowService.follow(1L, 15L);

        assertThat(result.responseCode()).isEqualTo(
                MemberResponseCode.ALREADY_FOLLOWING
        );
        assertThat(result.response().followedAt()).isEqualTo(
                OffsetDateTime.parse("2026-07-20T18:20:00+09:00")
        );
        verify(followRepository, never()).saveAndFlush(any());
        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시_중복_삽입이면_기존_팔로우를_다시_조회한다() {
        stubActiveMembers();
        Follow existing = follow(1L, 15L, "2026-07-20T09:20:00Z");
        when(followRepository.findByFollowerIdAndFollowingId(1L, 15L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(followRepository.saveAndFlush(any(Follow.class))).thenThrow(
                new DataIntegrityViolationException("duplicate")
        );
        when(memberRepository.countPublicProfileFollowings(1L))
                .thenReturn(6L);

        MemberFollowResult result = memberFollowService.follow(1L, 15L);

        assertThat(result.responseCode()).isEqualTo(
                MemberResponseCode.ALREADY_FOLLOWING
        );
        assertThat(result.response().followingCount()).isEqualTo(6L);
        verify(notificationRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 본인은_팔로우할_수_없다() {
        Member follower = activeMember(1L, "나");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(follower));

        assertError(
                ErrorCode.MEMBER_SELF_FOLLOW_NOT_ALLOWED,
                () -> memberFollowService.follow(1L, 1L)
        );
        verify(followRepository, never()).saveAndFlush(any());
    }

    @Test
    void 없는_대상은_404로_처리한다() {
        when(memberRepository.findById(1L)).thenReturn(
                Optional.of(activeMember(1L, "나"))
        );
        when(memberRepository.findById(15L)).thenReturn(Optional.empty());

        assertError(
                ErrorCode.MEMBER_NOT_FOUND,
                () -> memberFollowService.follow(1L, 15L)
        );
    }

    @Test
    void 탈퇴한_대상은_존재_여부를_숨기고_404로_처리한다() {
        Member withdrawnTarget = activeMember(15L, "탈퇴회원");
        ReflectionTestUtils.setField(
                withdrawnTarget,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findById(1L)).thenReturn(
                Optional.of(activeMember(1L, "나"))
        );
        when(memberRepository.findById(15L)).thenReturn(
                Optional.of(withdrawnTarget)
        );

        assertError(
                ErrorCode.MEMBER_NOT_FOUND,
                () -> memberFollowService.follow(1L, 15L)
        );
    }

    @Test
    void 탈퇴한_로그인_회원은_403으로_처리한다() {
        Member withdrawnFollower = activeMember(1L, "탈퇴회원");
        ReflectionTestUtils.setField(
                withdrawnFollower,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findById(1L)).thenReturn(
                Optional.of(withdrawnFollower)
        );

        assertError(
                ErrorCode.AUTH_MEMBER_WITHDRAWN,
                () -> memberFollowService.follow(1L, 15L)
        );
    }

    @Test
    void 팔로우_관계를_삭제하고_명세_응답을_반환한다() {
        stubActiveMembers();
        when(followRepository.deleteRelation(1L, 15L)).thenReturn(1);
        when(memberRepository.countPublicProfileFollowings(1L))
                .thenReturn(5L);
        Instant startedAt = Instant.now();

        MemberUnfollowResult result = memberFollowService.unfollow(1L, 15L);

        assertThat(result.responseCode()).isEqualTo(
                MemberResponseCode.UNFOLLOWED
        );
        assertThat(result.response().memberId()).isEqualTo(15L);
        assertThat(result.response().nickname()).isEqualTo("임장초보");
        assertThat(result.response().selectedCharacterId())
                .isEqualTo("PALBANG_DOG");
        assertThat(result.response().isFollowing()).isFalse();
        assertThat(result.response().canSendMessage()).isFalse();
        assertThat(result.response().followingCount()).isEqualTo(5L);
        assertThat(result.response().unfollowedAt().toInstant())
                .isBetween(startedAt, Instant.now());
        verify(followRepository).deleteRelation(1L, 15L);
    }

    @Test
    void 이미_팔로우하지_않아도_멱등한_성공_응답을_반환한다() {
        stubActiveMembers();
        when(followRepository.deleteRelation(1L, 15L)).thenReturn(0);
        when(memberRepository.countPublicProfileFollowings(1L))
                .thenReturn(5L);

        MemberUnfollowResult result = memberFollowService.unfollow(1L, 15L);

        assertThat(result.responseCode()).isEqualTo(
                MemberResponseCode.ALREADY_UNFOLLOWED
        );
        assertThat(result.response().isFollowing()).isFalse();
        assertThat(result.response().followingCount()).isEqualTo(5L);
    }

    @Test
    void 본인은_팔로우_해제_대상으로_지정할_수_없다() {
        Member follower = activeMember(1L, "나");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(follower));

        assertError(
                ErrorCode.MEMBER_SELF_UNFOLLOW_NOT_ALLOWED,
                () -> memberFollowService.unfollow(1L, 1L)
        );
        verify(followRepository, never()).deleteRelation(any(), any());
    }

    @Test
    void 팔로우_해제_대상이_없으면_404로_처리한다() {
        when(memberRepository.findById(1L)).thenReturn(
                Optional.of(activeMember(1L, "나"))
        );
        when(memberRepository.findById(15L)).thenReturn(Optional.empty());

        assertError(
                ErrorCode.MEMBER_NOT_FOUND,
                () -> memberFollowService.unfollow(1L, 15L)
        );
        verify(followRepository, never()).deleteRelation(any(), any());
    }

    @Test
    void 탈퇴한_팔로우_해제_대상은_404로_처리한다() {
        Member withdrawnTarget = activeMember(15L, "탈퇴회원");
        ReflectionTestUtils.setField(
                withdrawnTarget,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findById(1L)).thenReturn(
                Optional.of(activeMember(1L, "나"))
        );
        when(memberRepository.findById(15L)).thenReturn(
                Optional.of(withdrawnTarget)
        );

        assertError(
                ErrorCode.MEMBER_NOT_FOUND,
                () -> memberFollowService.unfollow(1L, 15L)
        );
        verify(followRepository, never()).deleteRelation(any(), any());
    }

    @Test
    void 탈퇴한_로그인_회원은_팔로우를_해제할_수_없다() {
        Member withdrawnFollower = activeMember(1L, "탈퇴회원");
        ReflectionTestUtils.setField(
                withdrawnFollower,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findById(1L)).thenReturn(
                Optional.of(withdrawnFollower)
        );

        assertError(
                ErrorCode.AUTH_MEMBER_WITHDRAWN,
                () -> memberFollowService.unfollow(1L, 15L)
        );
        verify(followRepository, never()).deleteRelation(any(), any());
    }

    @Test
    void 팔로잉_목록을_최근순_커서로_조회한다() {
        when(memberRepository.findById(1L)).thenReturn(
                Optional.of(activeMember(1L, "나"))
        );
        FollowingMemberRow newest = followingRow(
                100L,
                15L,
                "임장초보",
                "PALBANG_DOG",
                "THIRTIES",
                true,
                4L,
                "2026-07-25T01:30:00Z"
        );
        FollowingMemberRow second = followingRow(
                90L,
                12L,
                "옥수탐방러",
                "PALBANG_RABBIT",
                "TWENTIES",
                false,
                2L,
                "2026-07-20T05:00:00Z"
        );
        FollowingMemberRow extra = followingRow(
                80L,
                8L,
                "성수탐방러",
                "PALBANG",
                null,
                false,
                1L,
                "2026-07-18T02:00:00Z"
        );
        when(followRepository.findFollowingPage(
                1L,
                null,
                PageRequest.of(0, 3)
        )).thenReturn(List.of(newest, second, extra));
        when(memberRepository.countPublicProfileFollowings(1L))
                .thenReturn(5L);

        MemberFollowingListResponse response = memberFollowService
                .getFollowings(1L, null, 2);

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).memberId()).isEqualTo(15L);
        assertThat(response.content().get(0).ageGroup())
                .isEqualTo("THIRTIES");
        assertThat(response.content().get(0).participatingStudyCount())
                .isEqualTo(4L);
        assertThat(response.content().get(0).isFollowing()).isTrue();
        assertThat(response.content().get(0).canSendMessage()).isTrue();
        assertThat(response.content().get(0).followedAt()).isEqualTo(
                OffsetDateTime.parse("2026-07-25T10:30:00+09:00")
        );
        assertThat(response.content().get(1).ageGroup()).isNull();
        assertThat(response.totalCount()).isEqualTo(5L);
        assertThat(response.nextCursor()).isEqualTo(90L);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    void 마지막_팔로잉_페이지는_다음_커서를_반환하지_않는다() {
        when(memberRepository.findById(1L)).thenReturn(
                Optional.of(activeMember(1L, "나"))
        );
        FollowingMemberRow remaining = followingRow(
                80L,
                8L,
                "성수탐방러",
                "PALBANG",
                null,
                false,
                1L,
                "2026-07-18T02:00:00Z"
        );
        when(followRepository.findFollowingPage(
                1L,
                90L,
                PageRequest.of(0, 3)
        )).thenReturn(List.of(remaining));
        when(memberRepository.countPublicProfileFollowings(1L))
                .thenReturn(1L);

        MemberFollowingListResponse response = memberFollowService
                .getFollowings(1L, 90L, 2);

        assertThat(response.content()).hasSize(1);
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void 팔로잉_목록_커서는_1_이상이어야_한다() {
        assertError(
                ErrorCode.MEMBER_FOLLOWING_CURSOR_INVALID,
                () -> memberFollowService.getFollowings(1L, 0L, 20)
        );
        verify(memberRepository, never()).findById(any());
    }

    @Test
    void 팔로잉_목록_조회_개수는_1부터_100까지다() {
        assertError(
                ErrorCode.INVALID_INPUT_VALUE,
                () -> memberFollowService.getFollowings(1L, null, 101)
        );
        verify(memberRepository, never()).findById(any());
    }

    @Test
    void 탈퇴한_회원의_팔로잉_목록은_404로_처리한다() {
        Member withdrawn = activeMember(1L, "탈퇴회원");
        ReflectionTestUtils.setField(
                withdrawn,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findById(1L)).thenReturn(
                Optional.of(withdrawn)
        );

        assertError(
                ErrorCode.MEMBER_NOT_FOUND,
                () -> memberFollowService.getFollowings(1L, null, 20)
        );
        verify(followRepository, never()).findFollowingPage(
                any(),
                any(),
                any()
        );
    }

    private void stubActiveMembers() {
        when(memberRepository.findById(1L)).thenReturn(
                Optional.of(activeMember(1L, "나"))
        );
        Member target = activeMember(15L, "임장초보");
        target.updateOnboardingProfile(null, false, "PALBANG_DOG");
        when(memberRepository.findById(15L)).thenReturn(Optional.of(target));
    }

    private Member activeMember(Long id, String nickname) {
        Member member = new Member(
                "member" + id + "@example.com",
                "hash",
                nickname
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Follow follow(
            Long followerId,
            Long followingId,
            String createdAt
    ) {
        Follow follow = Follow.of(followerId, followingId);
        ReflectionTestUtils.setField(
                follow,
                "createdAt",
                Instant.parse(createdAt)
        );
        return follow;
    }

    private FollowingMemberRow followingRow(
            Long followId,
            Long memberId,
            String nickname,
            String selectedCharacterId,
            String ageGroup,
            boolean ageGroupPublicAgreed,
            long participatingStudyCount,
            String followedAt
    ) {
        FollowingMemberRow row = mock(FollowingMemberRow.class);
        when(row.getFollowId()).thenReturn(followId);
        when(row.getMemberId()).thenReturn(memberId);
        when(row.getNickname()).thenReturn(nickname);
        when(row.getProfileImageUrl()).thenReturn(null);
        when(row.getSelectedCharacterId()).thenReturn(selectedCharacterId);
        when(row.getAgeGroup()).thenReturn(ageGroup);
        when(row.getAgeGroupPublicAgreed())
                .thenReturn(ageGroupPublicAgreed);
        when(row.getParticipatingStudyCount())
                .thenReturn(participatingStudyCount);
        when(row.getFollowedAt()).thenReturn(Instant.parse(followedAt));
        return row;
    }

    private void assertError(
            ErrorCode expected,
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable
    ) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expected)
                );
    }
}
