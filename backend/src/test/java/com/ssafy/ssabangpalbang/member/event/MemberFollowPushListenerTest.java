package com.ssafy.ssabangpalbang.member.event;

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
class MemberFollowPushListenerTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;
    @Mock
    private FcmPushGateway fcmPushGateway;

    private MemberFollowPushListener listener;

    @BeforeEach
    void setUp() {
        listener = new MemberFollowPushListener(fcmTokenRepository, fcmPushGateway);
    }

    @Test
    void 동의한_대상_회원의_모든_기기에_보낸다() {
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(15L)).thenReturn(List.of(
                token("first-token", "first-device"),
                token("second-token", "second-device")
        ));

        listener.sendAfterCommit(event(true));

        verify(fcmPushGateway, times(2)).sendNotification(
                anyString(), eq("새로운 팔로워가 생겼어요"),
                eq("'팔로워'님이 회원님을 팔로우하기 시작했어요."), eq(expectedData())
        );
    }

    @Test
    void 미동의이면_토큰을_조회하지_않는다() {
        listener.sendAfterCommit(event(false));

        verify(fcmTokenRepository, never()).findAllByMemberId(15L);
    }

    @Test
    void FCM이_비활성이면_토큰을_조회하지_않는다() {
        when(fcmPushGateway.isReady()).thenReturn(false);

        listener.sendAfterCommit(event(true));

        verify(fcmTokenRepository, never()).findAllByMemberId(15L);
    }

    @Test
    void 토큰이_없으면_전송하지_않는다() {
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(15L)).thenReturn(List.of());

        listener.sendAfterCommit(event(true));

        verify(fcmPushGateway, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
    }

    @Test
    void 한_기기_실패가_다음_기기_전송을_막지_않는다() {
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(15L)).thenReturn(List.of(
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

    private MemberFollowPushRequestedEvent event(boolean agreed) {
        return new MemberFollowPushRequestedEvent(
                81L, 15L, 1L, agreed,
                "새로운 팔로워가 생겼어요",
                "'팔로워'님이 회원님을 팔로우하기 시작했어요."
        );
    }

    private FcmToken token(String token, String deviceId) {
        return FcmToken.of(
                15L, token, deviceId,
                OffsetDateTime.parse("2026-08-05T10:00:00+09:00")
        );
    }

    private Map<String, String> expectedData() {
        return Map.of(
                "notificationType", "MEMBER_FOLLOWED",
                "targetScreen", "MEMBER_PROFILE",
                "targetId", "1",
                "notificationId", "81"
        );
    }
}
