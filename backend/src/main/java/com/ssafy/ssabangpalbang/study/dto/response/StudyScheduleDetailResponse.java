package com.ssafy.ssabangpalbang.study.dto.response;
import com.ssafy.ssabangpalbang.study.domain.*;
public record StudyScheduleDetailResponse(Long studyId,String studyTitle,String studyStatus,boolean isLeader,boolean canManageSchedule,ScheduleItem schedule){
 public static StudyScheduleDetailResponse from(Study study, boolean leader, boolean canManage, Schedule s){ ScheduleItem item=s==null||s.getStatus()==ScheduleStatus.CANCELED?null:ScheduleItem.from(study,s);return new StudyScheduleDetailResponse(study.getId(),study.getTitle(),study.getStatus().name(),leader,canManage,item);}
 public record ScheduleItem(Long scheduleId,String status,String startAt,String endAt,String meetingPlace,CalendarEvent calendarEvent,String createdAt,String updatedAt){static ScheduleItem from(Study st,Schedule s){String status=st.getStatus()==StudyStatus.CANCELED?"CANCELED":s.getStatus().name();return new ScheduleItem(s.getId(),status,StudyScheduleCreateResponse.f(s.getStartAt()),StudyScheduleCreateResponse.f(s.getEndAt()),s.getMeetingPlace(),new CalendarEvent(st.getTitle(),StudyScheduleCreateResponse.f(s.getStartAt()),StudyScheduleCreateResponse.f(s.getEndAt()),s.getMeetingPlace()),StudyScheduleCreateResponse.f(s.getCreatedAt()),StudyScheduleCreateResponse.f(s.getUpdatedAt()));}}
 public record CalendarEvent(String title,String startAt,String endAt,String location){}
}
