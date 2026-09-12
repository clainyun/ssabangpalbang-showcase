package com.ssafy.ssabangpalbang.chatbot.repository;

import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotConversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatbotConversationRepository
        extends JpaRepository<ChatbotConversation, Long> {

    Optional<ChatbotConversation> findById(Long id);

    /**
     * 동시 답변 제한을 직렬화한다. 잠금 없이 existsInProgress 만 보면
     * 두 요청이 모두 false 를 읽어 답변이 2건 생성된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChatbotConversation c WHERE c.id = :id")
    Optional<ChatbotConversation> findByIdForUpdate(@Param("id") Long id);
}
