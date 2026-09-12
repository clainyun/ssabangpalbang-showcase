package com.ssafy.ssabangpalbang.chatbot.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "chatbot_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatbotMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatbotMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatbotMessageStatus status;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "basis_type", length = 20)
    private String basisType;

    @Column(name = "basis_label", length = 100)
    private String basisLabel;

    // jsonb 컬럼은 JdbcTypeCode 없이 쓰면 varchar 로 전송돼 INSERT 가 거절된다.
    // Report.resultJson · MemberPreference 와 같은 매핑이다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sources_json", columnDefinition = "jsonb")
    private String sourcesJson;

    @Column(name = "fail_reason", length = 500)
    private String failReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * V1 CHECK 제약: USER는 content NOT NULL + status COMPLETED 여야 한다.
     * basisType은 저장하지 않는다 — 응답에서 "NONE"으로 표시된다.
     */
    public static ChatbotMessage userQuestion(
            Long conversationId,
            String content,
            Instant completedAt
    ) {
        ChatbotMessage message = new ChatbotMessage();
        message.conversationId = Objects.requireNonNull(conversationId);
        message.role = ChatbotMessageRole.USER;
        message.status = ChatbotMessageStatus.COMPLETED;
        message.content = Objects.requireNonNull(content);
        message.completedAt = completedAt;
        return message;
    }

    /**
     * V1 CHECK 제약: ASSISTANT가 PENDING이면 content가 반드시 null이어야 한다.
     * 빈 문자열도 거절된다.
     */
    public static ChatbotMessage assistantPlaceholder(Long conversationId) {
        ChatbotMessage message = new ChatbotMessage();
        message.conversationId = Objects.requireNonNull(conversationId);
        message.role = ChatbotMessageRole.ASSISTANT;
        message.status = ChatbotMessageStatus.PENDING;
        message.content = null;
        return message;
    }

    public void markProcessing() {
        this.status = ChatbotMessageStatus.PROCESSING;
    }

    /** 비정상 종료로 남은 PROCESSING 작업을 다시 복구 대기 상태로 돌린다. */
    public void requeue() {
        if (!isProcessingAssistant()) {
            return;
        }
        resetForRetry();
    }

    /** AI 일시 장애로 재시도할 때 updated_at을 움직여 재시도 간격을 보장한다. */
    public void deferRetry(Instant attemptedAt) {
        if (!isProcessingAssistant()) {
            return;
        }
        resetForRetry();
        this.updatedAt = Objects.requireNonNull(attemptedAt);
    }

    private void resetForRetry() {
        this.status = ChatbotMessageStatus.PENDING;
        this.content = null;
        this.basisType = null;
        this.basisLabel = null;
        this.sourcesJson = null;
        this.failReason = null;
        this.completedAt = null;
    }

    /**
     * AI가 준 근거 판정을 그대로 저장한다. 백엔드는 basisType을 재판정하지 않는다.
     */
    public void complete(
            String content,
            String basisType,
            String basisLabel,
            String sourcesJson,
            Instant completedAt
    ) {
        this.status = ChatbotMessageStatus.COMPLETED;
        this.content = Objects.requireNonNull(content);
        this.basisType = basisType;
        this.basisLabel = basisLabel;
        this.sourcesJson = sourcesJson;
        this.failReason = null;
        this.completedAt = completedAt;
    }

    /** V1 CHECK 제약: FAILED면 failReason이 반드시 있어야 한다. */
    public void fail(String failReason, Instant completedAt) {
        this.status = ChatbotMessageStatus.FAILED;
        this.failReason = Objects.requireNonNull(failReason);
        this.completedAt = completedAt;
    }

    public boolean isAssistant() {
        return role == ChatbotMessageRole.ASSISTANT;
    }

    public boolean isPendingAssistant() {
        return isAssistant() && status == ChatbotMessageStatus.PENDING;
    }

    public boolean isProcessingAssistant() {
        return isAssistant() && status == ChatbotMessageStatus.PROCESSING;
    }

    public boolean isInProgressAssistant() {
        return isPendingAssistant() || isProcessingAssistant();
    }
}
