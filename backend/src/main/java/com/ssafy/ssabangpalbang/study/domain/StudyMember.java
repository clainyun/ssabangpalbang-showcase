package com.ssafy.ssabangpalbang.study.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "study_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false)
    private Long studyId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyMemberStatus status;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "chat_push_enabled", nullable = false)
    private boolean chatPushEnabled = true;

    public static StudyMember createLeader(Long studyId, Long memberId) {
        StudyMember studyMember = new StudyMember();
        studyMember.studyId = studyId;
        studyMember.memberId = memberId;
        studyMember.role = StudyMemberRole.LEADER;
        studyMember.status = StudyMemberStatus.ACTIVE;
        return studyMember;
    }

    public static StudyMember createMember(Long studyId, Long memberId) {
        StudyMember studyMember = new StudyMember();
        studyMember.studyId = studyId;
        studyMember.memberId = memberId;
        studyMember.role = StudyMemberRole.MEMBER;
        studyMember.status = StudyMemberStatus.ACTIVE;
        return studyMember;
    }

    public void reactivate() {
        this.status = StudyMemberStatus.ACTIVE;
        this.leftAt = null;
        this.chatPushEnabled = true;
    }

    public void updateChatPushEnabled(boolean chatPushEnabled) {
        this.chatPushEnabled = chatPushEnabled;
    }

    public void kick(Instant now) {
        remove(now);
    }

    public void leave(Instant now) {
        remove(now);
    }

    public void remove(Instant now) {
        this.status = StudyMemberStatus.REMOVED;
        this.leftAt = now;
    }
}
