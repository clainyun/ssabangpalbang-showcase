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

@Slf4j @Component @RequiredArgsConstructor
public class StudySchedulePushListener {
    private final FcmTokenRepository fcmTokenRepository; private final FcmPushGateway fcmPushGateway;
    @Async(StudyPushAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(StudySchedulePushRequestedEvent event) {
        if (!event.serviceNotificationAgreed() || !fcmPushGateway.isReady()) return;
        try { Map<String,String> data=Map.of("notificationType",event.notificationType(),"targetScreen","STUDY_SCHEDULE","targetId",event.studyId().toString(),"notificationId",event.notificationId().toString());
            for(FcmToken token:fcmTokenRepository.findAllByMemberId(event.recipientId())) try {fcmPushGateway.sendNotification(token.getToken(),event.title(),event.body(),data);} catch(RuntimeException e){log.warn("Study schedule FCM push rejected. notificationId={}",event.notificationId());}
        } catch(RuntimeException e){log.warn("Study schedule FCM processing failed. notificationId={}",event.notificationId());}
    }
}
