package com.ssafy.ssabangpalbang.study.service.port;

public interface StudyNotificationPort {

    void notifyApplicationSubmitted(ApplicationSubmittedNotification notification);

    void notifyApplicationApproved(ApplicationDecisionNotification notification);

    void notifyApplicationRejected(ApplicationDecisionNotification notification);

    void notifyStudyCanceled(StudyCanceledNotification notification);

    void notifyScheduleCreated(ScheduleNotification notification);

    void notifyScheduleChanged(ScheduleNotification notification);

    void notifyScheduleCanceled(ScheduleNotification notification);

    boolean notifyScheduleReminder(ScheduleNotification notification);

    record ApplicationSubmittedNotification(
            Long recipientId,
            Long actorId,
            Long studyId,
            Long applicationId,
            String studyTitle,
            String applicantNickname,
            boolean serviceNotificationAgreed
    ) {
    }

    record ApplicationDecisionNotification(
            Long recipientId,
            Long actorId,
            Long studyId,
            Long applicationId,
            String studyTitle,
            boolean serviceNotificationAgreed
    ) {
    }

    record StudyCanceledNotification(
            Long recipientId,
            Long actorId,
            Long studyId,
            String studyTitle
    ) {
    }

    record ScheduleNotification(
            Long recipientId,
            Long actorId,
            Long studyId,
            Long scheduleId,
            String studyTitle,
            String startAtText,
            String meetingPlace,
            long versionEpochMilli,
            boolean serviceNotificationAgreed
    ) {
    }
}
