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
 * 체크리스트 항목별 완료 상태다.
 *
 * <p>AI-002는 조회용 매핑만 사용한다. BE-015가 upsert(생성·완료·해제)를 담당한다.</p>
 */
@Entity
@Table(name = "checklist_answer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checklist_item_id", nullable = false, unique = true)
    private Long checklistItemId;

    @Column(name = "is_completed", nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ChecklistAnswer create(
            Long checklistItemId,
            boolean completed,
            Instant now
    ) {
        ChecklistAnswer answer = new ChecklistAnswer();
        answer.checklistItemId = Objects.requireNonNull(checklistItemId);
        answer.applyCompletion(completed, now);
        return answer;
    }

    public void applyCompletion(boolean completed, Instant now) {
        Objects.requireNonNull(now);
        if (completed) {
            if (!this.completed) {
                this.completedAt = now;
            }
            this.completed = true;
        } else {
            this.completed = false;
            this.completedAt = null;
        }
        this.updatedAt = now;
    }
}
