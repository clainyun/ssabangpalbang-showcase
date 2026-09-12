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
 * 임장 세션의 개인 참여 기록이다.
 *
 * <p>참여자 생성(GPS 검증 후 개인 임장 시작)은 BE-014, 종료는 BE-018 책임이다.
 * AI-002·BE-015는 상태 조회에만 사용한다.</p>
 */
@Entity
@Table(name = "field_participant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FieldParticipantStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "end_reason", length = 30)
    private String endReason;

    @Column(name = "stay_duration_sec")
    private Integer stayDurationSec;

    public static FieldParticipant start(
            Long sessionId,
            Long memberId,
            Instant startedAt
    ) {
        FieldParticipant participant = new FieldParticipant();
        participant.sessionId = Objects.requireNonNull(sessionId);
        participant.memberId = Objects.requireNonNull(memberId);
        participant.status = FieldParticipantStatus.IN_PROGRESS;
        participant.startedAt = Objects.requireNonNull(startedAt);
        return participant;
    }

    public void endBySelf(Instant endedAt, int stayDurationSec) {
        end(endedAt, stayDurationSec, "SELF_ENDED");
    }

    public void endByLeader(Instant endedAt, int stayDurationSec) {
        end(endedAt, stayDurationSec, "LEADER_FORCED");
    }

    public void endByMajority(Instant endedAt, int stayDurationSec) {
        end(endedAt, stayDurationSec, "MAJORITY_FORCED");
    }

    /**
     * 본인이 직접 종료(SELF_ENDED)한 참여를 다시 진행 중으로 되돌린다.
     *
     * <p>세션이 IN_PROGRESS이고 endReason이 SELF_ENDED일 때에만 호출해야 한다.
     * 상태·종료 시각·종료 사유·체류 시간을 시작 직후 상태로 초기화한다.</p>
     */
    public void reopenFromSelfEnded() {
        if (this.status != FieldParticipantStatus.ENDED
                || !"SELF_ENDED".equals(this.endReason)) {
            throw new IllegalStateException(
                    "본인 종료 상태가 아닌 참여는 다시 진행 중으로 되돌릴 수 없습니다."
            );
        }
        this.status = FieldParticipantStatus.IN_PROGRESS;
        this.endedAt = null;
        this.endReason = null;
        this.stayDurationSec = null;
    }

    private void end(Instant endedAt, int stayDurationSec, String endReason) {
        if (stayDurationSec < 0) {
            throw new IllegalArgumentException("stayDurationSec must not be negative");
        }
        this.status = FieldParticipantStatus.ENDED;
        this.endedAt = Objects.requireNonNull(endedAt);
        this.endReason = endReason;
        this.stayDurationSec = stayDurationSec;
    }
}
