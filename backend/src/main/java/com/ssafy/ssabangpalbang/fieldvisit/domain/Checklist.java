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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * 참여자 개인별 임장 체크리스트다(AI-002).
 *
 * <p>{@code (session_id, member_id)} UNIQUE 제약으로 같은 임장 세션에서
 * 참여자당 한 건만 존재한다. 재생성·항목 추가·삭제 기능은 제공하지 않으므로
 * 이 Entity에는 상태 변경 메서드를 두지 않는다.</p>
 */
@Entity
@Table(name = "checklist")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Checklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "is_fallback", nullable = false)
    private boolean fallback;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    public static Checklist create(
            Long sessionId,
            Long memberId,
            boolean fallback
    ) {
        Checklist checklist = new Checklist();
        checklist.sessionId = Objects.requireNonNull(sessionId);
        checklist.memberId = Objects.requireNonNull(memberId);
        checklist.fallback = fallback;
        return checklist;
    }
}
