package com.ssafy.ssabangpalbang.study.service.port;

import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.repository.NotificationCommandRepository;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import com.ssafy.ssabangpalbang.study.event.StudyApplicationPushRequestedEvent;
import com.ssafy.ssabangpalbang.study.event.StudySchedulePushRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyNotificationPortImplTest {
    @Mock NotificationRepository notificationRepository;
    @Mock NotificationCommandRepository notificationCommandRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks StudyNotificationPortImpl port;

    @Test
    void 신청_접수_알림은_스터디장을_수신자로_신청자명을_본문에_담는다() {
        prepareInsertedNotification();
        port.notifyApplicationSubmitted(new StudyNotificationPort.ApplicationSubmittedNotification(
                8L, 7L, 10L, 25L, "검증 스터디", "신청자", true));

        Notification saved = captureInsertedNotification();
        assertThat(saved.getRecipientId()).isEqualTo(8L);
        assertThat(saved.getActorId()).isEqualTo(7L);
        assertThat(saved.getCategory().name()).isEqualTo("STUDY");
        assertThat(saved.getType()).isEqualTo("STUDY_APPLICATION_SUBMITTED");
        assertThat(saved.getTargetScreen()).isEqualTo("STUDY_MANAGE");
        assertThat(saved.getTargetId()).isEqualTo(10L);
        assertThat(saved.getTargetSubId()).isEqualTo(25L);
        assertThat(saved.getBody()).contains("신청자", "검증 스터디");
        assertThat(saved.getIdempotencyKey()).isEqualTo("STUDY_APPLICATION_SUBMITTED:25");
        ArgumentCaptor<StudyApplicationPushRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(StudyApplicationPushRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().notificationId()).isEqualTo(81L);
        assertThat(eventCaptor.getValue().targetScreen()).isEqualTo("STUDY_MANAGE");
        assertThat(eventCaptor.getValue().serviceNotificationAgreed()).isTrue();
    }

    @Test
    void 신청_접수_알림_멱등키_충돌은_예외_없이_스킵하고_이벤트를_발행하지_않는다() {
        when(notificationCommandRepository.insertIfAbsent(any(Notification.class)))
                .thenReturn(Optional.empty());

        assertThatCode(() -> port.notifyApplicationSubmitted(
                new StudyNotificationPort.ApplicationSubmittedNotification(
                        8L, 7L, 10L, 25L, "검증 스터디", "신청자", true)
        )).doesNotThrowAnyException();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 발행_값이_정본_규약대로_채워진다() {
        prepareInsertedNotification();
        port.notifyApplicationApproved(notification());

        Notification saved = captureInsertedNotification();
        assertThat(saved.getCategory().name()).isEqualTo("STUDY");
        assertThat(saved.getType()).isEqualTo("STUDY_APPLICATION_APPROVED");
        assertThat(saved.getTargetScreen()).isEqualTo("STUDY_DETAIL");
        assertThat(saved.getTargetId()).isEqualTo(10L);
        assertThat(saved.getTargetSubId()).isEqualTo(25L);
        assertThat(saved.getIdempotencyKey()).isEqualTo("STUDY_APPLICATION_APPROVED:25");
    }

    @Test
    void 같은_신청으로_두번_발행해도_예외_없이_스킵한다() {
        when(notificationCommandRepository.insertIfAbsent(any(Notification.class)))
                .thenReturn(Optional.empty());

        assertThatCode(() -> port.notifyApplicationRejected(notification())).doesNotThrowAnyException();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 취소_알림은_스터디명과_수신자별_멱등키를_사용한다() {
        when(notificationCommandRepository.insertIfAbsent(any(Notification.class)))
                .thenReturn(Optional.of(81L));
        port.notifyStudyCanceled(
                new StudyNotificationPort.StudyCanceledNotification(
                        8L, 7L, 10L, "검증 스터디"));

        Notification saved = captureInsertedNotification();
        assertThat(saved.getCategory().name()).isEqualTo("STUDY");
        assertThat(saved.getType()).isEqualTo("STUDY_CANCELED");
        assertThat(saved.getTargetScreen()).isEqualTo("STUDY_DETAIL");
        assertThat(saved.getTargetId()).isEqualTo(10L);
        assertThat(saved.getTargetSubId()).isNull();
        assertThat(saved.getBody()).contains("검증 스터디", "취소");
        assertThat(saved.getIdempotencyKey()).isEqualTo("STUDY_CANCELED:10:8");
    }

    @Test
    void 취소_알림_멱등키_충돌은_예외_없이_스킵한다() {
        when(notificationCommandRepository.insertIfAbsent(any(Notification.class)))
                .thenReturn(Optional.empty());

        assertThatCode(() -> port.notifyStudyCanceled(
                new StudyNotificationPort.StudyCanceledNotification(
                        8L, 7L, 10L, "검증 스터디")
        )).doesNotThrowAnyException();
    }

    @Test
    void 저장_실패_예외는_그대로_전파한다() {
        doThrow(new IllegalStateException("failure"))
                .when(notificationCommandRepository).insertIfAbsent(any(Notification.class));

        assertThatThrownBy(() -> port.notifyApplicationRejected(notification()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 일정_등록은_고정된_타입과_제목으로_저장한다() {
        prepareInsertedNotification();

        port.notifyScheduleCreated(scheduleNotification("검증 스터디", "옥수역"));

        Notification saved = captureInsertedNotification();
        assertThat(saved.getType()).isEqualTo("STUDY_SCHEDULE_CREATED");
        assertThat(saved.getTitle()).isEqualTo("임장 일정이 등록되었습니다.");
        assertThat(saved.getTargetScreen()).isEqualTo("STUDY_SCHEDULE");
        assertThat(saved.getIdempotencyKey())
                .isEqualTo("STUDY_SCHEDULE_CREATED:25:8:1000");
        assertThat(saved.getBody())
                .isEqualTo("'검증 스터디' 임장이 8월 15일 오후 3시 00분에 옥수역에서 시작해요.");
    }

    @Test
    void 일정_알림_시각은_입력_offset과_무관하게_서울_시각으로_표시한다() {
        prepareInsertedNotification();

        port.notifyScheduleCreated(new StudyNotificationPort.ScheduleNotification(
                8L, 7L, 10L, 25L, "검증 스터디",
                "2026-08-15T06:16:00Z", "옥수역", 1000L, true
        ));

        assertThat(captureInsertedNotification().getBody())
                .isEqualTo("'검증 스터디' 임장이 8월 15일 오후 3시 16분에 옥수역에서 시작해요.");
    }

    @Test
    void 일정_알림에_장소가_없어도_자연스러운_한국어_문장으로_저장한다() {
        prepareInsertedNotification();

        port.notifyScheduleCreated(scheduleNotification("검증 스터디", null));

        assertThat(captureInsertedNotification().getBody())
                .isEqualTo("'검증 스터디' 임장이 8월 15일 오후 3시 00분에 시작해요.");
    }

    @Test
    void 일정_수정은_고정된_SCHEDULE_CHANGED_타입으로_저장한다() {
        prepareInsertedNotification();

        port.notifyScheduleChanged(scheduleNotification("검증 스터디", "옥수역"));

        Notification saved = captureInsertedNotification();
        assertThat(saved.getType()).isEqualTo("SCHEDULE_CHANGED");
        assertThat(saved.getTitle()).isEqualTo("임장 일정이 변경되었습니다.");
        assertThat(saved.getBody())
                .isEqualTo(
                        "'검증 스터디' 임장이 8월 15일 오후 3시 00분에 "
                                + "옥수역에서 시작하는 일정으로 변경됐어요."
                );
    }

    @Test
    void 일정_취소는_별도_메서드가_고정된_취소_타입으로_저장한다() {
        prepareInsertedNotification();

        port.notifyScheduleCanceled(scheduleNotification("검증 스터디", "옥수역"));

        Notification saved = captureInsertedNotification();
        assertThat(saved.getType()).isEqualTo("STUDY_SCHEDULE_CANCELED");
        assertThat(saved.getTitle()).isEqualTo("임장 일정이 취소되었습니다.");
        assertThat(saved.getBody()).isEqualTo("'검증 스터디' 임장 일정이 취소됐어요.");
    }

    @Test
    void 일정_알림_body는_500자로_잘라_저장하고_이벤트에도_같이_담는다() {
        prepareInsertedNotification();
        ArgumentCaptor<StudySchedulePushRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(StudySchedulePushRequestedEvent.class);

        port.notifyScheduleCreated(scheduleNotification("가".repeat(500), "나".repeat(200)));

        Notification saved = captureInsertedNotification();
        assertThat(saved.getBody()).hasSize(500);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().body()).hasSize(500);
    }

    @Test
    void 일정_알림_멱등키_충돌은_이벤트를_발행하지_않는다() {
        when(notificationCommandRepository.insertIfAbsent(any(Notification.class)))
                .thenReturn(Optional.empty());

        assertThatCode(() -> port.notifyScheduleCreated(
                scheduleNotification("검증 스터디", "옥수역")
        )).doesNotThrowAnyException();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 리마인더는_기존_멱등키가_있으면_삽입_없이_false를_반환한다() {
        when(notificationRepository.findByIdempotencyKey(
                "STUDY_SCHEDULE_REMINDER:25:8:1000"))
                .thenReturn(Optional.of(existingNotification()));

        boolean created = port.notifyScheduleReminder(
                scheduleNotification("검증 스터디", "옥수역"));

        assertThat(created).isFalse();
        verify(notificationCommandRepository, never()).insertIfAbsent(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 리마인더_삽입_경합_충돌도_예외_없이_false를_반환한다() {
        when(notificationRepository.findByIdempotencyKey(
                "STUDY_SCHEDULE_REMINDER:25:8:1000"))
                .thenReturn(Optional.empty());
        when(notificationCommandRepository.insertIfAbsent(any(Notification.class)))
                .thenReturn(Optional.empty());

        boolean created = port.notifyScheduleReminder(
                scheduleNotification("검증 스터디", "옥수역"));

        assertThat(created).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
    }

    private StudyNotificationPort.ApplicationDecisionNotification notification() {
        return new StudyNotificationPort.ApplicationDecisionNotification(
                8L, 7L, 10L, 25L, "검증 스터디", true
        );
    }

    private StudyNotificationPort.ScheduleNotification scheduleNotification(
            String studyTitle,
            String meetingPlace
    ) {
        return new StudyNotificationPort.ScheduleNotification(
                8L,
                7L,
                10L,
                25L,
                studyTitle,
                "2026-08-15T15:00:00+09:00",
                meetingPlace,
                1000L,
                true
        );
    }

    private Notification existingNotification() {
        return Notification.create(
                8L, null, com.ssafy.ssabangpalbang.notification.entity.NotificationCategory.STUDY,
                "STUDY_SCHEDULE_REMINDER", "STUDY_SCHEDULE", 10L, 25L,
                "내일 임장이 있어요", "본문",
                "STUDY_SCHEDULE_REMINDER:25:8:1000",
                java.time.OffsetDateTime.now(java.time.ZoneId.of("Asia/Seoul"))
        );
    }

    private void prepareInsertedNotification() {
        when(notificationCommandRepository.insertIfAbsent(any(Notification.class)))
                .thenReturn(Optional.of(81L));
    }

    private Notification captureInsertedNotification() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationCommandRepository).insertIfAbsent(captor.capture());
        return captor.getValue();
    }
}
