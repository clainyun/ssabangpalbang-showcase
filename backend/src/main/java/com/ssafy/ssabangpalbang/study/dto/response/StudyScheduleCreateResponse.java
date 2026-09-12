package com.ssafy.ssabangpalbang.study.dto.response;
import com.ssafy.ssabangpalbang.study.domain.Schedule;
import java.time.*; import java.time.format.DateTimeFormatter;
public record StudyScheduleCreateResponse(Long scheduleId, Long studyId, String status, String startAt, String endAt, String meetingPlace, String createdAt, String updatedAt) {
 public static StudyScheduleCreateResponse from(Schedule s){return new StudyScheduleCreateResponse(s.getId(),s.getStudyId(),s.getStatus().name(),f(s.getStartAt()),f(s.getEndAt()),s.getMeetingPlace(),f(s.getCreatedAt()),f(s.getUpdatedAt()));} public static String f(Instant i){return i==null?null:DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(i.atZone(ZoneId.of("Asia/Seoul")));}}
