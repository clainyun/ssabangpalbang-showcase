package com.ssafy.ssabangpalbang.study.dto.response;
import com.ssafy.ssabangpalbang.study.domain.Schedule;
public record StudyScheduleDeleteResponse(Long studyId, Long scheduleId, String status, String updatedAt) { public static StudyScheduleDeleteResponse from(Schedule s){return new StudyScheduleDeleteResponse(s.getStudyId(),s.getId(),s.getStatus().name(),StudyScheduleCreateResponse.f(s.getUpdatedAt()));}}
