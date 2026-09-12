package com.ssafy.ssabangpalbang.study.event;

import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushGateway;
import com.ssafy.ssabangpalbang.study.config.StudyPushAsyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StudyApplicationPushListener {

    private final FcmTokenRepository fcmTokenRepository;
    private final FcmPushGateway fcmPushGateway;

    @Async(StudyPushAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(StudyApplicationPushRequestedEvent event) {
        if (!event.serviceNotificationAgreed() || !fcmPushGateway.isReady()) {
            return;
        }

        try {
            Map<String, String> data = Map.of(
                    "notificationType", event.notificationType(),
                    "targetScreen", event.targetScreen(),
                    "targetId", event.studyId().toString(),
                    "notificationId", event.notificationId().toString()
            );
            for (FcmToken token : fcmTokenRepository.findAllByMemberId(event.recipientId())) {
                send(event, token, data);
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "스터디 신청 FCM 후처리에 실패했습니다. notificationId={}, recipientId={}, reason=internal_error",
                    event.notificationId(), event.recipientId(), exception
            );
        }
    }

    private void send(
            StudyApplicationPushRequestedEvent event,
            FcmToken token,
            Map<String, String> data
    ) {
        try {
            fcmPushGateway.sendNotification(
                    token.getToken(), event.title(), event.body(), data
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "스터디 신청 FCM 전송에 실패했습니다. notificationId={}, recipientId={}, deviceId={}, reason=provider_rejected",
                    event.notificationId(), event.recipientId(), token.getDeviceId(), exception
            );
        }
    }
}
