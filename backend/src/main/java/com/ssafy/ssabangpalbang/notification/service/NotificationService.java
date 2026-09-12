package com.ssafy.ssabangpalbang.notification.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.auth.LoginMember;
import com.ssafy.ssabangpalbang.member.auth.LoginMemberResolver;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationActorResponse;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationItemResponse;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationListResponse;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationReadAllResponse;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationReadResponse;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import com.ssafy.ssabangpalbang.notification.response.NotificationResponseCode;
import com.ssafy.ssabangpalbang.notification.support.NotificationCursor;
import com.ssafy.ssabangpalbang.notification.support.NotificationCursorCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final LoginMemberResolver loginMemberResolver;
    private final NotificationCursorCodec notificationCursorCodec;

    public NotificationListResponse getNotifications(
            boolean unreadOnly,
            String cursor,
            int size
    ) {
        validateSize(size);

        LoginMember loginMember = loginMemberResolver.resolve();
        if (!loginMember.active()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        NotificationCursor snapshotCursor = cursor == null
                ? initialCursor(loginMember.memberId(), unreadOnly)
                : notificationCursorCodec.decode(
                        cursor,
                        loginMember.memberId(),
                        unreadOnly
                );
        boolean firstPage = cursor == null;

        List<Notification> notifications = notificationRepository.findSnapshotPage(
                loginMember.memberId(),
                snapshotCursor.snapshotMaxId(),
                snapshotCursor.snapshotAt(),
                unreadOnly,
                firstPage ? null : snapshotCursor.unreadGroup(),
                firstPage ? null : snapshotCursor.sentAt(),
                firstPage ? null : snapshotCursor.notificationId(),
                PageRequest.of(0, size + 1)
        );
        boolean hasNext = notifications.size() > size;
        List<Notification> pageNotifications = hasNext
                ? notifications.subList(0, size)
                : notifications;
        Map<Long, NotificationActorResponse> actorsById = findActors(pageNotifications);
        List<NotificationItemResponse> content = pageNotifications.stream()
                .map(notification -> NotificationItemResponse.from(
                        notification,
                        findActor(actorsById, notification.getActorId())
                ))
                .toList();
        String nextCursor = hasNext && !pageNotifications.isEmpty()
                ? encodeNextCursor(
                        snapshotCursor,
                        unreadOnly,
                        pageNotifications.get(pageNotifications.size() - 1)
                )
                : null;

        return new NotificationListResponse(
                content,
                notificationRepository.countByRecipientIdAndIsReadFalse(loginMember.memberId()),
                nextCursor,
                hasNext
        );
    }

    private NotificationCursor initialCursor(
            long recipientId,
            boolean unreadOnly
    ) {
        long snapshotMaxId = notificationRepository
                .findMaxIdByRecipientId(recipientId)
                .orElse(0L);
        OffsetDateTime snapshotAt = OffsetDateTime.now(SEOUL_ZONE_ID);

        return NotificationCursor.of(
                recipientId,
                snapshotMaxId,
                snapshotAt,
                unreadOnly,
                true,
                snapshotAt,
                Math.max(snapshotMaxId, 1L)
        );
    }

    private String encodeNextCursor(
            NotificationCursor snapshotCursor,
            boolean unreadOnly,
            Notification lastNotification
    ) {
        return notificationCursorCodec.encode(NotificationCursor.of(
                snapshotCursor.recipientId(),
                snapshotCursor.snapshotMaxId(),
                snapshotCursor.snapshotAt(),
                unreadOnly,
                isUnreadAtSnapshot(lastNotification, snapshotCursor.snapshotAt()),
                lastNotification.getSentAt(),
                lastNotification.getId()
        ));
    }

    private boolean isUnreadAtSnapshot(
            Notification notification,
            OffsetDateTime snapshotAt
    ) {
        return !notification.isRead()
                || notification.getReadAt() != null
                && notification.getReadAt().isAfter(snapshotAt);
    }

    @Transactional
    public ReadResult readNotification(String notificationId) {
        Long parsedNotificationId = parseNotificationId(notificationId);
        LoginMember loginMember = loginMemberResolver.resolve();

        if (!loginMember.active()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        Notification notification = notificationRepository
                .findByIdAndRecipientId(parsedNotificationId, loginMember.memberId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOTIFICATION_NOT_FOUND
                ));
        boolean wasAlreadyRead = notification.isRead();
        notification.markAsRead(OffsetDateTime.now(SEOUL_ZONE_ID));

        NotificationReadResponse response = new NotificationReadResponse(
                notification.getId(),
                notification.isRead(),
                notification.getReadAt(),
                notificationRepository.countByRecipientIdAndIsReadFalse(
                        loginMember.memberId()
                )
        );
        NotificationResponseCode responseCode = wasAlreadyRead
                ? NotificationResponseCode.NOTIFICATION_ALREADY_READ
                : NotificationResponseCode.NOTIFICATION_READ_SUCCESS;

        return new ReadResult(responseCode, response);
    }

    @Transactional
    public ReadAllResult readAllNotifications() {
        LoginMember loginMember = loginMemberResolver.resolve();
        if (!loginMember.active()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        OffsetDateTime now = OffsetDateTime.now(SEOUL_ZONE_ID);
        int updatedCount = notificationRepository.markAllAsReadByRecipientId(
                loginMember.memberId(),
                now
        );
        long unreadCount = notificationRepository.countByRecipientIdAndIsReadFalse(
                loginMember.memberId()
        );
        NotificationReadAllResponse response = new NotificationReadAllResponse(
                updatedCount,
                unreadCount,
                now
        );
        NotificationResponseCode responseCode = updatedCount > 0
                ? NotificationResponseCode.NOTIFICATION_READ_ALL_SUCCESS
                : NotificationResponseCode.NOTIFICATION_ALREADY_ALL_READ;

        return new ReadAllResult(responseCode, response);
    }

    private Map<Long, NotificationActorResponse> findActors(
            Collection<Notification> notifications
    ) {
        Set<Long> actorIds = notifications.stream()
                .map(Notification::getActorId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        if (actorIds.isEmpty()) {
            return Map.of();
        }

        return memberRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(
                        Member::getId,
                        member -> new NotificationActorResponse(
                                member.getId(),
                                member.getNickname(),
                                member.getProfileImageUrl(),
                                member.getSelectedCharacterId()
                        )
                ));
    }

    private NotificationActorResponse findActor(
            Map<Long, NotificationActorResponse> actorsById,
            Long actorId
    ) {
        return actorId == null ? null : actorsById.get(actorId);
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

    private Long parseNotificationId(String notificationId) {
        try {
            if (notificationId == null) {
                throw new NumberFormatException();
            }

            long parsedNotificationId = Long.parseLong(notificationId);
            if (parsedNotificationId < 1) {
                throw new NumberFormatException();
            }

            return parsedNotificationId;
        } catch (NumberFormatException exception) {
            throw new BusinessException(
                    ErrorCode.NOTIFICATION_ID_INVALID,
                    Map.of(
                            "field", "notificationId",
                            "reason", "알림 ID는 1 이상의 숫자여야 합니다."
                    )
            );
        }
    }

    public record ReadResult(
            NotificationResponseCode responseCode,
            NotificationReadResponse response
    ) {
    }

    public record ReadAllResult(
            NotificationResponseCode responseCode,
            NotificationReadAllResponse response
    ) {
    }
}
