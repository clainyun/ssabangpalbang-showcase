package com.ssafy.ssabangpalbang.member.event;

import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberFollowPushListener {

    private final FcmTokenRepository fcmTokenRepository;
    private final FcmPushGateway fcmPushGateway;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(MemberFollowPushRequestedEvent event) {
        if (!event.serviceNotificationAgreed() || !fcmPushGateway.isReady()) {
            return;
        }

        try {
            Map<String, String> data = Map.of(
                    "notificationType", "MEMBER_FOLLOWED",
                    "targetScreen", "MEMBER_PROFILE",
                    "targetId", event.followerId().toString(),
                    "notificationId", event.notificationId().toString()
            );
            for (FcmToken token : fcmTokenRepository.findAllByMemberId(event.recipientId())) {
                send(event, token, data);
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "팔로우 FCM 후처리에 실패했습니다. notificationId={}, recipientId={}, reason=internal_error",
                    event.notificationId(), event.recipientId(), exception
            );
        }
    }

    private void send(
            MemberFollowPushRequestedEvent event,
            FcmToken token,
            Map<String, String> data
    ) {
        try {
            fcmPushGateway.sendNotification(
                    token.getToken(), event.title(), event.body(), data
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "팔로우 FCM 전송에 실패했습니다. notificationId={}, recipientId={}, deviceId={}, reason=provider_rejected",
                    event.notificationId(), event.recipientId(), token.getDeviceId(), exception
            );
        }
    }
}
