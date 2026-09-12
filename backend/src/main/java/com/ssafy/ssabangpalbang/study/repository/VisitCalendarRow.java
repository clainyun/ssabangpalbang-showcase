package com.ssafy.ssabangpalbang.study.repository;

import java.time.Instant;

public interface VisitCalendarRow {

    Long getScheduleId();

    Long getStudyId();

    String getStudyTitle();

    String getApartmentName();

    Instant getStartAt();

    String getScheduleStatus();
}
