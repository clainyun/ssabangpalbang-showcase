package com.ssafy.ssabangpalbang.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    private static final String MESSAGE_TYPE = "MESSAGE";
    private static final String MEMBER_PROFILE_SCREEN = "MEMBER_PROFILE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "actor_id")
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationCategory category;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(name = "target_screen", length = 50)
    private String targetScreen;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_sub_id")
    private Long targetSubId;

    @Column(length = 200)
    private String title;

    @Column(length = 500)
    private String body;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 200)
    private String idempotencyKey;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    private Notification(
            Long recipientId,
            Long actorId,
            String title,
            String body,
            String idempotencyKey,
            OffsetDateTime sentAt
    ) {
        this.recipientId = recipientId;
        this.actorId = actorId;
        this.category = NotificationCategory.MESSAGE;
        this.type = MESSAGE_TYPE;
        this.targetScreen = MEMBER_PROFILE_SCREEN;
        this.targetId = actorId;
        this.title = title;
        this.body = body;
        this.idempotencyKey = idempotencyKey;
        this.sentAt = sentAt;
    }

    public static Notification message(
            Long recipientId,
            Long actorId,
            String title,
            String body,
            String idempotencyKey,
            OffsetDateTime sentAt
    ) {
        return new Notification(
                recipientId,
                actorId,
                title,
                body,
                idempotencyKey,
                sentAt
        );
    }

    public void markAsRead(OffsetDateTime now) {
        if (this.isRead) {
            return;
        }

        this.isRead = true;
        this.readAt = now;
    }

    public static Notification create(
            Long recipientId,
            Long actorId,
            NotificationCategory category,
            String type,
            String targetScreen,
            Long targetId,
            Long targetSubId,
            String title,
            String body,
            String idempotencyKey,
            OffsetDateTime sentAt
    ) {
        Notification notification = new Notification();
        notification.recipientId = recipientId;
        notification.actorId = actorId;
        notification.category = category;
        notification.type = type;
        notification.targetScreen = targetScreen;
        notification.targetId = targetId;
        notification.targetSubId = targetSubId;
        notification.title = title;
        notification.body = body;
        notification.isRead = false;
        notification.readAt = null;
        notification.idempotencyKey = idempotencyKey;
        notification.sentAt = sentAt;
        return notification;
    }
}
