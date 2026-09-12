package com.ssafy.ssabangpalbang.notification.integration;

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
public class ReportCompletionPushListener {

    private static final Logger log = LoggerFactory.getLogger(
            ReportCompletionPushListener.class
    );

    private final FcmTokenRepository fcmTokenRepository;
    private final FcmPushGateway fcmPushGateway;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(ReportCompletionPushRequestedEvent event) {
        if (!event.serviceNotificationAgreed()) {
            return;
        }

        try {
            if (!fcmPushGateway.isReady()) {
                return;
            }

            List<FcmToken> tokens = fcmTokenRepository.findAllByMemberId(
                    event.recipientId()
            );
            Map<String, String> data = Map.of(
                    "notificationType", "REPORT_COMPLETED",
                    "targetScreen", "REPORT_DETAIL",
                    "targetId", event.reportId().toString(),
                    "notificationId", event.notificationId().toString()
            );
            for (FcmToken token : tokens) {
                send(event, token, data);
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "리포트 완료 FCM 후처리에 실패했습니다. "
                            + "notificationId={}, recipientId={}, reason=internal_error",
                    event.notificationId(),
                    event.recipientId(),
                    exception
            );
        }
    }

    private void send(
            ReportCompletionPushRequestedEvent event,
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
                    "리포트 완료 FCM 전송에 실패했습니다. "
                            + "notificationId={}, recipientId={}, deviceId={}, "
                            + "reason=provider_rejected",
                    event.notificationId(),
                    event.recipientId(),
                    token.getDeviceId(),
                    exception
            );
        }
    }
}
