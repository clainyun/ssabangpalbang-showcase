package com.ssafy.ssabangpalbang.chat.repository;

import com.ssafy.ssabangpalbang.chat.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findBySenderIdAndClientMessageId(
            Long senderId,
            String clientMessageId
    );

    Optional<ChatMessage> findByIdAndStudyId(Long id, Long studyId);

    /**
     * cursor가 없으면 최신 메시지부터, 있으면 해당 id보다 작은(더 과거) 메시지를
     * id DESC로 조회한다. hasNext 판단을 위해 size보다 1 많은 limit을 넘겨 호출한다.
     */
    @Query(
            value = """
                    SELECT cm.id AS messageId,
                           cm.message_type AS messageType,
                           cm.content AS content,
                           cm.image_file_id AS imageFileId,
                           cm.sender_id AS senderId,
                           m.nickname AS senderNickname,
                           m.selected_character_id AS senderSelectedCharacterId,
                           cm.created_at AS createdAt,
                           cm.deleted_at AS deletedAt,
                           cm.edited_at AS editedAt
                    FROM chat_message cm
                    LEFT JOIN member m ON m.id = cm.sender_id
                    WHERE cm.study_id = :studyId
                      AND (:cursor IS NULL OR cm.id < :cursor)
                    ORDER BY cm.id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<ChatMessageHistoryRow> findHistoryPage(
            @Param("studyId") Long studyId,
            @Param("cursor") Long cursor,
            @Param("limit") int limit
    );

    /**
     * 본인이 보낸 메시지를 제외하고, lastReadAt 이후(한 번도 안 읽었으면 전체)
     * 생성된 메시지 수를 계산한다. SYSTEM 메시지(senderId=null)는 항상 포함한다.
     */
    @Query(
            value = """
                    SELECT count(*)
                    FROM chat_message cm
                    WHERE cm.study_id = :studyId
                      AND cm.sender_id IS DISTINCT FROM :memberId
                      AND cm.created_at > COALESCE(:lastReadAt, TIMESTAMPTZ '-infinity')
                    """,
            nativeQuery = true
    )
    long countUnreadExcludingSender(
            @Param("studyId") Long studyId,
            @Param("memberId") Long memberId,
            @Param("lastReadAt") Instant lastReadAt
    );
}
