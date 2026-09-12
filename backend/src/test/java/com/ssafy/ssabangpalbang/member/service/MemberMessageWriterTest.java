package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.member.event.MemberMessagePushRequestedEvent;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberMessageWriterTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void 알림_저장에_성공하면_커밋후_FCM용_이벤트를_발행한다() {
        OffsetDateTime sentAt = OffsetDateTime.parse(
                "2026-07-25T11:00:00+09:00"
        );
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);
                    ReflectionTestUtils.setField(notification, "id", 81L);
                    return notification;
                });
        MemberMessageWriter writer = new MemberMessageWriter(
                notificationRepository,
                eventPublisher
        );

        Notification saved = writer.save(
                1L,
                12L,
                true,
                "새로운 메시지가 도착했어요",
                "다음 임장도 같이 참여해요!",
                "member-message:1:8e70e108-7c81-477a-bb55-a9f334fb5e67",
                sentAt
        );

        assertThat(saved.getRecipientId()).isEqualTo(12L);
        assertThat(saved.getActorId()).isEqualTo(1L);
        assertThat(saved.getCategory().name()).isEqualTo("MESSAGE");
        assertThat(saved.getType()).isEqualTo("MESSAGE");
        assertThat(saved.getTargetScreen()).isEqualTo("MEMBER_PROFILE");
        assertThat(saved.getTargetId()).isEqualTo(1L);
        assertThat(saved.getTargetSubId()).isNull();
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getSentAt()).isEqualTo(sentAt);

        ArgumentCaptor<MemberMessagePushRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        MemberMessagePushRequestedEvent.class
                );
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().notificationId()).isEqualTo(81L);
        assertThat(eventCaptor.getValue().recipientId()).isEqualTo(12L);
        assertThat(eventCaptor.getValue().senderId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().serviceNotificationAgreed())
                .isTrue();
    }
}
