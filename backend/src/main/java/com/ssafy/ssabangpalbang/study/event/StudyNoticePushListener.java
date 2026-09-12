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
public class StudyNoticePushListener {

    private final FcmTokenRepository fcmTokenRepository;
    private final FcmPushGateway fcmPushGateway;

    @Async(StudyPushAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(StudyNoticePushRequestedEvent event) {
        if (!event.serviceNotificationAgreed() || !fcmPushGateway.isReady()) {
            return;
        }

        Map<String, String> data = Map.of(
                "notificationType", "STUDY_NOTICE_CREATED",
                "targetScreen", "STUDY_DETAIL",
                "targetId", event.studyId().toString(),
                "targetSubId", event.noticeId().toString(),
                "notificationId", event.notificationId().toString()
        );
        try {
            for (FcmToken token : fcmTokenRepository.findAllByMemberId(event.recipientId())) {
                send(token, event, data);
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "스터디 공지 FCM 후처리에 실패했습니다. noticeId={}, recipientId={}, reason=internal_error",
                    event.noticeId(),
                    event.recipientId(),
                    exception
            );
        }
    }

    private void send(
            FcmToken token,
            StudyNoticePushRequestedEvent event,
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
                    "스터디 공지 FCM 전송에 실패했습니다. noticeId={}, recipientId={}, deviceId={}, reason=provider_rejected",
                    event.noticeId(),
                    event.recipientId(),
                    token.getDeviceId(),
                    exception
            );
        }
    }
}
