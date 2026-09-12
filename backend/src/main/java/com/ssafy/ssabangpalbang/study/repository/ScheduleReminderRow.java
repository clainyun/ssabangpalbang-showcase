package com.ssafy.ssabangpalbang.study.repository;

import java.time.Instant;

public interface ScheduleReminderRow {

    Long getScheduleId();

    Long getStudyId();

    String getStudyTitle();

    Long getLeaderId();

    Instant getStartAt();

    String getMeetingPlace();
}
