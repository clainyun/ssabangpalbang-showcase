package com.ssafy.ssabangpalbang.notification.integration;

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
class FieldVisitStartedPushListenerTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;
    @Mock
    private FcmPushGateway fcmPushGateway;

    private FieldVisitStartedPushListener listener;

    @BeforeEach
    void setUp() {
        listener = new FieldVisitStartedPushListener(
                fcmTokenRepository, fcmPushGateway
        );
    }

    @Test
    void 동의한_수신자의_모든_기기에_딥링크_데이터를_보낸다() {
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(43L)).thenReturn(List.of(
                token("first-token", "first-device"),
                token("second-token", "second-device")
        ));

        listener.sendAfterCommit(event(true));

        verify(fcmPushGateway, times(2)).sendNotification(
                anyString(), eq("임장이 시작됐어요"),
                eq("'검증 스터디' 임장이 시작되었습니다. 지금 참여해 보세요."),
                eq(expectedData())
        );
    }

    @Test
    void 미동의이면_토큰을_조회하지_않는다() {
        listener.sendAfterCommit(event(false));

        verify(fcmTokenRepository, never()).findAllByMemberId(43L);
    }

    @Test
    void FCM이_비활성이면_토큰을_조회하지_않는다() {
        when(fcmPushGateway.isReady()).thenReturn(false);

        listener.sendAfterCommit(event(true));

        verify(fcmTokenRepository, never()).findAllByMemberId(43L);
    }

    @Test
    void 토큰이_없으면_전송하지_않는다() {
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(43L)).thenReturn(List.of());

        listener.sendAfterCommit(event(true));

        verify(fcmPushGateway, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
    }

    @Test
    void 한_기기_실패가_다음_기기_전송을_막지_않는다() {
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(43L)).thenReturn(List.of(
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

    private FieldVisitStartedPushRequestedEvent event(boolean agreed) {
        return new FieldVisitStartedPushRequestedEvent(
                81L, 43L, 7L, 100L, agreed,
                "임장이 시작됐어요",
                "'검증 스터디' 임장이 시작되었습니다. 지금 참여해 보세요."
        );
    }

    private FcmToken token(String token, String deviceId) {
        return FcmToken.of(
                43L, token, deviceId,
                OffsetDateTime.parse("2026-08-05T10:00:00+09:00")
        );
    }

    private Map<String, String> expectedData() {
        return Map.of(
                "notificationType", "FIELD_VISIT_STARTED",
                "targetScreen", "FIELD_VISIT",
                "targetId", "7",
                "targetSubId", "100",
                "notificationId", "81"
        );
    }
}
