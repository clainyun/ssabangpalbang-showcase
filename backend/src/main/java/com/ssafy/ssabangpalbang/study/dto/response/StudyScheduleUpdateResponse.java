package com.ssafy.ssabangpalbang.study.dto.response;
import com.ssafy.ssabangpalbang.study.domain.Schedule;
public record StudyScheduleUpdateResponse(Long scheduleId, Long studyId, String status, String startAt, String endAt, String meetingPlace, String updatedAt) { public static StudyScheduleUpdateResponse from(Schedule s){var r=StudyScheduleCreateResponse.from(s);return new StudyScheduleUpdateResponse(r.scheduleId(),r.studyId(),r.status(),r.startAt(),r.endAt(),r.meetingPlace(),r.updatedAt());}}
