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

@Entity
@Table(name = "checklist_generation_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistGenerationProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "attempt_id", nullable = false, length = 64)
    private String attemptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChecklistGenerationProgressStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChecklistGenerationStage stage;

    @Column(name = "progress_rate", nullable = false)
    private int progressRate;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
