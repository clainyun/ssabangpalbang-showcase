package com.ssafy.ssabangpalbang.chat.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPushAsyncConfigTest {

    @Test
    void 큐_포화_시_커밋된_채팅_푸시를_호출_스레드에서_계속_처리한다() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor)
                new ChatPushAsyncConfig().chatPushExecutor();

        try {
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void 채팅_푸시_전용_스레드_이름을_쓴다() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor)
                new ChatPushAsyncConfig().chatPushExecutor();

        try {
            assertThat(executor.getThreadNamePrefix()).isEqualTo("chat-push-");
        } finally {
            executor.shutdown();
        }
    }
}
