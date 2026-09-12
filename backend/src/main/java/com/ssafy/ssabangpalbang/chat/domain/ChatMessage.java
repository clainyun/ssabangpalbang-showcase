package com.ssafy.ssabangpalbang.chat.domain;

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

/**
 * 스터디 채팅 메시지다.
 *
 * <p>{@code clientMessageId}는 클라이언트 재전송 시 중복 저장을 막기 위한 멱등 키다.
 * 유일성 범위는 (senderId, clientMessageId)이며, {@code SYSTEM} 메시지처럼
 * senderId가 없거나 clientMessageId를 사용하지 않는 저장은 이 제약의 대상이 아니다.</p>
 */
@Entity
@Table(name = "chat_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false)
    private Long studyId;

    /** SYSTEM 메시지는 발신자가 없다(null). */
    @Column(name = "sender_id")
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType messageType;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "image_file_id")
    private Long imageFileId;

    @Column(name = "client_message_id", length = 100)
    private String clientMessageId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 소프트 삭제 시각. null이면 정상 메시지다. 행은 지우지 않는다(커서 안정성). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** 마지막 수정 시각. null이면 수정된 적 없다. */
    @Column(name = "edited_at")
    private Instant editedAt;

    public static ChatMessage createText(
            Long studyId,
            Long senderId,
            String content,
            String clientMessageId
    ) {
        ChatMessage message = new ChatMessage();
        message.studyId = studyId;
        message.senderId = senderId;
        message.messageType = MessageType.TEXT;
        message.content = content;
        message.clientMessageId = clientMessageId;
        return message;
    }

    public static ChatMessage createImage(
            Long studyId,
            Long senderId,
            Long imageFileId,
            String clientMessageId
    ) {
        ChatMessage message = new ChatMessage();
        message.studyId = studyId;
        message.senderId = senderId;
        message.messageType = MessageType.IMAGE;
        message.imageFileId = imageFileId;
        message.clientMessageId = clientMessageId;
        return message;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 이미 삭제된 메시지에 다시 호출해도 최초 삭제 시각을 유지한다(멱등). */
    public void markDeleted(Instant now) {
        if (deletedAt == null) {
            deletedAt = now;
        }
    }

    /** TEXT 본문을 제자리에서 바꾼다. 유형·권한·삭제 여부 검사는 서비스가 먼저 한다. */
    public void editContent(String newContent, Instant now) {
        content = newContent;
        editedAt = now;
    }

    /** 서버 내부 이벤트(일정 변경·멤버 변경 등)로만 호출하는 SYSTEM 메시지 생성이다. */
    public static ChatMessage createSystem(Long studyId, String content) {
        ChatMessage message = new ChatMessage();
        message.studyId = studyId;
        message.senderId = null;
        message.messageType = MessageType.SYSTEM;
        message.content = content;
        message.clientMessageId = null;
        return message;
    }
}
