package com.ssafy.ssabangpalbang.chatbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 저장소에 {@code @EnableAsync}가 없어 여기서 처음 켠다.
 *
 * <p>전역 기본 Executor를 만들지 않고 챗봇 전용 Executor를 두어 영향 범위를 좁혔다.
 * 워커는 {@code @Async("chatbotAnswerExecutor")}로 이 풀만 쓴다.
 */
@Configuration
@EnableAsync
public class ChatbotAsyncConfig {

    public static final String EXECUTOR = "chatbotAnswerExecutor";

    @Bean(name = EXECUTOR)
    Executor chatbotAnswerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        // 대화당 동시 답변이 1건으로 제한되므로 큐가 길 필요가 없다.
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("chatbot-answer-");
        // AFTER_COMMIT 뒤 큐가 가득 차도 HTTP 응답을 500으로 바꾸지 않는다.
        // 버려진 작업은 DB의 PENDING 메시지를 복구 스케줄러가 다시 처리한다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        // 종료 시 진행 중인 답변을 마저 저장하게 한다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
