package com.ssafy.ssabangpalbang.notification.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.auth.LoginMember;
import com.ssafy.ssabangpalbang.member.auth.LoginMemberResolver;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationListResponse;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationReadAllResponse;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.entity.NotificationCategory;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import com.ssafy.ssabangpalbang.notification.response.NotificationResponseCode;
import com.ssafy.ssabangpalbang.notification.support.NotificationCursor;
import com.ssafy.ssabangpalbang.notification.support.NotificationCursorCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long RECIPIENT_ID = 1L;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private LoginMemberResolver loginMemberResolver;

    @Mock
    private NotificationCursorCodec notificationCursorCodec;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void 미읽음_최신순_페이지와_불투명_다음_커서를_반환한다() {
        activeMember();
        Notification newest = notification(81L, null, false, NotificationCategory.REPORT);
        Notification second = notification(80L, null, false, NotificationCategory.STUDY);
        Notification extra = notification(76L, null, true, NotificationCategory.SYSTEM);
        stubInitialPage(List.of(newest, second, extra));
        when(notificationCursorCodec.encode(any(NotificationCursor.class)))
                .thenReturn("next-opaque-cursor");
        when(notificationRepository.countByRecipientIdAndIsReadFalse(RECIPIENT_ID))
                .thenReturn(2L);

        NotificationListResponse result = notificationService.getNotifications(false, null, 2);

        assertThat(result.content()).extracting(item -> item.notificationId())
                .containsExactly(81L, 80L);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo("next-opaque-cursor");
        assertThat(result.unreadCount()).isEqualTo(2L);

        ArgumentCaptor<NotificationCursor> nextCursorCaptor = ArgumentCaptor.forClass(
                NotificationCursor.class
        );
        verify(notificationCursorCodec).encode(nextCursorCaptor.capture());
        assertThat(nextCursorCaptor.getValue()).satisfies(nextCursor -> {
            assertThat(nextCursor.recipientId()).isEqualTo(RECIPIENT_ID);
            assertThat(nextCursor.snapshotMaxId()).isEqualTo(81L);
            assertThat(nextCursor.unreadOnly()).isFalse();
            assertThat(nextCursor.unreadGroup()).isTrue();
            assertThat(nextCursor.sentAt()).isEqualTo(second.getSentAt());
            assertThat(nextCursor.notificationId()).isEqualTo(second.getId());
        });

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findSnapshotPage(
                eq(RECIPIENT_ID),
                eq(81L),
                any(OffsetDateTime.class),
                eq(false),
                isNull(),
                isNull(),
                isNull(),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void 커서의_스냅샷과_미읽음_필터를_저장소에_전달한다() {
        activeMember();
        OffsetDateTime snapshotAt = OffsetDateTime.parse("2026-08-03T10:00:00+09:00");
        OffsetDateTime lastSentAt = OffsetDateTime.parse("2026-08-02T09:00:00+09:00");
        NotificationCursor decodedCursor = NotificationCursor.of(
                RECIPIENT_ID,
                81L,
                snapshotAt,
                true,
                true,
                lastSentAt,
                81L
        );
        when(notificationCursorCodec.decode("opaque-cursor", RECIPIENT_ID, true))
                .thenReturn(decodedCursor);
        when(notificationRepository.findSnapshotPage(
                anyLong(), anyLong(), any(), anyBoolean(), anyBoolean(), any(), anyLong(), any()
        ))
                .thenReturn(List.of(notification(80L, null, false, NotificationCategory.STUDY)));
        when(notificationRepository.countByRecipientIdAndIsReadFalse(RECIPIENT_ID))
                .thenReturn(4L);

        NotificationListResponse result = notificationService.getNotifications(
                true,
                "opaque-cursor",
                20
        );

        verify(notificationRepository).findSnapshotPage(
                eq(RECIPIENT_ID),
                eq(81L),
                eq(snapshotAt),
                eq(true),
                eq(true),
                eq(lastSentAt),
                eq(81L),
                any(Pageable.class)
        );
        assertThat(result.content()).hasSize(1);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.unreadCount()).isEqualTo(4L);
    }

    @Test
    void 발신자는_페이지당_한번의_배치_조회로_매핑한다() {
        activeMember();
        Notification first = notification(81L, 12L, false, NotificationCategory.MESSAGE);
        Notification second = notification(80L, 12L, true, NotificationCategory.STUDY);
        Member actor = member(12L, "옥수탐방러", "JIPKONG");
        stubInitialPage(List.of(first, second));
        when(notificationRepository.countByRecipientIdAndIsReadFalse(RECIPIENT_ID))
                .thenReturn(1L);
        when(memberRepository.findAllById(any())).thenReturn(List.of(actor));

        NotificationListResponse result = notificationService.getNotifications(false, null, 20);

        verify(memberRepository, times(1)).findAllById(Set.of(12L));
        assertThat(result.content().get(0).actor())
                .extracting(
                        response -> response.memberId(),
                        response -> response.nickname(),
                        response -> response.selectedCharacterId()
                )
                .containsExactly(12L, "옥수탐방러", "JIPKONG");
        assertThat(result.content().get(1).actor().memberId()).isEqualTo(12L);
    }

    @Test
    void 발신자가_없는_알림은_actor를_null로_반환한다() {
        activeMember();
        stubInitialPage(List.of(notification(
                81L,
                null,
                false,
                NotificationCategory.REPORT
        )));
        when(notificationRepository.countByRecipientIdAndIsReadFalse(RECIPIENT_ID))
                .thenReturn(1L);

        NotificationListResponse result = notificationService.getNotifications(false, null, 20);

        verify(memberRepository, never()).findAllById(any());
        assertThat(result.content().get(0).actor()).isNull();
        assertThat(result.content().get(0).targetAvailable()).isTrue();
    }

    @Test
    void 잘못된_커서는_전용_오류를_반환한다() {
        activeMember();
        when(notificationCursorCodec.decode("tampered", RECIPIENT_ID, false))
                .thenThrow(new BusinessException(ErrorCode.NOTIFICATION_CURSOR_INVALID));

        assertThatThrownBy(() -> notificationService.getNotifications(false, "tampered", 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOTIFICATION_CURSOR_INVALID);

        verifyNoInteractions(notificationRepository, memberRepository);
    }

    @Test
    void 범위를_벗어난_조회_개수는_공통_입력_오류를_반환한다() {
        assertThatThrownBy(() -> notificationService.getNotifications(false, null, 101))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(notificationRepository, memberRepository, loginMemberResolver);
    }

    @Test
    void 비활성_회원은_알림을_조회할_수_없다() {
        when(loginMemberResolver.resolve()).thenReturn(new LoginMember(RECIPIENT_ID, false));

        assertThatThrownBy(() -> notificationService.getNotifications(false, null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verifyNoInteractions(notificationRepository, memberRepository);
    }

    @Test
    void 알림이_없으면_빈_목록과_미읽음_수_0을_반환한다() {
        activeMember();
        stubInitialPage(List.of());
        when(notificationRepository.countByRecipientIdAndIsReadFalse(RECIPIENT_ID))
                .thenReturn(0L);

        NotificationListResponse result = notificationService.getNotifications(false, null, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.unreadCount()).isZero();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        verifyNoInteractions(memberRepository);
    }

    @Test
    void 읽지_않은_알림을_읽음_처리하고_갱신된_미읽음_수를_반환한다() {
        activeMember();
        Notification notification = notification(
                81L,
                null,
                false,
                NotificationCategory.REPORT
        );
        when(notificationRepository.findByIdAndRecipientId(81L, RECIPIENT_ID))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.countByRecipientIdAndIsReadFalse(RECIPIENT_ID))
                .thenReturn(2L);

        NotificationService.ReadResult result = notificationService.readNotification("81");

        assertThat(result.responseCode())
                .isEqualTo(NotificationResponseCode.NOTIFICATION_READ_SUCCESS);
        assertThat(result.response().notificationId()).isEqualTo(81L);
        assertThat(result.response().isRead()).isTrue();
        assertThat(result.response().readAt()).isNotNull();
        assertThat(result.response().readAt().getOffset())
                .isEqualTo(ZoneOffset.ofHours(9));
        assertThat(result.response().readAt()).isEqualTo(notification.getReadAt());
        assertThat(result.response().unreadCount()).isEqualTo(2L);
        verify(notificationRepository).findByIdAndRecipientId(81L, RECIPIENT_ID);
        verify(notificationRepository).countByRecipientIdAndIsReadFalse(RECIPIENT_ID);
        verify(notificationRepository, never()).save(any(Notification.class));
        verifyNoInteractions(memberRepository);
    }

    @Test
    void 이미_읽은_알림은_기존_읽음_시각을_유지한다() {
        activeMember();
        OffsetDateTime firstReadAt = OffsetDateTime.of(
                2026,
                7,
                22,
                10,
                30,
                0,
                0,
                ZoneOffset.ofHours(9)
        );
        Notification notification = notification(
                81L,
                null,
                true,
                NotificationCategory.REPORT
        );
        ReflectionTestUtils.setField(notification, "readAt", firstReadAt);
        when(notificationRepository.findByIdAndRecipientId(81L, RECIPIENT_ID))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.countByRecipientIdAndIsReadFalse(RECIPIENT_ID))
                .thenReturn(2L);

        NotificationService.ReadResult result = notificationService.readNotification("81");

        assertThat(result.responseCode())
                .isEqualTo(NotificationResponseCode.NOTIFICATION_ALREADY_READ);
        assertThat(result.response().readAt()).isEqualTo(firstReadAt);
        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
        assertThat(result.response().unreadCount()).isEqualTo(2L);
        verify(notificationRepository, never()).save(any(Notification.class));
        verifyNoInteractions(memberRepository);
    }

    @Test
    void 같은_알림을_연속으로_읽어도_최초_읽음_시각을_유지한다() {
        activeMember();
        Notification notification = notification(
                81L,
                null,
                false,
                NotificationCategory.REPORT
        );
        when(notificationRepository.findByIdAndRecipientId(81L, RECIPIENT_ID))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.countByRecipientIdAndIsReadFalse(RECIPIENT_ID))
                .thenReturn(2L);

        NotificationService.ReadResult first = notificationService.readNotification("81");
        NotificationService.ReadResult second = notificationService.readNotification("81");

        assertThat(first.responseCode())
                .isEqualTo(NotificationResponseCode.NOTIFICATION_READ_SUCCESS);
        assertThat(second.responseCode())
                .isEqualTo(NotificationResponseCode.NOTIFICATION_ALREADY_READ);
        assertThat(second.response().readAt()).isEqualTo(first.response().readAt());
        verify(notificationRepository, times(2))
                .findByIdAndRecipientId(81L, RECIPIENT_ID);
        verify(notificationRepository, times(2))
                .countByRecipientIdAndIsReadFalse(RECIPIENT_ID);
        verify(notificationRepository, never()).save(any(Notification.class));
        verifyNoInteractions(memberRepository);
    }

    @Test
    void 다른_회원의_알림은_찾을_수_없는_알림으로_처리한다() {
        activeMember();
        when(notificationRepository.findByIdAndRecipientId(81L, RECIPIENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.readNotification("81"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);

        verify(notificationRepository).findByIdAndRecipientId(81L, RECIPIENT_ID);
        verify(notificationRepository, never())
                .countByRecipientIdAndIsReadFalse(anyLong());
        verifyNoInteractions(memberRepository);
    }

    @Test
    void 존재하지_않는_알림은_찾을_수_없는_알림으로_처리한다() {
        activeMember();
        when(notificationRepository.findByIdAndRecipientId(81L, RECIPIENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.readNotification("81"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);

        verify(notificationRepository).findByIdAndRecipientId(81L, RECIPIENT_ID);
        verifyNoInteractions(memberRepository);
    }

    @Test
    void 숫자가_아니거나_1보다_작은_알림_ID는_전용_오류를_반환한다() {
        for (String notificationId : List.of("abc", "0", "-1")) {
            assertThatThrownBy(() -> notificationService.readNotification(notificationId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOTIFICATION_ID_INVALID);
        }

        verifyNoInteractions(notificationRepository, memberRepository, loginMemberResolver);
    }

    @Test
    void 비활성_회원은_알림을_읽음_처리할_수_없다() {
        when(loginMemberResolver.resolve()).thenReturn(new LoginMember(RECIPIENT_ID, false));

        assertThatThrownBy(() -> notificationService.readNotification("81"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verifyNoInteractions(notificationRepository, memberRepository);
    }

    @Test
    void 읽지_않은_알림을_벌크로_모두_읽음_처리한다() {
        activeMember();
        when(notificationRepository.markAllAsReadByRecipientId(
                eq(RECIPIENT_ID),
                any(OffsetDateTime.class)
        )).thenReturn(5);
        when(notificationRepository.countByRecipientIdAndIsReadFalse(RECIPIENT_ID))
                .thenReturn(0L);

        NotificationService.ReadAllResult result = notificationService.readAllNotifications();

        assertThat(result.responseCode())
                .isEqualTo(NotificationResponseCode.NOTIFICATION_READ_ALL_SUCCESS);
        NotificationReadAllResponse response = result.response();
        assertThat(response.updatedCount()).isEqualTo(5);
        assertThat(response.unreadCount()).isZero();
        assertThat(response.readAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        ArgumentCaptor<OffsetDateTime> readAtCaptor = ArgumentCaptor.forClass(
                OffsetDateTime.class
        );
        verify(notificationRepository).markAllAsReadByRecipientId(
                eq(RECIPIENT_ID),
                readAtCaptor.capture()
        );
        assertThat(response.readAt()).isEqualTo(readAtCaptor.getValue());
        verify(notificationRepository).countByRecipientIdAndIsReadFalse(RECIPIENT_ID);
        verifyNoInteractions(memberRepository);
    }

    @Test
    void 미읽음_알림이_없는_회원은_이미_모두_읽음_응답을_반환한다() {
        activeMember();
        when(notificationRepository.markAllAsReadByRecipientId(
                eq(RECIPIENT_ID),
                any(OffsetDateTime.class)
        )).thenReturn(0);
        when(notificationRepository.countByRecipientIdAndIsReadFalse(RECIPIENT_ID))
                .thenReturn(0L);

        NotificationService.ReadAllResult result = notificationService.readAllNotifications();

        assertThat(result.responseCode())
                .isEqualTo(NotificationResponseCode.NOTIFICATION_ALREADY_ALL_READ);
        assertThat(result.response().updatedCount()).isZero();
        assertThat(result.response().unreadCount()).isZero();
        assertThat(result.response().readAt().getOffset())
                .isEqualTo(ZoneOffset.ofHours(9));
        verify(notificationRepository).markAllAsReadByRecipientId(
                eq(RECIPIENT_ID),
                any(OffsetDateTime.class)
        );
        verify(notificationRepository).countByRecipientIdAndIsReadFalse(RECIPIENT_ID);
        verifyNoInteractions(memberRepository);
    }

    @Test
    void 전체_읽음_요청을_반복하면_두번째_요청은_이미_읽음으로_처리한다() {
        activeMember();
        when(notificationRepository.markAllAsReadByRecipientId(
                eq(RECIPIENT_ID),
                any(OffsetDateTime.class)
        )).thenReturn(5, 0);
        when(notificationRepository.countByRecipientIdAndIsReadFalse(RECIPIENT_ID))
                .thenReturn(0L);

        NotificationService.ReadAllResult first = notificationService.readAllNotifications();
        NotificationService.ReadAllResult second = notificationService.readAllNotifications();

        assertThat(first.responseCode())
                .isEqualTo(NotificationResponseCode.NOTIFICATION_READ_ALL_SUCCESS);
        assertThat(second.responseCode())
                .isEqualTo(NotificationResponseCode.NOTIFICATION_ALREADY_ALL_READ);
        assertThat(second.response().updatedCount()).isZero();
        verify(notificationRepository, times(2)).markAllAsReadByRecipientId(
                eq(RECIPIENT_ID),
                any(OffsetDateTime.class)
        );
        verify(notificationRepository, times(2))
                .countByRecipientIdAndIsReadFalse(RECIPIENT_ID);
        verifyNoInteractions(memberRepository);
    }

    @Test
    void 비활성_회원은_전체_읽음_처리를_할_수_없다() {
        when(loginMemberResolver.resolve()).thenReturn(new LoginMember(RECIPIENT_ID, false));

        assertThatThrownBy(notificationService::readAllNotifications)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verifyNoInteractions(notificationRepository, memberRepository);
    }

    private void activeMember() {
        when(loginMemberResolver.resolve()).thenReturn(new LoginMember(RECIPIENT_ID, true));
    }

    private void stubInitialPage(List<Notification> notifications) {
        Optional<Long> maxId = notifications.stream()
                .map(Notification::getId)
                .max(Long::compareTo);
        when(notificationRepository.findMaxIdByRecipientId(RECIPIENT_ID))
                .thenReturn(maxId);
        when(notificationRepository.findSnapshotPage(
                eq(RECIPIENT_ID),
                eq(maxId.orElse(0L)),
                any(OffsetDateTime.class),
                anyBoolean(),
                isNull(),
                isNull(),
                isNull(),
                any(Pageable.class)
        )).thenReturn(notifications);
    }

    private Notification notification(
            Long id,
            Long actorId,
            boolean isRead,
            NotificationCategory category
    ) {
        Notification notification = newNotification();
        ReflectionTestUtils.setField(notification, "id", id);
        ReflectionTestUtils.setField(notification, "recipientId", RECIPIENT_ID);
        ReflectionTestUtils.setField(notification, "actorId", actorId);
        ReflectionTestUtils.setField(notification, "category", category);
        ReflectionTestUtils.setField(notification, "type", category == NotificationCategory.MESSAGE
                ? "MESSAGE"
                : "REPORT_COMPLETED");
        ReflectionTestUtils.setField(notification, "title", "알림 제목");
        ReflectionTestUtils.setField(notification, "body", "알림 내용");
        ReflectionTestUtils.setField(notification, "isRead", isRead);
        ReflectionTestUtils.setField(notification, "targetScreen", "REPORT_DETAIL");
        ReflectionTestUtils.setField(notification, "targetId", 48L);
        ReflectionTestUtils.setField(notification, "sentAt", OffsetDateTime.now(ZoneOffset.ofHours(9)));
        return notification;
    }

    private Notification newNotification() {
        try {
            Constructor<Notification> constructor = Notification.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private Member member(
            Long id,
            String nickname,
            String selectedCharacterId
    ) {
        Member member = new Member("actor" + id + "@example.com", "password", nickname);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "profileImageUrl", "https://example.com/profile.png");
        ReflectionTestUtils.setField(member, "selectedCharacterId", selectedCharacterId);
        return member;
    }
}
