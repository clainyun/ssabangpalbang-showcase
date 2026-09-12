package com.ssafy.ssabangpalbang.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비밀번호 재설정 코드 이메일 발송을 요청 스레드/트랜잭션 밖에서 처리하기 위한 executor.
 * 발송을 비동기로 분리해 응답 시간을 균질화하고 계정 존재 여부에 대한 타이밍 사이드채널을 줄인다.
 */
@Configuration
@EnableAsync
public class AuthEmailAsyncConfig {

    public static final String EXECUTOR = "authEmailExecutor";

    @Bean(name = EXECUTOR)
    Executor authEmailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("auth-email-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.DiscardPolicy()
        );
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
