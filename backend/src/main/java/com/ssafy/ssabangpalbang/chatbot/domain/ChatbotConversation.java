package com.ssafy.ssabangpalbang.chatbot.domain;

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

@Entity
@Table(name = "chatbot_conversation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatbotConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "apartment_id", nullable = false)
    private Long apartmentId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 질문 저장 시 사용자 메시지 시각으로, 답변 완료 시 완료 시각으로 갱신한다.
     */
    public void touchLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public static ChatbotConversation start(Long memberId, Long apartmentId) {
        ChatbotConversation conversation = new ChatbotConversation();
        conversation.memberId = Objects.requireNonNull(memberId);
        conversation.apartmentId = Objects.requireNonNull(apartmentId);
        return conversation;
    }
}
