package com.ssafy.ssabangpalbang.chat.repository;

import java.time.Instant;

/**
 * 채팅 이력 조회용 네이티브 쿼리 결과 프로젝션이다.
 * SYSTEM 메시지는 senderId·senderNickname·senderSelectedCharacterId가 모두 null이다.
 */
public interface ChatMessageHistoryRow {
    Long getMessageId();
    String getMessageType();
    String getContent();
    Long getImageFileId();
    Long getSenderId();
    String getSenderNickname();
    String getSenderSelectedCharacterId();
    Instant getCreatedAt();
    /** 소프트 삭제 시각. null이면 정상 메시지다. */
    Instant getDeletedAt();
    /** 마지막 수정 시각. null이면 수정된 적 없다. */
    Instant getEditedAt();
}
