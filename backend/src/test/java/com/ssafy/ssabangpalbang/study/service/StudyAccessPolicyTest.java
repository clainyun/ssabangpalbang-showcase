package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyApplicationStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

class StudyAccessPolicyTest {

    @Test
    void completedAndCanceledStudiesAreReadOnly() {
        assertThat(StudyAccessPolicy.isReadOnly(StudyStatus.COMPLETED)).isTrue();
        assertThat(StudyAccessPolicy.isReadOnly(StudyStatus.CANCELED)).isTrue();
        assertThat(StudyAccessPolicy.isReadOnly(StudyStatus.RECRUITING)).isFalse();
        assertThat(StudyAccessPolicy.isReadOnly(StudyStatus.CLOSED)).isFalse();
        assertThat(StudyAccessPolicy.isReadOnly(StudyStatus.IN_PROGRESS)).isFalse();
    }

    @Test
    void identifiesLeaderByMemberId() {
        Study study = BeanUtils.instantiateClass(Study.class);
        ReflectionTestUtils.setField(study, "leaderId", 7L);

        assertThat(StudyAccessPolicy.isLeader(study, 7L)).isTrue();
        assertThat(StudyAccessPolicy.isLeader(study, 8L)).isFalse();
    }

    @Test
    void identifiesOnlyMembersInTheProvidedActiveMemberList() {
        StudyMember member = StudyMember.createLeader(10L, 7L);
        assertThat(StudyAccessPolicy.isActiveMember(List.of(member), 7L)).isTrue();
        assertThat(StudyAccessPolicy.isActiveMember(List.of(member), 8L)).isFalse();
    }

    @Test
    void kickPolicyCoversRequesterTargetAndStudyStatus() {
        assertThat(StudyAccessPolicy.canKick(StudyStatus.RECRUITING, false, true, 7L, 9L)).isTrue();
        assertThat(StudyAccessPolicy.canKick(StudyStatus.CLOSED, false, true, 7L, 9L)).isTrue();
        assertThat(StudyAccessPolicy.canKick(StudyStatus.IN_PROGRESS, false, true, 7L, 9L)).isFalse();
        assertThat(StudyAccessPolicy.canKick(StudyStatus.COMPLETED, false, true, 7L, 9L)).isFalse();
        assertThat(StudyAccessPolicy.canKick(StudyStatus.CANCELED, false, true, 7L, 9L)).isFalse();
        assertThat(StudyAccessPolicy.canKick(StudyStatus.RECRUITING, false, true, 7L, 7L)).isFalse();
        assertThat(StudyAccessPolicy.canKick(StudyStatus.RECRUITING, false, false, 9L, 7L)).isFalse();
        assertThat(StudyAccessPolicy.canKick(StudyStatus.RECRUITING, true, true, 7L, 9L)).isFalse();
    }

    @Test
    void managesNoticeOnlyWhenLeaderAndWritable() {
        assertThat(StudyAccessPolicy.canManageNotice(StudyStatus.RECRUITING, true)).isTrue();
        assertThat(StudyAccessPolicy.canManageNotice(StudyStatus.CLOSED, true)).isTrue();
        assertThat(StudyAccessPolicy.canManageNotice(StudyStatus.IN_PROGRESS, true)).isTrue();
        assertThat(StudyAccessPolicy.canManageNotice(StudyStatus.COMPLETED, true)).isFalse();
        assertThat(StudyAccessPolicy.canManageNotice(StudyStatus.CANCELED, true)).isFalse();
        assertThat(StudyAccessPolicy.canManageNotice(StudyStatus.RECRUITING, false)).isFalse();
    }

    @Test
    void managesScheduleOnlyBeforeFieldVisitStarts() {
        assertThat(StudyAccessPolicy.canManageSchedule(StudyStatus.RECRUITING, true)).isTrue();
        assertThat(StudyAccessPolicy.canManageSchedule(StudyStatus.CLOSED, true)).isTrue();
        assertThat(StudyAccessPolicy.canManageSchedule(StudyStatus.IN_PROGRESS, true)).isFalse();
        assertThat(StudyAccessPolicy.canManageSchedule(StudyStatus.COMPLETED, true)).isFalse();
        assertThat(StudyAccessPolicy.canManageSchedule(StudyStatus.CANCELED, true)).isFalse();
        assertThat(StudyAccessPolicy.canManageSchedule(StudyStatus.RECRUITING, false)).isFalse();
    }

    @Test
    void allowsApplicationOnlyWhenAllFiveConditionsPass() {
        assertThat(canApply(StudyStatus.RECRUITING, 5, 6, false, false, "NONE")).isTrue();
        assertThat(canApply(StudyStatus.CLOSED, 5, 6, false, false, "NONE")).isFalse();
        assertThat(canApply(StudyStatus.RECRUITING, 6, 6, false, false, "NONE")).isFalse();
        assertThat(canApply(StudyStatus.RECRUITING, 5, 6, true, false, "NONE")).isFalse();
        assertThat(canApply(StudyStatus.RECRUITING, 5, 6, false, true, "NONE")).isFalse();
        assertThat(canApply(StudyStatus.RECRUITING, 5, 6, false, false, "PENDING")).isFalse();
        assertThat(canApply(StudyStatus.RECRUITING, 5, 6, false, false, "APPROVED")).isFalse();
        assertThat(canApply(StudyStatus.RECRUITING, 5, 6, false, false, "REJECTED")).isFalse();
    }

    @Test
    void startsFieldVisitOnlyForEligibleMemberAndSchedule() {
        Instant now = Instant.parse("2026-06-17T09:00:00Z");
        Instant startAt = now.minusSeconds(60);
        assertThat(canStart(true, StudyStatus.CLOSED, true, startAt, now, "NOT_STARTED"))
                .isTrue();
        assertThat(canStart(false, StudyStatus.CLOSED, true, startAt, now, "NOT_STARTED"))
                .isFalse();
        assertThat(canStart(true, StudyStatus.RECRUITING, true, startAt, now, "NOT_STARTED"))
                .isFalse();
        assertThat(canStart(true, StudyStatus.CLOSED, false, null, now, "NOT_STARTED"))
                .isFalse();
        assertThat(canStart(true, StudyStatus.CLOSED, true, startAt, now, "ENDED"))
                .isFalse();
    }

    @Test
    void startsFieldVisitOnlyWhenScheduleStartAtIsReached() {
        Instant now = Instant.parse("2026-06-17T09:00:00Z");
        assertThat(canStart(
                true, StudyStatus.CLOSED, true, now.plusSeconds(1), now, "NOT_STARTED"
        )).isFalse();
        assertThat(canStart(
                true, StudyStatus.CLOSED, true, now, now, "NOT_STARTED"
        )).isTrue();
        assertThat(canStart(
                true, StudyStatus.CLOSED, true, now.minusSeconds(1), now, "NOT_STARTED"
        )).isTrue();
        assertThat(canStart(
                true, StudyStatus.IN_PROGRESS, true, now, now, "IN_PROGRESS"
        )).isTrue();
        assertThat(canStart(
                true, StudyStatus.CLOSED, true, now, now, "ENDED"
        )).isFalse();
        assertThat(canStart(
                false, StudyStatus.CLOSED, true, now, now, "NOT_STARTED"
        )).isFalse();
    }

    @Test
    void derivesApplicationDecisionPermissionsFromSharedRules() {
        assertThat(StudyAccessPolicy.canApproveApplication(
                StudyApplicationStatus.PENDING, StudyStatus.RECRUITING, 1, 2)).isTrue();
        assertThat(StudyAccessPolicy.canApproveApplication(
                StudyApplicationStatus.PENDING, StudyStatus.CLOSED, 1, 2)).isFalse();
        assertThat(StudyAccessPolicy.canApproveApplication(
                StudyApplicationStatus.PENDING, StudyStatus.RECRUITING, 2, 2)).isFalse();
        assertThat(StudyAccessPolicy.canApproveApplication(
                StudyApplicationStatus.APPROVED, StudyStatus.RECRUITING, 1, 2)).isFalse();
        assertThat(StudyAccessPolicy.canRejectApplication(
                StudyApplicationStatus.PENDING, StudyStatus.RECRUITING)).isTrue();
        assertThat(StudyAccessPolicy.canRejectApplication(
                StudyApplicationStatus.PENDING, StudyStatus.CLOSED)).isTrue();
        assertThat(StudyAccessPolicy.canRejectApplication(
                StudyApplicationStatus.PENDING, StudyStatus.IN_PROGRESS)).isFalse();
        assertThat(StudyAccessPolicy.canRejectApplication(
                StudyApplicationStatus.REJECTED, StudyStatus.CLOSED)).isFalse();
    }

    @Test
    void derivesPermissionsWithoutChangingExistingRules() {
        StudyAccessPolicy.Permissions leader =
                StudyAccessPolicy.permissions(true, true, StudyStatus.IN_PROGRESS);
        assertThat(leader.canManageApplications()).isTrue();
        assertThat(leader.canManageMembers()).isTrue();
        assertThat(leader.canManageNotices()).isTrue();
        assertThat(leader.canManageSchedule()).isTrue();
        assertThat(leader.canUseChat()).isTrue();
        assertThat(leader.canUseFieldVisit()).isTrue();

        StudyAccessPolicy.Permissions readOnlyLeader =
                StudyAccessPolicy.permissions(true, true, StudyStatus.COMPLETED);
        assertThat(readOnlyLeader.canManageApplications()).isFalse();
        assertThat(readOnlyLeader.canManageMembers()).isFalse();
        assertThat(readOnlyLeader.canManageNotices()).isFalse();
        assertThat(readOnlyLeader.canManageSchedule()).isFalse();
        assertThat(readOnlyLeader.canUseChat()).isTrue();
        assertThat(readOnlyLeader.canUseFieldVisit()).isFalse();

        StudyAccessPolicy.Permissions member =
                StudyAccessPolicy.permissions(false, true, StudyStatus.IN_PROGRESS);
        assertThat(member.canManageApplications()).isFalse();
        assertThat(member.canManageMembers()).isFalse();
        assertThat(member.canManageNotices()).isFalse();
        assertThat(member.canManageSchedule()).isFalse();
        assertThat(member.canUseChat()).isTrue();
        assertThat(member.canUseFieldVisit()).isTrue();

        StudyAccessPolicy.Permissions outsider =
                StudyAccessPolicy.permissions(false, false, StudyStatus.RECRUITING);
        assertThat(outsider).isEqualTo(
                new StudyAccessPolicy.Permissions(false, false, false, false, false, false));

        StudyAccessPolicy.Permissions canceledMember =
                StudyAccessPolicy.permissions(false, true, StudyStatus.CANCELED);
        assertThat(canceledMember.canUseChat()).isFalse();
        assertThat(canceledMember.canUseFieldVisit()).isFalse();
    }

    private boolean canApply(
            StudyStatus status,
            long count,
            int capacity,
            boolean isLeader,
            boolean isMember,
            String participationStatus
    ) {
        return StudyAccessPolicy.canApply(
                status, count, capacity, isLeader, isMember, participationStatus);
    }

    private boolean canStart(
            boolean isMember,
            StudyStatus status,
            boolean hasSchedule,
            Instant scheduleStartAt,
            Instant now,
            String fieldVisitStatus
    ) {
        return StudyAccessPolicy.canStartFieldVisit(
                isMember, status, hasSchedule, scheduleStartAt, now, fieldVisitStatus
        );
    }
}
