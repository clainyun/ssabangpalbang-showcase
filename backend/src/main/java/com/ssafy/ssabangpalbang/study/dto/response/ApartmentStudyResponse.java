package com.ssafy.ssabangpalbang.study.dto.response;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.study.repository.RecruitingStudyRow;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.service.StudyAccessPolicy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public record ApartmentStudyResponse(
        Long studyId,
        String status,
        String title,
        String intro,
        String goal,
        String purpose,
        int currentMemberCount,
        Integer capacity,
        int remainingCapacity,
        ScheduleSummary schedule,
        LeaderSummary leader,
        String applicationStatus,
        boolean canApply,
        boolean isMember,
        boolean isLeader
) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static ApartmentStudyResponse from(
            RecruitingStudyRow row,
            Member leader,
            String applicationStatus,
            boolean isMember,
            Long memberId
    ) {
        boolean isLeader = row.getLeaderId().equals(memberId);
        return new ApartmentStudyResponse(
                row.getStudyId(),
                "RECRUITING",
                row.getTitle(),
                row.getIntro(),
                row.getGoal(),
                row.getPurpose(),
                row.getCurrentMemberCount(),
                row.getCapacity(),
                row.getRemainingCapacity(),
                ScheduleSummary.from(row),
                LeaderSummary.from(leader),
                applicationStatus,
                StudyAccessPolicy.canApply(
                        StudyStatus.RECRUITING,
                        row.getCurrentMemberCount(),
                        row.getCapacity(),
                        isLeader,
                        isMember,
                        applicationStatus
                ),
                isMember,
                isLeader
        );
    }

    public record ScheduleSummary(
            Long scheduleId,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String meetingPlace,
            long dDay
    ) {
        private static ScheduleSummary from(RecruitingStudyRow row) {
            if (row.getScheduleId() == null) {
                return null;
            }
            Instant startAt = row.getStartAt();
            LocalDate today = LocalDate.now(SEOUL);
            LocalDate target = startAt.atZone(SEOUL).toLocalDate();
            return new ScheduleSummary(
                    row.getScheduleId(),
                    startAt.atZone(SEOUL).toOffsetDateTime(),
                    row.getEndAt() == null ? null : row.getEndAt().atZone(SEOUL).toOffsetDateTime(),
                    row.getMeetingPlace(),
                    ChronoUnit.DAYS.between(today, target)
            );
        }
    }

    public record LeaderSummary(
            Long memberId,
            String nickname,
            String selectedCharacterId,
            String ageGroup
    ) {
        private static LeaderSummary from(Member leader) {
            return new LeaderSummary(
                    leader.getId(),
                    leader.getNickname(),
                    leader.getSelectedCharacterId(),
                    leader.isAgeGroupPublicAgreed() ? leader.getAgeGroup() : null
            );
        }
    }
}
