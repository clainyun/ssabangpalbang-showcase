package com.ssafy.ssabangpalbang.member.event;

import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushGateway;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MemberMessagePushListener {

    private static final Logger log = LoggerFactory.getLogger(
            MemberMessagePushListener.class
    );

    private final FcmTokenRepository fcmTokenRepository;
    private final FcmPushGateway fcmPushGateway;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(MemberMessagePushRequestedEvent event) {
        if (!event.serviceNotificationAgreed() || !fcmPushGateway.isReady()) {
            return;
        }

        try {
            List<FcmToken> tokens = fcmTokenRepository.findAllByMemberId(
                    event.recipientId()
            );
            Map<String, String> data = Map.of(
                    "notificationType", "MESSAGE",
                    "targetScreen", "MEMBER_PROFILE",
                    "targetId", event.senderId().toString(),
                    "notificationId", event.notificationId().toString()
            );
            for (FcmToken token : tokens) {
                send(event, token, data);
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "쪽지 FCM 후처리에 실패했습니다. "
                            + "notificationId={}, recipientId={}, reason=internal_error",
                    event.notificationId(),
                    event.recipientId()
            );
        }
    }

    private void send(
            MemberMessagePushRequestedEvent event,
            FcmToken token,
            Map<String, String> data
    ) {
        try {
            fcmPushGateway.sendNotification(
                    token.getToken(),
                    event.title(),
                    event.body(),
                    data
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "쪽지 FCM 전송에 실패했습니다. "
                            + "notificationId={}, recipientId={}, reason=provider_rejected",
                    event.notificationId(),
                    event.recipientId()
            );
        }
    }
}
