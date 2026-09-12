package com.ssafy.ssabangpalbang.study.repository;

import java.time.Instant;

public interface RecruitingStudyRow {
    Long getStudyId();
    String getTitle();
    String getIntro();
    String getGoal();
    String getPurpose();
    Integer getCapacity();
    Long getLeaderId();
    int getCurrentMemberCount();
    int getRemainingCapacity();
    Long getScheduleId();
    Instant getStartAt();
    Instant getEndAt();
    String getMeetingPlace();
}
