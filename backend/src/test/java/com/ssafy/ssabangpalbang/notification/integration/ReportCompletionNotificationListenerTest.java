package com.ssafy.ssabangpalbang.notification.integration;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.entity.NotificationCategory;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import com.ssafy.ssabangpalbang.report.integration.ReportCompletedEvent;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportCompletionNotificationListenerTest {

    private static final long REPORT_ID = 48L;
    private static final long STUDY_ID = 7L;
    private static final long MEMBER_ID = 3L;
    private static final Instant NOW = Instant.parse("2026-08-03T11:30:00Z");

    @Mock
    private StudyMemberRepository studyMemberRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void 완료_리포트_알림을_활성_스터디원에게_저장한다() {
        when(studyMemberRepository.findByStudyIdAndStatus(
                STUDY_ID,
                StudyMemberStatus.ACTIVE
        )).thenReturn(List.of(StudyMember.createLeader(STUDY_ID, MEMBER_ID)));
        when(notificationRepository.findByIdempotencyKey(
                "REPORT_COMPLETED:48:3"
        )).thenReturn(Optional.empty());
        when(memberRepository.findAllById(List.of(MEMBER_ID)))
                .thenReturn(List.of(member(true)));
        prepareSavedNotification();

        listener().createBeforeCommit(event());

        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        Notification notification = captor.getValue();
        assertThat(notification.getRecipientId()).isEqualTo(MEMBER_ID);
        assertThat(notification.getActorId()).isNull();
        assertThat(notification.getCategory())
                .isEqualTo(NotificationCategory.REPORT);
        assertThat(notification.getType()).isEqualTo("REPORT_COMPLETED");
        assertThat(notification.getTargetScreen()).isEqualTo("REPORT_DETAIL");
        assertThat(notification.getTargetId()).isEqualTo(REPORT_ID);
        assertThat(notification.getTitle())
                .isEqualTo("AI 임장 리포트가 완성됐어요");
        assertThat(notification.getBody())
                .isEqualTo("임장 리포트가 생성되었습니다.");
        assertThat(notification.getIdempotencyKey())
                .isEqualTo("REPORT_COMPLETED:48:3");
        assertThat(notification.getSentAt())
                .isEqualTo(OffsetDateTime.parse("2026-08-03T20:30:00+09:00"));

        ArgumentCaptor<ReportCompletionPushRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        ReportCompletionPushRequestedEvent.class
                );
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ReportCompletionPushRequestedEvent pushEvent = eventCaptor.getValue();
        assertThat(pushEvent.notificationId()).isEqualTo(81L);
        assertThat(pushEvent.recipientId()).isEqualTo(MEMBER_ID);
        assertThat(pushEvent.reportId()).isEqualTo(REPORT_ID);
        assertThat(pushEvent.serviceNotificationAgreed()).isTrue();
        assertThat(pushEvent.title())
                .isEqualTo("AI 임장 리포트가 완성됐어요");
        assertThat(pushEvent.body())
                .isEqualTo("임장 리포트가 생성되었습니다.");
    }

    @Test
    void 서비스_알림_미동의도_DB에는_저장하고_이벤트에_고정한다() {
        when(studyMemberRepository.findByStudyIdAndStatus(
                STUDY_ID,
                StudyMemberStatus.ACTIVE
        )).thenReturn(List.of(StudyMember.createLeader(STUDY_ID, MEMBER_ID)));
        when(memberRepository.findAllById(List.of(MEMBER_ID)))
                .thenReturn(List.of(member(false)));
        when(notificationRepository.findByIdempotencyKey(
                "REPORT_COMPLETED:48:3"
        )).thenReturn(Optional.empty());
        prepareSavedNotification();

        listener().createBeforeCommit(event());

        verify(notificationRepository).saveAndFlush(any(Notification.class));
        ArgumentCaptor<ReportCompletionPushRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        ReportCompletionPushRequestedEvent.class
                );
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().serviceNotificationAgreed())
                .isFalse();
    }

    @Test
    void 동일_리포트와_수신자의_기존_알림은_중복_저장하지_않는다() {
        String key = "REPORT_COMPLETED:48:3";
        when(studyMemberRepository.findByStudyIdAndStatus(
                STUDY_ID,
                StudyMemberStatus.ACTIVE
        )).thenReturn(List.of(StudyMember.createLeader(STUDY_ID, MEMBER_ID)));
        when(notificationRepository.findByIdempotencyKey(key))
                .thenReturn(Optional.of(Notification.create(
                        MEMBER_ID,
                        null,
                        NotificationCategory.REPORT,
                        "REPORT_COMPLETED",
                        "REPORT_DETAIL",
                        REPORT_ID,
                        null,
                        "title",
                        "body",
                        key,
                        OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)
                )));

        listener().createBeforeCommit(event());

        verify(notificationRepository, never()).saveAndFlush(
                org.mockito.ArgumentMatchers.any(Notification.class)
        );
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 활성_스터디원이_없으면_완료_트랜잭션을_실패시킨다() {
        when(studyMemberRepository.findByStudyIdAndStatus(
                STUDY_ID,
                StudyMemberStatus.ACTIVE
        )).thenReturn(List.of());

        assertThatThrownBy(() -> listener().createBeforeCommit(event()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 알림은_리포트_완료_트랜잭션의_커밋_전에_저장한다()
            throws NoSuchMethodException {
        Method method = ReportCompletionNotificationListener.class
                .getDeclaredMethod(
                        "createBeforeCommit",
                        ReportCompletedEvent.class
                );
        TransactionalEventListener annotation = method.getAnnotation(
                TransactionalEventListener.class
        );

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.BEFORE_COMMIT);
    }

    private ReportCompletionNotificationListener listener() {
        return new ReportCompletionNotificationListener(
                studyMemberRepository,
                memberRepository,
                notificationRepository,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private void prepareSavedNotification() {
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);
                    ReflectionTestUtils.setField(notification, "id", 81L);
                    return notification;
                });
    }

    private Member member(boolean serviceNotificationAgreed) {
        Member member = new Member("member@example.com", "hash", "member");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        member.updateNotificationSettings(serviceNotificationAgreed, null);
        return member;
    }

    private ReportCompletedEvent event() {
        return new ReportCompletedEvent(REPORT_ID, STUDY_ID);
    }
}
