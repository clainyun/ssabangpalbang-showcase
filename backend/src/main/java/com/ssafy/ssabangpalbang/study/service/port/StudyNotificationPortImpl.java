package com.ssafy.ssabangpalbang.study.service.port;

import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.entity.NotificationCategory;
import com.ssafy.ssabangpalbang.notification.repository.NotificationCommandRepository;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import com.ssafy.ssabangpalbang.study.event.StudyApplicationPushRequestedEvent;
import com.ssafy.ssabangpalbang.study.event.StudySchedulePushRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StudyNotificationPortImpl implements StudyNotificationPort {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter SCHEDULE_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("M월 d일 a h시 mm분", Locale.KOREAN);
    private static final String TARGET_SCREEN = "STUDY_DETAIL";

    private final NotificationRepository notificationRepository;
    private final NotificationCommandRepository notificationCommandRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void notifyApplicationSubmitted(ApplicationSubmittedNotification notification) {
        String type = "STUDY_APPLICATION_SUBMITTED";
        String title = "새로운 스터디 신청이 도착했습니다.";
        String body = "'%s'님이 '%s' 스터디에 참여를 신청했어요.".formatted(
                notification.applicantNickname(), notification.studyTitle());
        // ON CONFLICT DO NOTHING이므로 idempotency_key 충돌이
        // 호출자의 비즈니스 트랜잭션을 abort시키지 않는다. 충돌 시 알림만 스킵한다.
        notificationCommandRepository.insertIfAbsent(Notification.create(
                        notification.recipientId(), notification.actorId(), NotificationCategory.STUDY,
                        type, "STUDY_MANAGE", notification.studyId(), notification.applicationId(),
                        title,
                        body,
                        type + ":" + notification.applicationId(),
                        OffsetDateTime.now(SEOUL_ZONE_ID)))
                .ifPresent(notificationId -> publishApplicationPush(
                        notificationId, notification.recipientId(), notification.studyId(),
                        type, "STUDY_MANAGE", title, body,
                        notification.serviceNotificationAgreed()));
    }

    @Override
    public void notifyApplicationApproved(ApplicationDecisionNotification notification) {
        publish(notification, "STUDY_APPLICATION_APPROVED",
                "스터디 신청이 승인되었습니다.",
                "'%s' 스터디에 참여하게 되었어요.".formatted(notification.studyTitle()));
    }

    @Override
    public void notifyApplicationRejected(ApplicationDecisionNotification notification) {
        publish(notification, "STUDY_APPLICATION_REJECTED",
                "스터디 신청이 거절되었습니다.",
                "'%s' 스터디 신청이 거절되었어요.".formatted(notification.studyTitle()));
    }

    @Override
    public void notifyStudyCanceled(StudyCanceledNotification notification) {
        // 수신자별 idempotency_key 충돌은 이미 발행된 동일 취소 알림이므로 스킵된다.
        notificationCommandRepository.insertIfAbsent(Notification.create(
                notification.recipientId(),
                notification.actorId(),
                NotificationCategory.STUDY,
                "STUDY_CANCELED",
                TARGET_SCREEN,
                notification.studyId(),
                null,
                "스터디가 취소되었습니다.",
                "'%s' 스터디가 취소되었습니다.".formatted(notification.studyTitle()),
                "STUDY_CANCELED:%d:%d".formatted(
                        notification.studyId(), notification.recipientId()),
                OffsetDateTime.now(SEOUL_ZONE_ID)
        ));
    }

    @Override
    public void notifyScheduleCreated(ScheduleNotification notification) {
        publishSchedule(
                notification,
                "STUDY_SCHEDULE_CREATED",
                "임장 일정이 등록되었습니다.",
                scheduleBody(notification, "시작해요.")
        );
    }

    @Override
    public void notifyScheduleChanged(ScheduleNotification notification) {
        publishSchedule(
                notification,
                "SCHEDULE_CHANGED",
                "임장 일정이 변경되었습니다.",
                scheduleBody(notification, "시작하는 일정으로 변경됐어요.")
        );
    }

    @Override
    public void notifyScheduleCanceled(ScheduleNotification notification) {
        publishSchedule(
                notification,
                "STUDY_SCHEDULE_CANCELED",
                "임장 일정이 취소되었습니다.",
                "'%s' 임장 일정이 취소됐어요.".formatted(notification.studyTitle())
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean notifyScheduleReminder(ScheduleNotification notification) {
        String idempotencyKey = scheduleIdempotencyKey(
                "STUDY_SCHEDULE_REMINDER",
                notification
        );
        if (notificationRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return false;
        }
        String body = notification.meetingPlace() == null
                ? "'%s' 임장이 %s에 시작해요.".formatted(
                        notification.studyTitle(),
                        scheduleStartAtText(notification)
                )
                : "'%s' 임장이 %s에 %s에서 시작해요.".formatted(
                        notification.studyTitle(),
                        scheduleStartAtText(notification),
                        notification.meetingPlace()
                );
        return publishSchedule(
                notification,
                "STUDY_SCHEDULE_REMINDER",
                "내일 임장이 있어요",
                body
        );
    }

    private String scheduleBody(ScheduleNotification notification, String suffix) {
        String place = notification.meetingPlace() == null
                ? ""
                : " " + notification.meetingPlace() + "에서";
        return "'%s' 임장이 %s에%s %s".formatted(
                notification.studyTitle(),
                scheduleStartAtText(notification),
                place,
                suffix
        );
    }

    private String scheduleStartAtText(ScheduleNotification notification) {
        return OffsetDateTime.parse(notification.startAtText())
                .atZoneSameInstant(SEOUL_ZONE_ID)
                .format(SCHEDULE_DATE_TIME_FORMATTER);
    }

    private boolean publishSchedule(
            ScheduleNotification notification,
            String type,
            String title,
            String body
    ) {
        String storedBody = body.length() > 500 ? body.substring(0, 500) : body;
        // idempotency_key 충돌은 이미 발행된 동일 일정 알림이므로
        // 삽입과 푸시 이벤트 발행을 모두 스킵한다(트랜잭션 abort 없음).
        Optional<Long> notificationId = notificationCommandRepository.insertIfAbsent(
                Notification.create(
                        notification.recipientId(),
                        notification.actorId(),
                        NotificationCategory.STUDY,
                        type,
                        "STUDY_SCHEDULE",
                        notification.studyId(),
                        notification.scheduleId(),
                        title,
                        storedBody,
                        scheduleIdempotencyKey(type, notification),
                        OffsetDateTime.now(SEOUL_ZONE_ID)
                ));
        notificationId.ifPresent(id -> eventPublisher.publishEvent(new StudySchedulePushRequestedEvent(
                id,
                notification.recipientId(),
                notification.studyId(),
                type,
                title,
                storedBody,
                notification.serviceNotificationAgreed()
        )));
        return notificationId.isPresent();
    }

    private String scheduleIdempotencyKey(
            String type,
            ScheduleNotification notification
    ) {
        return type + ":" + notification.scheduleId() + ":"
                + notification.recipientId() + ":"
                + notification.versionEpochMilli();
    }

    private void publish(
            ApplicationDecisionNotification notification,
            String type,
            String title,
            String body
    ) {
        // idempotency_key 충돌은 이미 발행된 동일 결과 알림이므로 스킵한다(트랜잭션 abort 없음).
        notificationCommandRepository.insertIfAbsent(Notification.create(
                        notification.recipientId(), notification.actorId(), NotificationCategory.STUDY,
                        type, TARGET_SCREEN, notification.studyId(), notification.applicationId(),
                        title, body, type + ":" + notification.applicationId(),
                        OffsetDateTime.now(SEOUL_ZONE_ID)))
                .ifPresent(notificationId -> publishApplicationPush(
                        notificationId, notification.recipientId(), notification.studyId(),
                        type, TARGET_SCREEN, title, body,
                        notification.serviceNotificationAgreed()));
    }

    private void publishApplicationPush(
            Long notificationId,
            Long recipientId,
            Long studyId,
            String type,
            String targetScreen,
            String title,
            String body,
            boolean serviceNotificationAgreed
    ) {
        eventPublisher.publishEvent(new StudyApplicationPushRequestedEvent(
                notificationId, recipientId, studyId, type, targetScreen,
                title, body, serviceNotificationAgreed
        ));
    }
}
