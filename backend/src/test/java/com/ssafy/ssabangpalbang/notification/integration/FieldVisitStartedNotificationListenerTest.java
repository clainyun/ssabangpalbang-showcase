package com.ssafy.ssabangpalbang.notification.integration;

import com.ssafy.ssabangpalbang.fieldvisit.integration.FieldVisitSessionStartedEvent;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldVisitStartedNotificationListenerTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private FieldVisitStartedNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new FieldVisitStartedNotificationListener(
                memberRepository, notificationRepository, eventPublisher
        );
    }

    @Test
    void 미동의_수신자도_앱_알림은_저장하고_푸시_이벤트에_동의를_담는다() {
        when(memberRepository.findAllById(List.of(43L, 44L))).thenReturn(List.of(
                member(43L, true), member(44L, false)
        ));
        when(notificationRepository.findByIdempotencyKey(anyString()))
                .thenReturn(Optional.empty());
        AtomicLong sequence = new AtomicLong(80L);
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);
                    ReflectionTestUtils.setField(
                            notification, "id", sequence.incrementAndGet()
                    );
                    return notification;
                });

        listener.createBeforeCommit(event(List.of(43L, 44L)));

        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(2))
                .saveAndFlush(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues())
                .allSatisfy(notification -> {
                    assertThat(notification.getActorId()).isEqualTo(42L);
                    assertThat(notification.getCategory().name()).isEqualTo("FIELD");
                    assertThat(notification.getType()).isEqualTo("FIELD_VISIT_STARTED");
                    assertThat(notification.getTargetScreen()).isEqualTo("FIELD_VISIT");
                    assertThat(notification.getTargetId()).isEqualTo(7L);
                    assertThat(notification.getTargetSubId()).isEqualTo(100L);
                });
        assertThat(notificationCaptor.getAllValues())
                .extracting(Notification::getIdempotencyKey)
                .containsExactly(
                        "FIELD_VISIT_STARTED:100:43",
                        "FIELD_VISIT_STARTED:100:44"
                );

        ArgumentCaptor<FieldVisitStartedPushRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(FieldVisitStartedPushRequestedEvent.class);
        verify(eventPublisher, org.mockito.Mockito.times(2))
                .publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(FieldVisitStartedPushRequestedEvent::serviceNotificationAgreed)
                .containsExactly(true, false);
    }

    @Test
    void 같은_세션과_수신자의_알림이_있으면_저장과_푸시를_생략한다() {
        when(memberRepository.findAllById(List.of(43L)))
                .thenReturn(List.of(member(43L, true)));
        when(notificationRepository.findByIdempotencyKey(
                "FIELD_VISIT_STARTED:100:43"
        )).thenReturn(Optional.of(org.mockito.Mockito.mock(Notification.class)));

        listener.createBeforeCommit(event(List.of(43L)));

        verify(notificationRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private FieldVisitSessionStartedEvent event(List<Long> recipients) {
        return new FieldVisitSessionStartedEvent(
                7L, 100L, 42L, "검증 스터디",
                Instant.parse("2026-08-05T01:00:00Z"), recipients
        );
    }

    private Member member(Long id, boolean agreed) {
        Member member = new Member("member" + id + "@example.com", "hash", "회원" + id);
        ReflectionTestUtils.setField(member, "id", id);
        member.updateNotificationSettings(agreed, null);
        return member;
    }
}
