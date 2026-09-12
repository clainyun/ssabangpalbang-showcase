package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Follow;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFollowResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFollowResult;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFollowingListResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFollowingResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberUnfollowResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberUnfollowResult;
import com.ssafy.ssabangpalbang.member.event.MemberFollowPushRequestedEvent;
import com.ssafy.ssabangpalbang.member.repository.FollowRepository;
import com.ssafy.ssabangpalbang.member.repository.FollowingMemberRow;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.entity.NotificationCategory;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemberFollowService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final String FOLLOW_NOTIFICATION_TYPE = "MEMBER_FOLLOWED";
    private static final String FOLLOW_NOTIFICATION_TITLE = "새로운 팔로워가 생겼어요";

    private final MemberRepository memberRepository;
    private final FollowRepository followRepository;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public MemberFollowResult follow(Long followerId, Long followingId) {
        Member follower = memberRepository.findById(followerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
        validateActiveFollower(follower);
        if (followerId.equals(followingId)) {
            throw new BusinessException(
                    ErrorCode.MEMBER_SELF_FOLLOW_NOT_ALLOWED
            );
        }

        Member targetMember = memberRepository.findById(followingId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
        validateActiveTarget(targetMember);

        return followRepository.findByFollowerIdAndFollowingId(
                        followerId,
                        followingId
                )
                .map(follow -> result(
                        MemberResponseCode.ALREADY_FOLLOWING,
                        targetMember,
                        follow
                ))
                .orElseGet(() -> createFollow(
                        follower,
                        targetMember
                ));
    }

    @Transactional
    public MemberUnfollowResult unfollow(Long followerId, Long followingId) {
        Member follower = memberRepository.findById(followerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
        validateActiveFollower(follower);
        if (followerId.equals(followingId)) {
            throw new BusinessException(
                    ErrorCode.MEMBER_SELF_UNFOLLOW_NOT_ALLOWED
            );
        }

        Member targetMember = memberRepository.findById(followingId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
        validateActiveTarget(targetMember);

        int deletedCount = followRepository.deleteRelation(
                followerId,
                followingId
        );
        MemberResponseCode responseCode = deletedCount > 0
                ? MemberResponseCode.UNFOLLOWED
                : MemberResponseCode.ALREADY_UNFOLLOWED;
        return new MemberUnfollowResult(
                responseCode,
                MemberUnfollowResponse.of(
                        targetMember,
                        memberRepository.countPublicProfileFollowings(
                                followerId
                        ),
                        Instant.now()
                )
        );
    }

    @Transactional(readOnly = true)
    public MemberFollowingListResponse getFollowings(
            Long memberId,
            Long cursor,
            int size
    ) {
        validateCursor(cursor);
        validateSize(size);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
        validateListMember(member);

        List<FollowingMemberRow> rows = followRepository.findFollowingPage(
                memberId,
                cursor,
                PageRequest.of(0, size + 1)
        );
        boolean hasNext = rows.size() > size;
        List<FollowingMemberRow> pageRows = hasNext
                ? rows.subList(0, size)
                : rows;
        List<MemberFollowingResponse> content = pageRows.stream()
                .map(MemberFollowingResponse::from)
                .toList();
        Long nextCursor = hasNext && !pageRows.isEmpty()
                ? pageRows.get(pageRows.size() - 1).getFollowId()
                : null;

        return new MemberFollowingListResponse(
                content,
                memberRepository.countPublicProfileFollowings(memberId),
                nextCursor,
                hasNext
        );
    }

    private MemberFollowResult createFollow(
            Member follower,
            Member targetMember
    ) {
        Follow follow;
        MemberResponseCode responseCode = MemberResponseCode.FOLLOWED;
        try {
            follow = saveFollowAndNotificationInNewTransaction(
                    follower,
                    targetMember
            );
        } catch (DataIntegrityViolationException exception) {
            follow = followRepository.findByFollowerIdAndFollowingId(
                            follower.getId(),
                            targetMember.getId()
                    )
                    .orElseThrow(() -> exception);
            responseCode = MemberResponseCode.ALREADY_FOLLOWING;
        }
        return result(responseCode, targetMember, follow);
    }

    private Follow saveFollowAndNotificationInNewTransaction(
            Member follower,
            Member targetMember
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(
                transactionManager
        );
        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        return transactionTemplate.execute(status -> {
            Follow follow = followRepository.saveAndFlush(
                    Follow.of(follower.getId(), targetMember.getId())
            );
            String body = "'%s'님이 회원님을 팔로우하기 시작했어요.".formatted(
                    follower.getNickname()
            );
            Notification notification = notificationRepository.saveAndFlush(
                    Notification.create(
                            targetMember.getId(),
                            follower.getId(),
                            NotificationCategory.COMMUNITY,
                            FOLLOW_NOTIFICATION_TYPE,
                            "MEMBER_PROFILE",
                            follower.getId(),
                            null,
                            FOLLOW_NOTIFICATION_TITLE,
                            body,
                            FOLLOW_NOTIFICATION_TYPE + ":" + follow.getId(),
                            OffsetDateTime.now(SEOUL_ZONE_ID)
                    )
            );
            eventPublisher.publishEvent(new MemberFollowPushRequestedEvent(
                    notification.getId(),
                    targetMember.getId(),
                    follower.getId(),
                    targetMember.isServiceNotificationAgreed(),
                    FOLLOW_NOTIFICATION_TITLE,
                    body
            ));
            return follow;
        });
    }

    private MemberFollowResult result(
            MemberResponseCode responseCode,
            Member targetMember,
            Follow follow
    ) {
        return new MemberFollowResult(
                responseCode,
                MemberFollowResponse.of(
                        targetMember,
                        follow,
                        memberRepository.countPublicProfileFollowings(
                                follow.getFollowerId()
                        )
                )
        );
    }

    private void validateActiveFollower(Member member) {
        if (member.getStatus() != MemberStatus.ACTIVE
                || member.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.AUTH_MEMBER_WITHDRAWN);
        }
    }

    private void validateActiveTarget(Member member) {
        if (member.getStatus() != MemberStatus.ACTIVE
                || member.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    private void validateListMember(Member member) {
        if (member.getStatus() != MemberStatus.ACTIVE
                || member.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    private void validateCursor(Long cursor) {
        if (cursor != null && cursor < 1) {
            throw invalidCursor();
        }
    }

    private void validateSize(int size) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of(
                            "field", "size",
                            "reason", "조회 개수는 1 이상 100 이하이어야 합니다."
                    )
            );
        }
    }

    private BusinessException invalidCursor() {
        return new BusinessException(
                ErrorCode.MEMBER_FOLLOWING_CURSOR_INVALID,
                Map.of(
                        "field", "cursor",
                        "reason", "커서는 1 이상의 숫자여야 합니다."
                )
        );
    }
}
