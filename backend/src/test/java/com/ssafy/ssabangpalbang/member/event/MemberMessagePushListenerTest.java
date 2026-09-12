package com.ssafy.ssabangpalbang.member.event;

import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushDeliveryException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberMessagePushListenerTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private FcmPushGateway fcmPushGateway;

    private MemberMessagePushListener listener;

    @BeforeEach
    void setUp() {
        listener = new MemberMessagePushListener(
                fcmTokenRepository,
                fcmPushGateway
        );
    }

    @Test
    void 서비스_알림에_동의한_수신자의_모든_기기에_FCM을_보낸다() {
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(12L)).thenReturn(List.of(
                token("first-token", "first-device"),
                token("second-token", "second-device")
        ));

        listener.sendAfterCommit(event(true));

        verify(fcmPushGateway).sendNotification(
                eq("first-token"),
                eq("새로운 메시지가 도착했어요"),
                eq("다음 임장도 같이 참여해요!"),
                eq(expectedData())
        );
        verify(fcmPushGateway).sendNotification(
                eq("second-token"),
                eq("새로운 메시지가 도착했어요"),
                eq("다음 임장도 같이 참여해요!"),
                eq(expectedData())
        );
    }

    @Test
    void 서비스_알림에_동의하지_않으면_토큰도_조회하지_않는다() {
        listener.sendAfterCommit(event(false));

        verify(fcmTokenRepository, never()).findAllByMemberId(12L);
        verify(fcmPushGateway, never()).sendNotification(
                eq("token"),
                eq("title"),
                eq("body"),
                anyMap()
        );
    }

    @Test
    void FCM이_비활성화되어_있으면_토큰도_조회하지_않는다() {
        when(fcmPushGateway.isReady()).thenReturn(false);

        listener.sendAfterCommit(event(true));

        verify(fcmTokenRepository, never()).findAllByMemberId(12L);
    }

    @Test
    void 한_기기_FCM_실패가_다음_기기_전송을_막지_않는다() {
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(12L)).thenReturn(List.of(
                token("first-token", "first-device"),
                token("second-token", "second-device")
        ));
        doThrow(new FcmPushDeliveryException(
                new IllegalStateException("provider failure")
        )).when(fcmPushGateway).sendNotification(
                eq("first-token"),
                eq("새로운 메시지가 도착했어요"),
                eq("다음 임장도 같이 참여해요!"),
                anyMap()
        );

        listener.sendAfterCommit(event(true));

        verify(fcmPushGateway, times(2)).sendNotification(
                org.mockito.ArgumentMatchers.anyString(),
                eq("새로운 메시지가 도착했어요"),
                eq("다음 임장도 같이 참여해요!"),
                eq(expectedData())
        );
    }

    private MemberMessagePushRequestedEvent event(
            boolean serviceNotificationAgreed
    ) {
        return new MemberMessagePushRequestedEvent(
                81L,
                12L,
                1L,
                serviceNotificationAgreed,
                "새로운 메시지가 도착했어요",
                "다음 임장도 같이 참여해요!"
        );
    }

    private FcmToken token(String token, String deviceId) {
        return FcmToken.of(
                12L,
                token,
                deviceId,
                OffsetDateTime.parse("2026-07-25T11:00:00+09:00")
        );
    }

    private Map<String, String> expectedData() {
        return Map.of(
                "notificationType", "MESSAGE",
                "targetScreen", "MEMBER_PROFILE",
                "targetId", "1",
                "notificationId", "81"
        );
    }
}
