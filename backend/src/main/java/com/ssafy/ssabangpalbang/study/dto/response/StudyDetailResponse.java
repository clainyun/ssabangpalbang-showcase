package com.ssafy.ssabangpalbang.study.dto.response;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.study.domain.Schedule;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

public record StudyDetailResponse(
        Long studyId, String title, String intro, String goal, String purpose, String status,
        ApartmentSummary apartment, LeaderSummary leader, long currentMemberCount, Integer capacity,
        ScheduleSummary nextSchedule, List<MemberSummary> memberSummary,
        String myParticipationStatus, boolean isLeader, boolean isMember, boolean canApply,
        Long unreadChatCount, String fieldVisitStatus, Long fieldSessionId,
        ReportSummary report, boolean canStartFieldVisit,
        boolean readOnly, Permissions permissions
) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static StudyDetailResponse from(
            Study study, Apartment apartment, Member leader, Schedule schedule,
            List<StudyMember> studyMembers, Map<Long, Member> members,
            long currentMemberCount, String participationStatus,
            boolean isLeader, boolean isMember, Long unreadChatCount, String fieldVisitStatus,
            Long fieldSessionId, ReportSummary report,
            boolean canApply, boolean canStartFieldVisit, boolean readOnly,
            Permissions permissions
    ) {
        List<MemberSummary> summaries = isMember ? studyMembers.stream()
                .map(sm -> {
                    Member member = members.get(sm.getMemberId());
                    return new MemberSummary(member.getId(), member.getNickname(),
                            member.getSelectedCharacterId(), sm.getRole().name());
                }).toList() : null;
        return new StudyDetailResponse(
                study.getId(), study.getTitle(), study.getIntro(),
                isMember ? study.getGoal() : null,
                study.getPurpose().name(), study.getStatus().name(),
                new ApartmentSummary(apartment.getId(), apartment.getName(), apartment.getAddress()),
                new LeaderSummary(leader.getId(), leader.getNickname(), leader.getSelectedCharacterId()),
                currentMemberCount, study.getCapacity(), schedule == null ? null : ScheduleSummary.from(schedule),
                summaries, participationStatus, isLeader, isMember, canApply,
                isMember ? unreadChatCount : null, isMember ? fieldVisitStatus : null,
                isMember ? fieldSessionId : null, isMember ? report : null,
                canStartFieldVisit, readOnly, permissions
        );
    }

    public record ApartmentSummary(Long apartmentId, String name, String address) {}
    public record LeaderSummary(Long memberId, String nickname, String selectedCharacterId) {}
    public record ScheduleSummary(Long scheduleId, OffsetDateTime startAt,
                                  OffsetDateTime endAt, String meetingPlace) {
        static ScheduleSummary from(Schedule schedule) {
            return new ScheduleSummary(schedule.getId(),
                    OffsetDateTime.ofInstant(schedule.getStartAt(), SEOUL),
                    schedule.getEndAt() == null ? null : OffsetDateTime.ofInstant(schedule.getEndAt(), SEOUL),
                    schedule.getMeetingPlace());
        }
    }
    public record MemberSummary(Long memberId, String nickname,
                                String selectedCharacterId, String role) {}
    public record ReportSummary(Long reportId, String status) {}
    public record Permissions(boolean canManageApplications, boolean canManageMembers,
                              boolean canManageNotices, boolean canManageSchedule,
                              boolean canUseChat, boolean canUseFieldVisit) {}
}
