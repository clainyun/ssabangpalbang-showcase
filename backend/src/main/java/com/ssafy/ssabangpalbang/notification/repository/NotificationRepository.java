package com.ssafy.ssabangpalbang.notification.repository;

import com.ssafy.ssabangpalbang.notification.entity.Notification;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT MAX(n.id) FROM Notification n "
            + "WHERE n.recipientId = :recipientId")
    Optional<Long> findMaxIdByRecipientId(
            @Param("recipientId") Long recipientId
    );

    @Query("SELECT n FROM Notification n "
            + "WHERE n.recipientId = :recipientId "
            + "AND n.id <= :snapshotMaxId "
            + "AND (:unreadOnly = false "
            + "OR n.isRead = false OR n.readAt > :snapshotAt) "
            + "AND (:lastNotificationId IS NULL "
            + "OR (:lastUnreadGroup = true AND ("
            + "((n.isRead = false OR n.readAt > :snapshotAt) AND ("
            + "n.sentAt < :lastSentAt OR (n.sentAt = :lastSentAt "
            + "AND n.id < :lastNotificationId))) "
            + "OR (:unreadOnly = false AND n.isRead = true "
            + "AND (n.readAt IS NULL OR n.readAt <= :snapshotAt)))) "
            + "OR (:lastUnreadGroup = false AND n.isRead = true "
            + "AND (n.readAt IS NULL OR n.readAt <= :snapshotAt) AND ("
            + "n.sentAt < :lastSentAt OR (n.sentAt = :lastSentAt "
            + "AND n.id < :lastNotificationId)))) "
            + "ORDER BY CASE WHEN (n.isRead = false OR n.readAt > :snapshotAt) "
            + "THEN 0 ELSE 1 END ASC, n.sentAt DESC, n.id DESC")
    List<Notification> findSnapshotPage(
            @Param("recipientId") Long recipientId,
            @Param("snapshotMaxId") Long snapshotMaxId,
            @Param("snapshotAt") OffsetDateTime snapshotAt,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("lastUnreadGroup") Boolean lastUnreadGroup,
            @Param("lastSentAt") OffsetDateTime lastSentAt,
            @Param("lastNotificationId") Long lastNotificationId,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now "
            + "WHERE n.recipientId = :recipientId AND n.isRead = false")
    int markAllAsReadByRecipientId(
            @Param("recipientId") Long recipientId,
            @Param("now") OffsetDateTime now
    );

    long countByRecipientIdAndIsReadFalse(Long recipientId);
}
