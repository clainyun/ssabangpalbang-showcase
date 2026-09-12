package com.ssafy.ssabangpalbang.study.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class StudyPushAsyncConfigTest {

    @Test
    void 큐_포화_시_커밋된_푸시를_호출_스레드에서_계속_처리한다() {
        Executor configured = new StudyPushAsyncConfig().studyPushExecutor();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configured;

        try {
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void 푸시_전용_스레드_이름을_쓴다() {
        Executor configured = new StudyPushAsyncConfig().studyPushExecutor();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configured;

        try {
            assertThat(executor.getThreadNamePrefix()).isEqualTo("study-push-");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void 스터디_도메인이_챗봇_설정에_의존하지_않는다() {
        assertThat(StudyPushAsyncConfig.class.isAnnotationPresent(EnableAsync.class))
                .isTrue();
    }
}
