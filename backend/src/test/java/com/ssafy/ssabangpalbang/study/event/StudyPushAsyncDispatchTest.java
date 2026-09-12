package com.ssafy.ssabangpalbang.study.event;

import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushGateway;
import com.ssafy.ssabangpalbang.study.config.StudyPushAsyncConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudyPushAsyncDispatchTest {

    private FcmTokenRepository fcmTokenRepository;
    private FcmPushGateway fcmPushGateway;
    private AnnotationConfigApplicationContext context;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        fcmTokenRepository = mock(FcmTokenRepository.class);
        fcmPushGateway = mock(FcmPushGateway.class);
        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class, StudyPushAsyncConfig.class);
        context.registerBean(
                StudyApplicationPushListener.class,
                () -> new StudyApplicationPushListener(fcmTokenRepository, fcmPushGateway)
        );
        context.registerBean(
                StudySchedulePushListener.class,
                () -> new StudySchedulePushListener(fcmTokenRepository, fcmPushGateway)
        );
        context.registerBean(
                StudyNoticePushListener.class,
                () -> new StudyNoticePushListener(fcmTokenRepository, fcmPushGateway)
        );
        context.refresh();
        transactionTemplate = new TransactionTemplate(
                context.getBean(PlatformTransactionManager.class)
        );
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(anyLong())).thenReturn(List.of(
                FcmToken.of(
                        10L,
                        "fcm-token",
                        "device-id",
                        OffsetDateTime.parse("2026-08-06T10:00:00+09:00")
                )
        ));
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void 푸시는_커밋_전에는_전송되지_않는다() throws InterruptedException {
        CountDownLatch sent = recordSendThread(new AtomicReference<>());

        transactionTemplate.executeWithoutResult(status -> {
            context.publishEvent(applicationEvent());
            verify(fcmPushGateway, never()).sendNotification(
                    anyString(), anyString(), anyString(), anyMap()
            );
        });

        assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void 푸시는_커밋_후_요청_스레드가_아닌_곳에서_전송된다() throws InterruptedException {
        AtomicReference<String> sendThread = new AtomicReference<>();
        CountDownLatch sent = recordSendThread(sendThread);
        String commitThread = Thread.currentThread().getName();

        transactionTemplate.executeWithoutResult(
                status -> context.publishEvent(applicationEvent())
        );

        assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(sendThread.get())
                .startsWith("study-push-")
                .isNotEqualTo(commitThread);
    }

    @Test
    void 푸시_전송이_느려도_커밋_스레드는_기다리지_않는다() throws InterruptedException {
        CountDownLatch sent = new CountDownLatch(1);
        doAnswer(invocation -> {
            try {
                Thread.sleep(300);
            } finally {
                sent.countDown();
            }
            return null;
        }).when(fcmPushGateway).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );

        long startedAt = System.nanoTime();
        transactionTemplate.executeWithoutResult(
                status -> context.publishEvent(applicationEvent())
        );
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
        );

        assertThat(elapsedMillis).isLessThan(100L);
        assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void 업무_트랜잭션이_롤백되면_푸시를_전송하지_않는다() throws InterruptedException {
        CountDownLatch sent = recordSendThread(new AtomicReference<>());

        transactionTemplate.executeWithoutResult(status -> {
            context.publishEvent(applicationEvent());
            status.setRollbackOnly();
        });

        assertThat(sent.await(200, TimeUnit.MILLISECONDS)).isFalse();
        verify(fcmPushGateway, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
    }

    @Test
    void 일정_푸시도_같은_풀에서_전송된다() throws InterruptedException {
        AtomicReference<String> sendThread = new AtomicReference<>();
        CountDownLatch sent = recordSendThread(sendThread);

        transactionTemplate.executeWithoutResult(status -> context.publishEvent(
                new StudySchedulePushRequestedEvent(
                        82L, 10L, 20L, "STUDY_SCHEDULE_UPDATED",
                        "일정 변경", "일정 변경 본문", true
                )
        ));

        assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(sendThread.get()).startsWith("study-push-");
    }

    @Test
    void 공지_푸시도_커밋_후_같은_풀에서_전송된다() throws InterruptedException {
        AtomicReference<String> sendThread = new AtomicReference<>();
        CountDownLatch sent = recordSendThread(sendThread);

        transactionTemplate.executeWithoutResult(status -> context.publishEvent(
                new StudyNoticePushRequestedEvent(
                        83L,
                        10L,
                        20L,
                        30L,
                        "새 스터디 공지가 등록되었습니다.",
                        "공지 본문",
                        true
                )
        ));

        assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(sendThread.get()).startsWith("study-push-");
    }

    private CountDownLatch recordSendThread(AtomicReference<String> sendThread) {
        CountDownLatch sent = new CountDownLatch(1);
        doAnswer(invocation -> {
            sendThread.set(Thread.currentThread().getName());
            sent.countDown();
            return null;
        }).when(fcmPushGateway).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
        return sent;
    }

    private StudyApplicationPushRequestedEvent applicationEvent() {
        return new StudyApplicationPushRequestedEvent(
                81L, 10L, 20L, "STUDY_APPLICATION_APPROVED", "STUDY_DETAIL",
                "신청 승인", "신청 승인 본문", true
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new TestTransactionManager();
        }
    }

    static class TestTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
