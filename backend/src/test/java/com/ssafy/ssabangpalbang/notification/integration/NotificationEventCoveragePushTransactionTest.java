package com.ssafy.ssabangpalbang.notification.integration;

import com.ssafy.ssabangpalbang.fieldvisit.integration.FieldVisitSessionStartedEvent;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.event.MemberFollowPushListener;
import com.ssafy.ssabangpalbang.member.event.MemberFollowPushRequestedEvent;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushGateway;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import com.ssafy.ssabangpalbang.study.event.StudyApplicationPushListener;
import com.ssafy.ssabangpalbang.study.event.StudyApplicationPushRequestedEvent;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEventCoveragePushTransactionTest {

    private FcmTokenRepository fcmTokenRepository;
    private FcmPushGateway fcmPushGateway;
    private MemberRepository memberRepository;
    private NotificationRepository notificationRepository;
    private AnnotationConfigApplicationContext context;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        fcmTokenRepository = mock(FcmTokenRepository.class);
        fcmPushGateway = mock(FcmPushGateway.class);
        memberRepository = mock(MemberRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class);
        context.registerBean(
                StudyApplicationPushListener.class,
                () -> new StudyApplicationPushListener(
                        fcmTokenRepository, fcmPushGateway
                )
        );
        context.registerBean(
                MemberFollowPushListener.class,
                () -> new MemberFollowPushListener(
                        fcmTokenRepository, fcmPushGateway
                )
        );
        context.registerBean(
                FieldVisitStartedNotificationListener.class,
                () -> new FieldVisitStartedNotificationListener(
                        memberRepository, notificationRepository, context
                )
        );
        context.registerBean(
                FieldVisitStartedPushListener.class,
                () -> new FieldVisitStartedPushListener(
                        fcmTokenRepository, fcmPushGateway
                )
        );
        context.refresh();
        transactionTemplate = new TransactionTemplate(
                context.getBean(PlatformTransactionManager.class)
        );
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(memberRepository.findAllById(List.of(43L)))
                .thenReturn(List.of(member(43L)));
        when(notificationRepository.findByIdempotencyKey(
                "FIELD_VISIT_STARTED:100:43"
        )).thenReturn(Optional.empty());
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);
                    ReflectionTestUtils.setField(notification, "id", 83L);
                    return notification;
                });
        when(fcmTokenRepository.findAllByMemberId(anyLong())).thenAnswer(
                invocation -> List.of(FcmToken.of(
                        invocation.getArgument(0),
                        "fcm-token",
                        "device-id",
                        OffsetDateTime.parse("2026-08-05T10:00:00+09:00")
                ))
        );
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void 세_이벤트군의_푸시는_업무_트랜잭션_커밋_후에만_전송한다() {
        transactionTemplate.executeWithoutResult(status -> {
            publishAllEvents();
            verify(fcmPushGateway, never()).sendNotification(
                    anyString(), anyString(), anyString(), anyMap()
            );
        });

        verify(fcmPushGateway, times(3)).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
        verify(notificationRepository).saveAndFlush(any(Notification.class));
    }

    @Test
    void 업무_트랜잭션이_롤백되면_세_이벤트군_푸시를_전송하지_않는다() {
        transactionTemplate.executeWithoutResult(status -> {
            publishAllEvents();
            status.setRollbackOnly();
        });

        verify(fcmPushGateway, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
        verify(notificationRepository, never()).saveAndFlush(any(Notification.class));
    }

    private void publishAllEvents() {
        context.publishEvent(new StudyApplicationPushRequestedEvent(
                81L, 8L, 10L, "STUDY_APPLICATION_APPROVED", "STUDY_DETAIL",
                "승인", "승인 본문", true
        ));
        context.publishEvent(new MemberFollowPushRequestedEvent(
                82L, 15L, 1L, true, "팔로우", "팔로우 본문"
        ));
        context.publishEvent(new FieldVisitSessionStartedEvent(
                7L,
                100L,
                42L,
                "검증 스터디",
                Instant.parse("2026-08-05T01:00:00Z"),
                List.of(43L)
        ));
    }

    private Member member(Long id) {
        Member member = new Member(
                "member" + id + "@example.com", "hash", "회원" + id
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
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
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition
        ) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
