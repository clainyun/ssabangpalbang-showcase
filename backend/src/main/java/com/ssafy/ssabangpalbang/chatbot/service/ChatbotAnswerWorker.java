package com.ssafy.ssabangpalbang.chatbot.service;

import com.ssafy.ssabangpalbang.chatbot.config.ChatbotAsyncConfig;
import com.ssafy.ssabangpalbang.chatbot.integration.ChatbotAnswerRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 202를 보낸 뒤 답변 생성을 시작하는 비동기 진입점이다.
 *
 * <p>AFTER_COMMIT으로 시작한다 — 커밋 전에 돌면 아직 보이지 않는 메시지를 조회한다.
 * 예외를 밖으로 던지지 않는다. 이미 응답을 보낸 뒤라 알릴 곳이 없고,
 * 사용자에게는 FAILED 메시지로 전달된다.
 *
 * <p>실제 저장은 {@link ChatbotAnswerWriter}가 한다. 트랜잭션이 프록시를 거치도록
 * <b>반드시 다른 빈이어야 한다</b>.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatbotAnswerWorker {

    private final ChatbotAnswerWriter writer;

    @Async(ChatbotAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnswerRequested(ChatbotAnswerRequestedEvent event) {
        try {
            writer.generate(event);
        } catch (RuntimeException exception) {
            log.error(
                    "챗봇 답변 생성에 실패했습니다. messageId={}",
                    event.assistantMessageId(),
                    exception
            );
            try {
                writer.markFailed(event.assistantMessageId());
            } catch (RuntimeException failure) {
                log.error(
                        "실패 기록마저 실패했습니다. messageId={}",
                        event.assistantMessageId(),
                        failure
                );
            }
        }
    }
}
