package com.ssafy.ssabangpalbang.notification.integration;

import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.notification.fcm.DisabledFcmPushGateway;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushDeliveryException;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportCompletionPushListenerTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;
    @Mock
    private FcmPushGateway fcmPushGateway;

    private ReportCompletionPushListener listener;

    @BeforeEach
    void setUp() {
        listener = new ReportCompletionPushListener(
                fcmTokenRepository,
                fcmPushGateway
        );
    }

    @Test
    void 동의한_수신자의_모든_기기에_리포트_완료_FCM을_보낸다() {
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(12L)).thenReturn(List.of(
                token("first-token", "first-device"),
                token("second-token", "second-device")
        ));

        listener.sendAfterCommit(event(true));

        verify(fcmPushGateway).sendNotification(
                "first-token",
                "AI 임장 리포트가 완성됐어요",
                "임장 리포트가 생성되었습니다.",
                expectedData()
        );
        verify(fcmPushGateway).sendNotification(
                "second-token",
                "AI 임장 리포트가 완성됐어요",
                "임장 리포트가 생성되었습니다.",
                expectedData()
        );
    }

    @Test
    void 서비스_알림에_동의하지_않으면_토큰도_조회하지_않는다() {
        listener.sendAfterCommit(event(false));

        verify(fcmTokenRepository, never()).findAllByMemberId(12L);
        verify(fcmPushGateway, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
    }

    @Test
    void FCM이_비활성화되어_있으면_토큰도_조회하지_않는다() {
        when(fcmPushGateway.isReady()).thenReturn(false);

        listener.sendAfterCommit(event(true));

        verify(fcmTokenRepository, never()).findAllByMemberId(12L);
        verify(fcmPushGateway, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
    }

    @Test
    void 등록된_토큰이_없으면_FCM을_보내지_않고_정상_종료한다() {
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(12L)).thenReturn(List.of());

        assertThatCode(() -> listener.sendAfterCommit(event(true)))
                .doesNotThrowAnyException();

        verify(fcmPushGateway, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
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
                anyString(),
                anyString(),
                anyMap()
        );

        assertThatCode(() -> listener.sendAfterCommit(event(true)))
                .doesNotThrowAnyException();

        verify(fcmPushGateway, times(2)).sendNotification(
                anyString(),
                eq("AI 임장 리포트가 완성됐어요"),
                eq("임장 리포트가 생성되었습니다."),
                eq(expectedData())
        );
    }

    @Test
    void FCM_후처리_실패는_커밋_흐름_밖으로_전파하지_않는다() {
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(12L))
                .thenThrow(new IllegalStateException("repository failure"));

        assertThatCode(() -> listener.sendAfterCommit(event(true)))
                .doesNotThrowAnyException();
    }

    @Test
    void 비활성_게이트웨이는_준비_상태_가드에서_멈춘다() {
        ReportCompletionPushListener disabledListener =
                new ReportCompletionPushListener(
                        fcmTokenRepository,
                        new DisabledFcmPushGateway()
                );

        assertThatCode(() -> disabledListener.sendAfterCommit(event(true)))
                .doesNotThrowAnyException();
        verify(fcmTokenRepository, never()).findAllByMemberId(12L);
    }

    @Test
    void FCM은_리포트_완료_트랜잭션_커밋_후에_발송한다()
            throws NoSuchMethodException {
        Method method = ReportCompletionPushListener.class.getDeclaredMethod(
                "sendAfterCommit",
                ReportCompletionPushRequestedEvent.class
        );
        TransactionalEventListener annotation = method.getAnnotation(
                TransactionalEventListener.class
        );

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    private ReportCompletionPushRequestedEvent event(
            boolean serviceNotificationAgreed
    ) {
        return new ReportCompletionPushRequestedEvent(
                81L,
                12L,
                48L,
                serviceNotificationAgreed,
                "AI 임장 리포트가 완성됐어요",
                "임장 리포트가 생성되었습니다."
        );
    }

    private FcmToken token(String token, String deviceId) {
        return FcmToken.of(
                12L,
                token,
                deviceId,
                OffsetDateTime.parse("2026-08-04T11:00:00+09:00")
        );
    }

    private Map<String, String> expectedData() {
        return Map.of(
                "notificationType", "REPORT_COMPLETED",
                "targetScreen", "REPORT_DETAIL",
                "targetId", "48",
                "notificationId", "81"
        );
    }
}
