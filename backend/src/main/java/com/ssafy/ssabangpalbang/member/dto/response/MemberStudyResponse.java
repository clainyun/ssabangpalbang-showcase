package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.study.domain.StudyMemberRole;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.MemberStudyRow;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record MemberStudyResponse(
        Long studyId,
        String title,
        String intro,
        String goal,
        StudyStatus status,
        StudyMemberRole role,
        ApartmentSummary apartment,
        ScheduleSummary nextSchedule,
        int unreadChatCount,
        int pendingReviewCount,
        boolean readOnly,
        boolean hasReturnableFieldVisit
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static MemberStudyResponse from(MemberStudyRow row) {
        StudyStatus status = StudyStatus.valueOf(row.getStatus());
        return new MemberStudyResponse(
                row.getStudyId(),
                row.getTitle(),
                row.getIntro(),
                row.getGoal(),
                status,
                StudyMemberRole.valueOf(row.getRole()),
                new ApartmentSummary(
                        row.getApartmentId(),
                        row.getApartmentName()
                ),
                scheduleFrom(row),
                Math.toIntExact(row.getUnreadChatCount()),
                Math.toIntExact(row.getPendingReviewCount()),
                status == StudyStatus.COMPLETED,
                Boolean.TRUE.equals(row.getHasReturnableFieldVisit())
        );
    }

    private static ScheduleSummary scheduleFrom(MemberStudyRow row) {
        if (row.getScheduleId() == null) {
            return null;
        }
        return new ScheduleSummary(
                row.getScheduleId(),
                OffsetDateTime.ofInstant(row.getStartAt(), SEOUL),
                row.getMeetingPlace()
        );
    }

    @Schema(name = "MemberStudyApartmentSummary")
    public record ApartmentSummary(
            Long apartmentId,
            String name
    ) {
    }

    @Schema(name = "MemberStudyScheduleSummary")
    public record ScheduleSummary(
            Long scheduleId,
            OffsetDateTime startAt,
            String meetingPlace
    ) {
    }
}
