package com.ssafy.ssabangpalbang.notification.integration;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushGateway;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import com.ssafy.ssabangpalbang.report.integration.ReportCompletedEvent;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportCompletionPushTransactionTest {

    private static final long REPORT_ID = 48L;
    private static final long STUDY_ID = 7L;
    private static final long MEMBER_ID = 3L;
    private static final Instant NOW = Instant.parse("2026-08-03T11:30:00Z");

    private StudyMemberRepository studyMemberRepository;
    private MemberRepository memberRepository;
    private NotificationRepository notificationRepository;
    private FcmTokenRepository fcmTokenRepository;
    private FcmPushGateway fcmPushGateway;
    private AnnotationConfigApplicationContext context;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        studyMemberRepository = mock(StudyMemberRepository.class);
        memberRepository = mock(MemberRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        fcmTokenRepository = mock(FcmTokenRepository.class);
        fcmPushGateway = mock(FcmPushGateway.class);

        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class);
        context.registerBean(
                ReportCompletionNotificationListener.class,
                () -> new ReportCompletionNotificationListener(
                        studyMemberRepository,
                        memberRepository,
                        notificationRepository,
                        context,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                )
        );
        context.registerBean(
                ReportCompletionPushListener.class,
                () -> new ReportCompletionPushListener(
                        fcmTokenRepository,
                        fcmPushGateway
                )
        );
        context.refresh();
        transactionTemplate = new TransactionTemplate(
                context.getBean(PlatformTransactionManager.class)
        );
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void 커밋_전_생성된_푸시_이벤트를_커밋_후에_전송한다() {
        prepareSuccessfulDelivery();

        transactionTemplate.executeWithoutResult(status -> {
            context.publishEvent(new ReportCompletedEvent(REPORT_ID, STUDY_ID));

            verify(fcmPushGateway, never()).sendNotification(
                    anyString(), anyString(), anyString(), anyMap()
            );
        });

        verify(fcmPushGateway).sendNotification(
                "fcm-token",
                "AI 임장 리포트가 완성됐어요",
                "임장 리포트가 생성되었습니다.",
                Map.of(
                        "notificationType", "REPORT_COMPLETED",
                        "targetScreen", "REPORT_DETAIL",
                        "targetId", "48",
                        "notificationId", "81"
                )
        );
    }

    @Test
    void 원본_트랜잭션이_롤백되면_알림과_FCM을_처리하지_않는다() {
        transactionTemplate.executeWithoutResult(status -> {
            context.publishEvent(new ReportCompletedEvent(REPORT_ID, STUDY_ID));
            status.setRollbackOnly();
        });

        verify(notificationRepository, never()).saveAndFlush(
                any(Notification.class)
        );
        verify(fcmPushGateway, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
    }

    private void prepareSuccessfulDelivery() {
        when(studyMemberRepository.findByStudyIdAndStatus(
                STUDY_ID,
                StudyMemberStatus.ACTIVE
        )).thenReturn(List.of(StudyMember.createLeader(STUDY_ID, MEMBER_ID)));
        when(memberRepository.findAllById(List.of(MEMBER_ID)))
                .thenReturn(List.of(member()));
        when(notificationRepository.findByIdempotencyKey(
                "REPORT_COMPLETED:48:3"
        )).thenReturn(java.util.Optional.empty());
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);
                    ReflectionTestUtils.setField(notification, "id", 81L);
                    return notification;
                });
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(fcmTokenRepository.findAllByMemberId(MEMBER_ID)).thenReturn(
                List.of(FcmToken.of(
                        MEMBER_ID,
                        "fcm-token",
                        "device-id",
                        OffsetDateTime.parse("2026-08-04T11:00:00+09:00")
                ))
        );
    }

    private Member member() {
        Member member = new Member("member@example.com", "hash", "member");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
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
