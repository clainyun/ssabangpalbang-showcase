package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.member.event.MemberMessagePushRequestedEvent;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class MemberMessageWriter {

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification save(
            Long senderId,
            Long recipientId,
            boolean serviceNotificationAgreed,
            String title,
            String content,
            String idempotencyKey,
            OffsetDateTime sentAt
    ) {
        Notification saved = notificationRepository.saveAndFlush(
                Notification.message(
                        recipientId,
                        senderId,
                        title,
                        content,
                        idempotencyKey,
                        sentAt
                )
        );
        eventPublisher.publishEvent(new MemberMessagePushRequestedEvent(
                saved.getId(),
                recipientId,
                senderId,
                serviceNotificationAgreed,
                title,
                content
        ));
        return saved;
    }
}
