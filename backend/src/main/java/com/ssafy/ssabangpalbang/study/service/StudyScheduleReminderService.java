package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.study.repository.ScheduleReminderRow;
import com.ssafy.ssabangpalbang.study.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyScheduleReminderService {

    private final ScheduleRepository scheduleRepository;
    private final StudyScheduleReminderWriter writer;
    private final ScheduleReminderProperties properties;

    @Transactional(readOnly = true)
    public ReminderResult sendDueReminders(Instant now) {
        Instant until = now.plus(Duration.ofHours(properties.leadTimeHours()));
        List<ScheduleReminderRow> rows =
                scheduleRepository.findRemindableSchedules(now, until);

        int created = 0;
        int skipped = 0;
        for (ScheduleReminderRow row : rows) {
            try {
                StudyScheduleReminderWriter.WriterResult result =
                        writer.sendForSchedule(row);
                created += result.createdNotifications();
                skipped += result.skippedDuplicates();
            } catch (RuntimeException exception) {
                log.warn(
                        "임장 D-1 일정 처리에 실패했습니다. scheduleId={}, reason=internal_error",
                        row.getScheduleId()
                );
            }
        }
        return new ReminderResult(rows.size(), created, skipped);
    }

    public record ReminderResult(
            int scannedSchedules,
            int createdNotifications,
            int skippedDuplicates
    ) {
    }
}
