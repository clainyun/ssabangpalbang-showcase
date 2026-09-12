package com.ssafy.ssabangpalbang.study.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "sbpb.schedule-reminder",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class StudyScheduleReminderScheduler {

    private final StudyScheduleReminderService service;

    @Scheduled(cron = "${sbpb.schedule-reminder.cron:0 */10 * * * *}")
    public void scan() {
        try {
            StudyScheduleReminderService.ReminderResult result =
                    service.sendDueReminders(Instant.now());
            log.info(
                    "임장 D-1 알림 스캔 완료: scannedSchedules={}, createdNotifications={}, skippedDuplicates={}",
                    result.scannedSchedules(),
                    result.createdNotifications(),
                    result.skippedDuplicates()
            );
        } catch (RuntimeException exception) {
            log.error("임장 D-1 알림 스캔에 실패했습니다. reason=internal_error");
        }
    }
}
