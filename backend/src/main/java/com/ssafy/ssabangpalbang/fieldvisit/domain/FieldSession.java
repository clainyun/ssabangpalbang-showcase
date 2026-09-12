package com.ssafy.ssabangpalbang.fieldvisit.domain;

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

import java.time.Instant;
import java.util.Objects;

/**
 * 스터디의 임장 세션이다.
 *
 * <p>세션 시작은 BE-014, 종료·강제종료는 BE-018 책임이다.
 * AI-002·BE-015는 상태를 읽고 검증하는 데에만 사용한다.</p>
 */
@Entity
@Table(name = "field_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false, unique = true)
    private Long studyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FieldSessionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "ended_by_id")
    private Long endedById;

    @Column(name = "end_reason", length = 30)
    private String endReason;

    public static FieldSession start(Long studyId, Instant startedAt) {
        FieldSession session = new FieldSession();
        session.studyId = Objects.requireNonNull(studyId);
        session.status = FieldSessionStatus.IN_PROGRESS;
        session.startedAt = Objects.requireNonNull(startedAt);
        return session;
    }

    public void endByAllParticipants(Instant endedAt) {
        this.status = FieldSessionStatus.ENDED;
        this.endedAt = Objects.requireNonNull(endedAt);
        this.endedById = null;
        this.endReason = "ALL_ENDED";
    }

    public void endByLeader(Instant endedAt, Long leaderMemberId) {
        this.status = FieldSessionStatus.ENDED;
        this.endedAt = Objects.requireNonNull(endedAt);
        this.endedById = Objects.requireNonNull(leaderMemberId);
        this.endReason = "LEADER_FORCED";
    }

    public void endByMajority(Instant endedAt) {
        this.status = FieldSessionStatus.ENDED;
        this.endedAt = Objects.requireNonNull(endedAt);
        this.endedById = null;
        this.endReason = "MAJORITY_FORCED";
    }
}
