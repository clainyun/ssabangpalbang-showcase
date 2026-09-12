package com.ssafy.ssabangpalbang.chatbot.integration;

/**
 * 질문 저장 트랜잭션이 커밋된 뒤 답변 생성을 시작하라는 신호다.
 *
 * <p>엔티티가 아니라 ID만 담는다. 워커는 별도 트랜잭션에서 다시 조회한다 —
 * 동기 트랜잭션의 영속성 컨텍스트가 이미 닫혀 있다.
 */
public record ChatbotAnswerRequestedEvent(
        Long conversationId,
        Long assistantMessageId,
        Long apartmentId,
        String apartmentName,
        String question
) {
}
