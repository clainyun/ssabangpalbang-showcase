package com.ssafy.ssabangpalbang.notification.integration;

import com.ssafy.ssabangpalbang.fieldvisit.integration.FieldVisitSessionStartedEvent;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.entity.NotificationCategory;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FieldVisitStartedNotificationListener {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final String TYPE = "FIELD_VISIT_STARTED";
    private static final String TITLE = "임장이 시작됐어요";

    private final MemberRepository memberRepository;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void createBeforeCommit(FieldVisitSessionStartedEvent event) {
        if (event.recipientIds().isEmpty()) {
            return;
        }
        Map<Long, Boolean> agreements = memberRepository
                .findAllById(event.recipientIds())
                .stream()
                .collect(Collectors.toMap(
                        Member::getId,
                        Member::isServiceNotificationAgreed
                ));
        String body = "'%s' 임장이 시작되었습니다. 지금 참여해 보세요.".formatted(
                event.studyTitle()
        );
        OffsetDateTime sentAt = OffsetDateTime.ofInstant(
                event.startedAt(), SEOUL_ZONE_ID
        );
        event.recipientIds().forEach(recipientId -> createIfAbsent(
                event,
                recipientId,
                agreements.getOrDefault(recipientId, false),
                body,
                sentAt
        ));
    }

    private void createIfAbsent(
            FieldVisitSessionStartedEvent event,
            Long recipientId,
            boolean serviceNotificationAgreed,
            String body,
            OffsetDateTime sentAt
    ) {
        String idempotencyKey = "%s:%d:%d".formatted(
                TYPE, event.sessionId(), recipientId
        );
        if (notificationRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return;
        }

        try {
            Notification saved = notificationRepository.saveAndFlush(
                    Notification.create(
                            recipientId,
                            event.starterId(),
                            NotificationCategory.FIELD,
                            TYPE,
                            "FIELD_VISIT",
                            event.studyId(),
                            event.sessionId(),
                            TITLE,
                            body,
                            idempotencyKey,
                            sentAt
                    )
            );
            eventPublisher.publishEvent(new FieldVisitStartedPushRequestedEvent(
                    saved.getId(),
                    recipientId,
                    event.studyId(),
                    event.sessionId(),
                    serviceNotificationAgreed,
                    TITLE,
                    body
            ));
        } catch (DataIntegrityViolationException ignored) {
            // 수신자별 멱등 키 충돌이면 이미 같은 시작 알림이 생성된 것이다.
        }
    }
}
