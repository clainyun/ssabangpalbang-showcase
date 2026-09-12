package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.auth.LoginMember;
import com.ssafy.ssabangpalbang.member.auth.LoginMemberResolver;
import com.ssafy.ssabangpalbang.member.dto.response.FcmTestPushResponse;
import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.notification.fcm.DisabledFcmPushGateway;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushDeliveryException;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushGateway;
import com.ssafy.ssabangpalbang.notification.fcm.FcmTestPushScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.core.task.TaskRejectedException;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmTestPushServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final String DEVICE_ID = "device-A";
    private static final String FCM_TOKEN = "fcm-token-A";
    private static final Instant REQUESTED_AT =
            Instant.parse("2026-07-29T07:30:00Z");
    private static final OffsetDateTime SCHEDULED_AT =
            OffsetDateTime.parse("2026-07-29T16:30:05+09:00");

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private LoginMemberResolver loginMemberResolver;

    @Mock
    private FcmPushGateway fcmPushGateway;

    @Mock
    private FcmTestPushScheduler fcmTestPushScheduler;

    @Mock
    private TaskScheduler taskScheduler;

    private FcmTestPushService fcmTestPushService;

    @BeforeEach
    void setUp() {
        fcmTestPushService = new FcmTestPushService(
                fcmTokenRepository,
                loginMemberResolver,
                fcmPushGateway,
                fcmTestPushScheduler
        );
    }

    @Test
    void 현재_회원의_요청_기기로_5초_뒤_테스트_알림을_예약한다() {
        activeMember();
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, DEVICE_ID))
                .thenReturn(Optional.of(fcmToken(FCM_TOKEN)));
        when(fcmTestPushScheduler.schedule(MEMBER_ID, DEVICE_ID))
                .thenReturn(SCHEDULED_AT);

        FcmTestPushResponse response = fcmTestPushService.sendToCurrentDevice(
                DEVICE_ID
        );

        verify(fcmTestPushScheduler).schedule(MEMBER_ID, DEVICE_ID);
        verify(fcmPushGateway).validateTestPush(FCM_TOKEN);
        verify(fcmPushGateway, never()).sendTestPush(FCM_TOKEN);
        assertThat(response.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(response.scheduledAt()).isEqualTo(SCHEDULED_AT);
    }

    @Test
    void 실행_시점에_변경된_최신_토큰으로_정확히_5초_뒤_전송한다() {
        FcmTestPushScheduler scheduler = schedulerWithFixedClock();
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, DEVICE_ID))
                .thenReturn(Optional.of(fcmToken("latest-token")));

        OffsetDateTime scheduledAt = scheduler.schedule(MEMBER_ID, DEVICE_ID);

        verify(taskScheduler).schedule(
                taskCaptor.capture(),
                instantCaptor.capture()
        );
        assertThat(instantCaptor.getValue())
                .isEqualTo(REQUESTED_AT.plusSeconds(5));
        assertThat(scheduledAt).isEqualTo(SCHEDULED_AT);

        taskCaptor.getValue().run();

        verify(fcmPushGateway).sendTestPush("latest-token");
        verify(fcmPushGateway, never()).sendTestPush(FCM_TOKEN);
    }

    @Test
    void 실행_전에_토큰이_삭제되면_발송을_생략한다() {
        FcmTestPushScheduler scheduler = schedulerWithFixedClock();
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, DEVICE_ID))
                .thenReturn(Optional.empty());

        scheduler.schedule(MEMBER_ID, DEVICE_ID);
        verify(taskScheduler).schedule(taskCaptor.capture(), org.mockito.ArgumentMatchers.any(Instant.class));

        taskCaptor.getValue().run();

        verify(fcmPushGateway, never()).sendTestPush(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 같은_회원과_기기의_중복_요청은_하나의_예약으로_합친다() {
        FcmTestPushScheduler scheduler = schedulerWithFixedClock();

        OffsetDateTime first = scheduler.schedule(MEMBER_ID, DEVICE_ID);
        OffsetDateTime second = scheduler.schedule(MEMBER_ID, DEVICE_ID);

        assertThat(second).isEqualTo(first);
        verify(taskScheduler, times(1)).schedule(
                any(Runnable.class),
                any(Instant.class)
        );
    }

    @Test
    void 예약_실행기가_요청을_거절하면_안전한_503_오류로_변환한다() {
        FcmTestPushScheduler scheduler = new FcmTestPushScheduler(
                taskScheduler,
                fcmTokenRepository,
                fcmPushGateway,
                Clock.fixed(REQUESTED_AT, ZoneOffset.UTC)
        );
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        doThrow(new TaskRejectedException("shutdown"))
                .doReturn(scheduledFuture)
                .when(taskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));

        assertThatThrownBy(() -> scheduler.schedule(MEMBER_ID, DEVICE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FCM_PUSH_SCHEDULING_FAILED);

        assertThatCode(() -> scheduler.schedule(MEMBER_ID, DEVICE_ID))
                .doesNotThrowAnyException();
        verify(taskScheduler, times(2)).schedule(
                any(Runnable.class),
                any(Instant.class)
        );
    }

    @Test
    void 비동기_제공자_실패는_요청_응답으로_노출하지_않는다() {
        FcmTestPushScheduler scheduler = schedulerWithFixedClock();
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, DEVICE_ID))
                .thenReturn(Optional.of(fcmToken(FCM_TOKEN)));
        doThrow(new FcmPushDeliveryException(new RuntimeException("provider detail")))
                .when(fcmPushGateway)
                .sendTestPush(FCM_TOKEN);

        scheduler.schedule(MEMBER_ID, DEVICE_ID);
        verify(taskScheduler).schedule(taskCaptor.capture(), org.mockito.ArgumentMatchers.any(Instant.class));

        assertThatCode(() -> taskCaptor.getValue().run())
                .doesNotThrowAnyException();
    }

    @Test
    void 비동기_DB_조회_실패도_스케줄러_밖으로_전파하지_않는다() {
        FcmTestPushScheduler scheduler = schedulerWithFixedClock();
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, DEVICE_ID))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        scheduler.schedule(MEMBER_ID, DEVICE_ID);
        verify(taskScheduler).schedule(
                taskCaptor.capture(),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );

        assertThatCode(() -> taskCaptor.getValue().run())
                .doesNotThrowAnyException();
    }

    @Test
    void 실행이_시작된_예약은_병합_대상에서_제거되어_새로_예약할_수_있다() {
        FcmTestPushScheduler scheduler = schedulerWithFixedClock();
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, DEVICE_ID))
                .thenReturn(Optional.of(fcmToken(FCM_TOKEN)));
        doAnswer(invocation -> {
            scheduler.schedule(MEMBER_ID, DEVICE_ID);
            return null;
        }).when(fcmPushGateway).sendTestPush(FCM_TOKEN);

        scheduler.schedule(MEMBER_ID, DEVICE_ID);
        verify(taskScheduler).schedule(
                taskCaptor.capture(),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );

        taskCaptor.getValue().run();

        verify(taskScheduler, times(2)).schedule(
                any(Runnable.class),
                any(Instant.class)
        );
    }

    @Test
    void 다른_기기나_다른_회원의_토큰은_예약_대상으로_사용하지_않는다() {
        activeMember();
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, DEVICE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> fcmTestPushService.sendToCurrentDevice(DEVICE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_FCM_TOKEN_NOT_FOUND);

        verifyNoInteractions(fcmTestPushScheduler);
        verify(fcmPushGateway, never())
                .sendTestPush(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 비활성_회원은_테스트_알림을_예약할_수_없다() {
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(MEMBER_ID, false));

        assertThatThrownBy(() -> fcmTestPushService.sendToCurrentDevice(DEVICE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verifyNoInteractions(
                fcmTokenRepository,
                fcmPushGateway,
                fcmTestPushScheduler
        );
    }

    @Test
    void FCM_설정이_비활성화되어_있으면_예약하지_않고_503_오류를_반환한다() {
        activeMember();
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, DEVICE_ID))
                .thenReturn(Optional.of(fcmToken(FCM_TOKEN)));
        FcmTestPushService disabledGatewayService = new FcmTestPushService(
                fcmTokenRepository,
                loginMemberResolver,
                new DisabledFcmPushGateway(),
                fcmTestPushScheduler
        );

        assertThatThrownBy(() -> disabledGatewayService.sendToCurrentDevice(DEVICE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FCM_PUSH_NOT_CONFIGURED);

        verifyNoInteractions(fcmTestPushScheduler);
    }

    @Test
    void FCM_dry_run_검증_실패는_예약하지_않고_502_오류를_반환한다() {
        activeMember();
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, DEVICE_ID))
                .thenReturn(Optional.of(fcmToken(FCM_TOKEN)));
        doThrow(new FcmPushDeliveryException(new RuntimeException()))
                .when(fcmPushGateway)
                .validateTestPush(FCM_TOKEN);

        assertThatThrownBy(() -> fcmTestPushService.sendToCurrentDevice(DEVICE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FCM_PUSH_DELIVERY_FAILED);

        verifyNoInteractions(fcmTestPushScheduler);
    }

    @Test
    void 유효하지_않은_기기_ID는_FCM_확인_전에_거절한다() {
        assertThatThrownBy(() -> fcmTestPushService.sendToCurrentDevice("  "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_DEVICE_ID_INVALID);

        verifyNoInteractions(
                fcmTokenRepository,
                loginMemberResolver,
                fcmPushGateway,
                fcmTestPushScheduler
        );
    }

    private FcmTestPushScheduler schedulerWithFixedClock() {
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        doReturn(scheduledFuture)
                .when(taskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));
        return new FcmTestPushScheduler(
                taskScheduler,
                fcmTokenRepository,
                fcmPushGateway,
                Clock.fixed(REQUESTED_AT, ZoneOffset.UTC)
        );
    }

    private void activeMember() {
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(MEMBER_ID, true));
    }

    private FcmToken fcmToken(String token) {
        return FcmToken.of(
                MEMBER_ID,
                token,
                DEVICE_ID,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
