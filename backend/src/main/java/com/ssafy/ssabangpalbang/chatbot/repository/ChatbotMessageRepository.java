package com.ssafy.ssabangpalbang.chatbot.repository;

import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;

import java.util.List;
import java.util.Optional;

public interface ChatbotMessageRepository
        extends Repository<ChatbotMessage, Long> {

    ChatbotMessage save(ChatbotMessage message);

    Optional<ChatbotMessage> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM ChatbotMessage m WHERE m.id = :id")
    Optional<ChatbotMessage> findByIdForUpdate(@Param("id") Long id);

    /**
     * 이벤트 워커와 복구 스케줄러 중 하나만 답변을 생성하게 한다.
     * 잠긴 행은 기다리지 않고 건너뛰어 scheduler thread 고갈을 막는다.
     */
    @Query(
            value = """
                    SELECT *
                    FROM chatbot_message
                    WHERE id = :id
                      AND role = 'ASSISTANT'
                      AND status = 'PENDING'
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    Optional<ChatbotMessage> findPendingAssistantByIdForUpdateSkipLocked(
            @Param("id") Long id
    );

    @Query("""
            SELECT m
            FROM ChatbotMessage m
            WHERE m.conversationId = :conversationId
              AND m.id < :assistantMessageId
              AND m.role = com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessageRole.USER
            ORDER BY m.id DESC
            """)
    List<ChatbotMessage> findLatestUserBefore(
            @Param("conversationId") Long conversationId,
            @Param("assistantMessageId") Long assistantMessageId,
            Pageable pageable
    );

    @Query("""
            SELECT m.id
            FROM ChatbotMessage m
            WHERE m.role = com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessageRole.ASSISTANT
              AND m.status = com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessageStatus.PENDING
              AND m.updatedAt <= :cutoff
            ORDER BY m.updatedAt, m.id
            """)
    List<Long> findStalePendingAssistantIds(
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );

    @Query("""
            SELECT m.id
            FROM ChatbotMessage m
            WHERE m.role = com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessageRole.ASSISTANT
              AND m.status = com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessageStatus.PROCESSING
              AND m.updatedAt <= :cutoff
            ORDER BY m.updatedAt, m.id
            """)
    List<Long> findStaleProcessingAssistantIds(
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );

    @Query("""
            SELECT m
            FROM ChatbotMessage m
            WHERE m.conversationId = :conversationId
              AND (:cursor IS NULL OR m.id > :cursor)
            ORDER BY m.id ASC
            """)
    List<ChatbotMessage> findPage(
            @Param("conversationId") Long conversationId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
            FROM ChatbotMessage m
            WHERE m.conversationId = :conversationId
              AND m.status IN ('PENDING', 'PROCESSING')
            """)
    boolean existsInProgress(
            @Param("conversationId") Long conversationId
    );
}
