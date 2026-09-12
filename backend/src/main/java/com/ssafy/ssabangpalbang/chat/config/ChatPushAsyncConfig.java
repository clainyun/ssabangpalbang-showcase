package com.ssafy.ssabangpalbang.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class ChatPushAsyncConfig {

    public static final String EXECUTOR = "chatPushExecutor";

    @Bean(name = EXECUTOR)
    Executor chatPushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("chat-push-");
        // 커밋된 채팅 푸시를 조용히 버리지 않는다. 큐가 포화되면 이벤트를 게시한
        // 스레드에서 전송을 이어가 DB에 저장된 메시지와 알림 전달이 갈라지지 않게 한다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
