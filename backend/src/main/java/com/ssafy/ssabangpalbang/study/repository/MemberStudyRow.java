package com.ssafy.ssabangpalbang.study.repository;

import java.time.Instant;

public interface MemberStudyRow {

    Long getStudyId();

    String getTitle();

    String getIntro();

    String getGoal();

    String getStatus();

    String getRole();

    Long getApartmentId();

    String getApartmentName();

    Long getScheduleId();

    Instant getStartAt();

    String getMeetingPlace();

    Long getUnreadChatCount();

    Long getPendingReviewCount();

    Boolean getHasReturnableFieldVisit();
}
