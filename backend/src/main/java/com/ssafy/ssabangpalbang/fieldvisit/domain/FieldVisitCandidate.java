package com.ssafy.ssabangpalbang.fieldvisit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 최초 임장 세션 시작 시점에 고정한 참여 후보다.
 *
 * <p>후보 명단은 세션 생성 시 한 번 저장하며, 이후 승인되거나 탈퇴한 회원 때문에
 * 스냅샷 자체를 변경하지 않는다. 실제 임장 참여 기록은 GPS 검증 후 별도의
 * {@link FieldParticipant}로 생성한다.</p>
 */
@Entity
@Table(name = "field_visit_candidate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldVisitCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static FieldVisitCandidate create(
            Long sessionId,
            Long memberId,
            Instant createdAt
    ) {
        FieldVisitCandidate candidate = new FieldVisitCandidate();
        candidate.sessionId = Objects.requireNonNull(sessionId);
        candidate.memberId = Objects.requireNonNull(memberId);
        candidate.createdAt = Objects.requireNonNull(createdAt);
        return candidate;
    }
}
