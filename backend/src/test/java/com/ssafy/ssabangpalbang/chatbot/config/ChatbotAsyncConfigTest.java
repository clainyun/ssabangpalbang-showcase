package com.ssafy.ssabangpalbang.chatbot.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class ChatbotAsyncConfigTest {

    @Test
    void 큐_포화_시_요청을_실패시키지_않고_PENDING_복구에_맡긴다() {
        Executor configured = new ChatbotAsyncConfig().chatbotAnswerExecutor();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configured;

        try {
            assertThat(executor.getThreadPoolExecutor()
                    .getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.DiscardPolicy.class);
        } finally {
            executor.shutdown();
        }
    }
}
