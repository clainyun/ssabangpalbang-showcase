package com.ssafy.ssabangpalbang.study.event;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.notification.fcm.DisabledFcmPushGateway;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudySchedulePushListenerTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;
    @Mock
    private FcmPushGateway fcmPushGateway;

    private StudySchedulePushListener listener;

    @BeforeEach
    void setUp() {
        listener = new StudySchedulePushListener(
                fcmTokenRepository,
                fcmPushGateway
        );
    }

    @Test
    void P1_동의한_수신자의_토큰_2개에_각각_발송한다() {
        readyWithTokens("first-token", "second-token");

        listener.sendAfterCommit(event(true));

        verify(fcmPushGateway, times(2)).sendNotification(
                anyString(),
                anyString(),
                anyString(),
                anyMap()
        );
    }

    @Test
    void P2_서비스_알림에_동의하지_않으면_발송하지_않는다() {
        listener.sendAfterCommit(event(false));

        verify(fcmTokenRepository, never()).findAllByMemberId(12L);
        verify(fcmPushGateway, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
    }

    @Test
    void P3_게이트웨이가_준비되지_않으면_발송하지_않는다() {
        when(fcmPushGateway.isReady()).thenReturn(false);

        listener.sendAfterCommit(event(true));

        verify(fcmTokenRepository, never()).findAllByMemberId(12L);
        verify(fcmPushGateway, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
    }

    @Test
    void P4_토큰이_없으면_예외_없이_발송_0회다() {
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(12L)).thenReturn(List.of());

        assertThatCode(() -> listener.sendAfterCommit(event(true)))
                .doesNotThrowAnyException();
        verify(fcmPushGateway, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
    }

    @Test
    void P5_게이트웨이_RuntimeException은_밖으로_나오지_않는다() {
        readyWithTokens("token");
        doThrow(new IllegalStateException("provider failure"))
                .when(fcmPushGateway)
                .sendNotification(anyString(), anyString(), anyString(), anyMap());

        assertThatCode(() -> listener.sendAfterCommit(event(true)))
                .doesNotThrowAnyException();
    }

    @Test
    void P6_data_맵은_정확한_4개_키를_담는다() {
        readyWithTokens("token");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);

        listener.sendAfterCommit(event(true));

        verify(fcmPushGateway).sendNotification(
                anyString(),
                anyString(),
                anyString(),
                captor.capture()
        );
        assertThat(captor.getValue()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "notificationType", "SCHEDULE_CHANGED",
                "targetScreen", "STUDY_SCHEDULE",
                "targetId", "10",
                "notificationId", "81"
        ));
    }

    @Test
    void P7_Disabled_게이트웨이는_isReady_가드에서_멈춘다() {
        DisabledFcmPushGateway disabledGateway = new DisabledFcmPushGateway();
        StudySchedulePushListener disabledListener = new StudySchedulePushListener(
                fcmTokenRepository,
                disabledGateway
        );

        assertThatCode(() -> disabledListener.sendAfterCommit(event(true)))
                .doesNotThrowAnyException();
        verify(fcmTokenRepository, never()).findAllByMemberId(12L);
    }

    @Test
    void P8_준비된_게이트웨이의_BusinessException도_삼킨다() {
        readyWithTokens("token");
        doThrow(new BusinessException(ErrorCode.FCM_PUSH_NOT_CONFIGURED))
                .when(fcmPushGateway)
                .sendNotification(anyString(), anyString(), anyString(), anyMap());

        assertThatCode(() -> listener.sendAfterCommit(event(true)))
                .doesNotThrowAnyException();
    }

    private void readyWithTokens(String... tokens) {
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(12L))
                .thenReturn(java.util.Arrays.stream(tokens)
                        .map(this::token)
                        .toList());
    }

    private StudySchedulePushRequestedEvent event(boolean agreed) {
        return new StudySchedulePushRequestedEvent(
                81L,
                12L,
                10L,
                "SCHEDULE_CHANGED",
                "임장 일정이 변경되었습니다.",
                "일정 본문",
                agreed
        );
    }

    private FcmToken token(String token) {
        return FcmToken.of(
                12L,
                token,
                token + "-device",
                OffsetDateTime.parse("2026-07-30T11:00:00+09:00")
        );
    }
}
