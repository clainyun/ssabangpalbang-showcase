package com.ssafy.ssabangpalbang.notification.repository;

import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.entity.NotificationCategory;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class NotificationRepositoryTest {

    private static final Long RECIPIENT_ID = 1L;
    private static final Long OTHER_RECIPIENT_ID = 2L;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void 스냅샷_읽음상태로_미읽음_최신순_다음_읽음_최신순을_페이지_조회한다() {
        OffsetDateTime snapshotAt = OffsetDateTime.parse(
                "2026-08-03T10:00:00+09:00"
        );
        Notification unreadTieOlderId = persistNotification(
                RECIPIENT_ID,
                false,
                null,
                "unread-tie-older-id",
                OffsetDateTime.parse("2026-08-01T08:00:00+09:00")
        );
        Notification unreadTieNewerId = persistNotification(
                RECIPIENT_ID,
                false,
                null,
                "unread-tie-newer-id",
                OffsetDateTime.parse("2026-08-01T08:00:00+09:00")
        );
        Notification unreadNewest = persistNotification(
                RECIPIENT_ID,
                false,
                null,
                "unread-newest",
                OffsetDateTime.parse("2026-08-02T09:00:00+09:00")
        );
        Notification readAfterSnapshot = persistNotification(
                RECIPIENT_ID,
                true,
                snapshotAt.plusMinutes(1),
                "read-after-snapshot",
                OffsetDateTime.parse("2026-08-02T10:30:00+09:00")
        );
        Notification readNewest = persistNotification(
                RECIPIENT_ID,
                true,
                snapshotAt.minusDays(1),
                "read-newest",
                OffsetDateTime.parse("2026-08-02T11:00:00+09:00")
        );
        Notification readOlder = persistNotification(
                RECIPIENT_ID,
                true,
                snapshotAt.minusDays(1),
                "read-older",
                OffsetDateTime.parse("2026-07-31T11:00:00+09:00")
        );
        persistNotification(
                OTHER_RECIPIENT_ID,
                false,
                null,
                "other-recipient",
                OffsetDateTime.parse("2026-08-03T12:00:00+09:00")
        );
        entityManager.flush();
        long snapshotMaxId = readOlder.getId();
        Notification insertedLater = persistNotification(
                RECIPIENT_ID,
                false,
                null,
                "inserted-later",
                OffsetDateTime.parse("2026-08-03T13:00:00+09:00")
        );
        entityManager.flush();

        List<Notification> firstPage = notificationRepository.findSnapshotPage(
                RECIPIENT_ID,
                snapshotMaxId,
                snapshotAt,
                false,
                null,
                null,
                null,
                PageRequest.of(0, 3)
        );
        Notification lastFirstPage = firstPage.get(firstPage.size() - 1);
        List<Notification> secondPage = notificationRepository.findSnapshotPage(
                RECIPIENT_ID,
                snapshotMaxId,
                snapshotAt,
                false,
                true,
                lastFirstPage.getSentAt(),
                lastFirstPage.getId(),
                PageRequest.of(0, 10)
        );

        assertThat(firstPage).extracting(Notification::getId).containsExactly(
                readAfterSnapshot.getId(),
                unreadNewest.getId(),
                unreadTieNewerId.getId()
        );
        assertThat(secondPage).extracting(Notification::getId).containsExactly(
                unreadTieOlderId.getId(),
                readNewest.getId(),
                readOlder.getId()
        );
        assertThat(firstPage).allMatch(
                notification -> !notification.getId().equals(insertedLater.getId())
        );
        assertThat(secondPage).allMatch(
                notification -> !notification.getId().equals(insertedLater.getId())
        );
    }

    @Test
    void 수신자의_미읽음_알림만_일괄_읽음_처리하고_기존_읽음_시각을_유지한다() {
        OffsetDateTime firstReadAt = OffsetDateTime.of(
                2026,
                7,
                22,
                9,
                0,
                0,
                0,
                ZoneOffset.ofHours(9)
        );
        OffsetDateTime readAllAt = OffsetDateTime.of(
                2026,
                7,
                22,
                10,
                30,
                0,
                0,
                ZoneOffset.ofHours(9)
        );
        Notification unread = persistNotification(
                RECIPIENT_ID,
                false,
                null,
                "recipient-unread"
        );
        Notification alreadyRead = persistNotification(
                RECIPIENT_ID,
                true,
                firstReadAt,
                "recipient-read"
        );
        Notification otherMemberUnread = persistNotification(
                OTHER_RECIPIENT_ID,
                false,
                null,
                "other-unread"
        );
        entityManager.flush();

        int updatedCount = notificationRepository.markAllAsReadByRecipientId(
                RECIPIENT_ID,
                readAllAt
        );
        entityManager.flush();
        entityManager.clear();

        Notification updatedUnread = entityManager.find(Notification.class, unread.getId());
        Notification unchangedRead = entityManager.find(Notification.class, alreadyRead.getId());
        Notification unchangedOther = entityManager.find(
                Notification.class,
                otherMemberUnread.getId()
        );

        assertThat(updatedCount).isEqualTo(1);
        assertThat(updatedUnread.isRead()).isTrue();
        assertThat(updatedUnread.getReadAt()).isEqualTo(readAllAt);
        assertThat(unchangedRead.isRead()).isTrue();
        assertThat(unchangedRead.getReadAt()).isEqualTo(firstReadAt);
        assertThat(unchangedOther.isRead()).isFalse();
        assertThat(unchangedOther.getReadAt()).isNull();
        assertThat(notificationRepository.countByRecipientIdAndIsReadFalse(RECIPIENT_ID))
                .isZero();
    }

    @Test
    void 쪽지_idempotencyKey로_저장된_알림을_다시_조회한다() {
        OffsetDateTime sentAt = OffsetDateTime.parse(
                "2026-07-25T11:00:00+09:00"
        );
        String idempotencyKey =
                "member-message:1:8e70e108-7c81-477a-bb55-a9f334fb5e67";
        Notification saved = notificationRepository.saveAndFlush(
                Notification.message(
                        12L,
                        1L,
                        "새로운 메시지가 도착했어요",
                        "다음 임장도 같이 참여해요!",
                        idempotencyKey,
                        sentAt
                )
        );
        entityManager.clear();

        Notification found = notificationRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getRecipientId()).isEqualTo(12L);
        assertThat(found.getActorId()).isEqualTo(1L);
        assertThat(found.getCategory())
                .isEqualTo(NotificationCategory.MESSAGE);
        assertThat(found.getType()).isEqualTo("MESSAGE");
        assertThat(found.getTargetScreen()).isEqualTo("MEMBER_PROFILE");
        assertThat(found.getTargetId()).isEqualTo(1L);
        assertThat(found.getTargetSubId()).isNull();
        assertThat(found.getBody()).isEqualTo("다음 임장도 같이 참여해요!");
        assertThat(found.getSentAt()).isEqualTo(sentAt);
    }

    private Notification persistNotification(
            Long recipientId,
            boolean isRead,
            OffsetDateTime readAt,
            String idempotencyKey
    ) {
        return persistNotification(
                recipientId,
                isRead,
                readAt,
                idempotencyKey,
                OffsetDateTime.now(ZoneOffset.ofHours(9))
        );
    }

    private Notification persistNotification(
            Long recipientId,
            boolean isRead,
            OffsetDateTime readAt,
            String idempotencyKey,
            OffsetDateTime sentAt
    ) {
        Notification notification = newNotification();
        ReflectionTestUtils.setField(notification, "recipientId", recipientId);
        ReflectionTestUtils.setField(notification, "category", NotificationCategory.SYSTEM);
        ReflectionTestUtils.setField(notification, "type", "SYSTEM_NOTICE");
        ReflectionTestUtils.setField(notification, "title", "알림 제목");
        ReflectionTestUtils.setField(notification, "body", "알림 내용");
        ReflectionTestUtils.setField(notification, "isRead", isRead);
        ReflectionTestUtils.setField(notification, "readAt", readAt);
        ReflectionTestUtils.setField(notification, "idempotencyKey", idempotencyKey);
        ReflectionTestUtils.setField(notification, "sentAt", sentAt);
        entityManager.persist(notification);
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
}
