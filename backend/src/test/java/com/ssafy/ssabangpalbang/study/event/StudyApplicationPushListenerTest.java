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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyApplicationPushListenerTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;
    @Mock
    private FcmPushGateway fcmPushGateway;

    private StudyApplicationPushListener listener;

    @BeforeEach
    void setUp() {
        listener = new StudyApplicationPushListener(fcmTokenRepository, fcmPushGateway);
    }

    @Test
    void 동의한_수신자의_모든_기기에_신청_푸시를_보낸다() {
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(8L)).thenReturn(List.of(
                token("first-token", "first-device"),
                token("second-token", "second-device")
        ));

        listener.sendAfterCommit(event(true));

        verify(fcmPushGateway, times(2)).sendNotification(
                anyString(), eq("스터디 신청이 승인되었습니다."),
                eq("'검증 스터디' 스터디에 참여하게 되었어요."), eq(expectedData())
        );
    }

    @Test
    void 미동의이면_토큰을_조회하지_않는다() {
        listener.sendAfterCommit(event(false));

        verify(fcmTokenRepository, never()).findAllByMemberId(8L);
        verify(fcmPushGateway, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
    }

    @Test
    void FCM이_비활성이면_토큰을_조회하지_않는다() {
        when(fcmPushGateway.isReady()).thenReturn(false);

        listener.sendAfterCommit(event(true));

        verify(fcmTokenRepository, never()).findAllByMemberId(8L);
    }

    @Test
    void 한_기기_실패가_다음_기기_전송을_막지_않는다() {
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
                anyString(), anyString(), anyString(), anyMap()
        );
    }

    private StudyApplicationPushRequestedEvent event(boolean agreed) {
        return new StudyApplicationPushRequestedEvent(
                81L, 8L, 10L, "STUDY_APPLICATION_APPROVED", "STUDY_DETAIL",
                "스터디 신청이 승인되었습니다.",
                "'검증 스터디' 스터디에 참여하게 되었어요.", agreed
        );
    }

    private FcmToken token(String token, String deviceId) {
        return FcmToken.of(
                8L, token, deviceId,
                OffsetDateTime.parse("2026-08-05T10:00:00+09:00")
        );
    }

    private Map<String, String> expectedData() {
        return Map.of(
                "notificationType", "STUDY_APPLICATION_APPROVED",
                "targetScreen", "STUDY_DETAIL",
                "targetId", "10",
                "notificationId", "81"
        );
    }
}
