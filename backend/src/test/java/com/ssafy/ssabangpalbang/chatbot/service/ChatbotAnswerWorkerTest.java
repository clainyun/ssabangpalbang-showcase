package com.ssafy.ssabangpalbang.chatbot.service;

import com.ssafy.ssabangpalbang.chatbot.integration.ChatbotAnswerRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 워커는 위임만 한다. 저장 로직은 {@link ChatbotAnswerWriterTest}가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ChatbotAnswerWorkerTest {

    private static final Long MESSAGE_ID = 54L;

    @Mock
    private ChatbotAnswerWriter writer;

    @InjectMocks
    private ChatbotAnswerWorker worker;

    private ChatbotAnswerRequestedEvent event() {
        return new ChatbotAnswerRequestedEvent(
                41L, MESSAGE_ID, 15L, "래미안 옥수 리버젠", "교통 어때요?"
        );
    }

    @Test
    void 정상이면_writer에_위임하고_실패를_기록하지_않는다() {
        worker.onAnswerRequested(event());

        verify(writer).generate(any());
        verify(writer, never()).markFailed(any());
    }

    @Test
    void 생성이_실패하면_예외를_삼키고_FAILED를_기록한다() {
        doThrow(new IllegalStateException("boom"))
                .when(writer).generate(any());

        worker.onAnswerRequested(event());

        verify(writer).markFailed(MESSAGE_ID);
    }

    @Test
    void 실패_기록마저_실패해도_예외가_밖으로_나가지_않는다() {
        doThrow(new IllegalStateException("boom"))
                .when(writer).generate(any());
        doThrow(new IllegalStateException("also boom"))
                .when(writer).markFailed(any());

        worker.onAnswerRequested(event());

        verify(writer).markFailed(MESSAGE_ID);
    }

    /**
     * 트랜잭션 경계가 다른 빈에 있어야 프록시를 거친다.
     * 같은 클래스로 되돌리면(자기 호출) 변경 감지가 사라져 메시지가 PENDING에 머문다.
     */
    @Test
    void 워커에는_트랜잭션_애노테이션이_없어야_한다() throws Exception {
        Method listener = ChatbotAnswerWorker.class.getDeclaredMethod(
                "onAnswerRequested", ChatbotAnswerRequestedEvent.class
        );

        assertThat(listener.isAnnotationPresent(TransactionalEventListener.class))
                .isTrue();
        assertThat(listener.isAnnotationPresent(Transactional.class))
                .as("리스너에 트랜잭션을 걸면 실패 기록까지 함께 롤백된다")
                .isFalse();
    }

    @Test
    void 저장_로직은_별도_빈에_있어야_한다() {
        assertThat(ChatbotAnswerWorker.class.getDeclaredMethods())
                .as("generate/markFailed 를 워커로 되돌리면 자기 호출이 된다")
                .noneMatch(method ->
                        method.getName().equals("generate")
                                || method.getName().equals("markFailed"));
    }
}
