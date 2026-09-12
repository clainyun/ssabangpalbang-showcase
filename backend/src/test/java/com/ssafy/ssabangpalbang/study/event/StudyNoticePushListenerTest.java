package com.ssafy.ssabangpalbang.study.event;

import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyNoticePushListenerTest {

    @Mock FcmTokenRepository fcmTokenRepository;
    @Mock FcmPushGateway fcmPushGateway;

    private StudyNoticePushListener listener;

    @BeforeEach
    void setUp() {
        listener = new StudyNoticePushListener(fcmTokenRepository, fcmPushGateway);
    }

    @Test
    void 앱_프로세스가_없어도_표시할_notification_payload를_모든_기기에_전송한다() {
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(8L)).thenReturn(List.of(
                token("first-token", "first-device"),
                token("second-token", "second-device")
        ));
        doThrow(new IllegalStateException("provider failure"))
                .when(fcmPushGateway).sendNotification(
                        eq("first-token"), anyString(), anyString(), anyMap()
                );

        listener.sendAfterCommit(event(true));

        verify(fcmPushGateway, times(2)).sendNotification(
                anyString(),
                eq("새 스터디 공지가 등록되었습니다."),
                eq("공지 본문"),
                eq(Map.of(
                        "notificationType", "STUDY_NOTICE_CREATED",
                        "targetScreen", "STUDY_DETAIL",
                        "targetId", "7",
                        "targetSubId", "18",
                        "notificationId", "108"
                ))
        );
    }

    @Test
    void 서비스_알림에_동의하지_않으면_토큰을_조회하지_않는다() {
        listener.sendAfterCommit(event(false));

        verifyNoInteractions(fcmTokenRepository);
        verify(fcmPushGateway, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
    }

    @Test
    void FCM이_비활성화되어_있으면_토큰을_조회하지_않는다() {
        when(fcmPushGateway.isReady()).thenReturn(false);

        listener.sendAfterCommit(event(true));

        verifyNoInteractions(fcmTokenRepository);
        verify(fcmPushGateway, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
    }

    private StudyNoticePushRequestedEvent event(boolean agreed) {
        return new StudyNoticePushRequestedEvent(
                108L,
                8L,
                7L,
                18L,
                "새 스터디 공지가 등록되었습니다.",
                "공지 본문",
                agreed
        );
    }

    private FcmToken token(String token, String deviceId) {
        return FcmToken.of(
                8L,
                token,
                deviceId,
                OffsetDateTime.parse("2026-08-12T10:00:00+09:00")
        );
    }
}
