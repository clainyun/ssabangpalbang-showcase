package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitStartTimePolicy;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyApplicationStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;

import java.time.Instant;
import java.util.List;

public final class StudyAccessPolicy {

    private StudyAccessPolicy() {
    }

    public static boolean isReadOnly(StudyStatus status) {
        return status == StudyStatus.COMPLETED || status == StudyStatus.CANCELED;
    }

    public static boolean isLeader(Study study, Long memberId) {
        return study.getLeaderId().equals(memberId);
    }

    public static boolean isActiveMember(List<StudyMember> activeMembers, Long memberId) {
        return activeMembers.stream()
                .anyMatch(studyMember -> studyMember.getMemberId().equals(memberId));
    }

    public static boolean canKick(
            StudyStatus status,
            boolean fieldVisitStarted,
            boolean requesterIsLeader,
            Long requesterMemberId,
            Long targetMemberId
    ) {
        return requesterIsLeader && !fieldVisitStarted
                && !requesterMemberId.equals(targetMemberId)
                && status != StudyStatus.IN_PROGRESS
                && status != StudyStatus.COMPLETED
                && status != StudyStatus.CANCELED;
    }

    public static boolean canManageNotice(StudyStatus status, boolean isLeader) {
        return isLeader && !isReadOnly(status);
    }
    public static boolean canManageSchedule(StudyStatus status, boolean isLeader) {
        return isLeader && (status == StudyStatus.RECRUITING || status == StudyStatus.CLOSED);
    }

    public static boolean canLeave(
            StudyStatus status,
            boolean fieldVisitStarted,
            boolean isLeader
    ) {
        return !isLeader && !fieldVisitStarted
                && (status == StudyStatus.RECRUITING || status == StudyStatus.CLOSED);
    }

    public static boolean canUpdateDetails(StudyStatus status, boolean isLeader) {
        return isLeader && !isReadOnly(status);
    }

    public static boolean canApply(
            StudyStatus status,
            long currentMemberCount,
            int capacity,
            boolean isLeader,
            boolean isMember,
            String participationStatus
    ) {
        return status == StudyStatus.RECRUITING
                && currentMemberCount < capacity
                && !isLeader
                && !isMember
                && "NONE".equals(participationStatus);
    }
    public static boolean canCancel(StudyStatus status) { return status == StudyStatus.RECRUITING || status == StudyStatus.CLOSED; }

    public static boolean canApproveApplication(
            StudyApplicationStatus applicationStatus,
            StudyStatus studyStatus,
            long currentMemberCount,
            int capacity
    ) {
        return applicationStatus == StudyApplicationStatus.PENDING
                && studyStatus == StudyStatus.RECRUITING
                && currentMemberCount < capacity;
    }

    public static boolean canRejectApplication(
            StudyApplicationStatus applicationStatus,
            StudyStatus studyStatus
    ) {
        return applicationStatus == StudyApplicationStatus.PENDING
                && (studyStatus == StudyStatus.RECRUITING
                || studyStatus == StudyStatus.CLOSED);
    }

    public static boolean canStartFieldVisit(
            boolean isMember,
            StudyStatus status,
            boolean hasSchedule,
            Instant scheduleStartAt,
            Instant now,
            String fieldVisitStatus
    ) {
        if (!isMember
                || (status != StudyStatus.CLOSED && status != StudyStatus.IN_PROGRESS)
                || !hasSchedule
                || "ENDED".equals(fieldVisitStatus)
                || scheduleStartAt == null) {
            return false;
        }
        return FieldVisitStartTimePolicy.isStartAtReached(now, scheduleStartAt);
    }

    public static Permissions permissions(
            boolean isLeader,
            boolean isMember,
            StudyStatus status
    ) {
        boolean readOnly = isReadOnly(status);
        boolean canManage = isLeader && !readOnly;
        return new Permissions(
                canManage,
                canManage,
                canManage,
                canManage,
                isMember && status != StudyStatus.CANCELED,
                isMember && !readOnly
        );
    }

    public record Permissions(
            boolean canManageApplications,
            boolean canManageMembers,
            boolean canManageNotices,
            boolean canManageSchedule,
            boolean canUseChat,
            boolean canUseFieldVisit
    ) {
    }
}
