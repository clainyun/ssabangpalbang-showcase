package com.ssafy.ssabangpalbang.study.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 스터디 신청·일정 FCM 푸시를 요청 스레드에서 떼어낸다.
 *
 * <p>AFTER_COMMIT 리스너는 Spring 기본이 동기라, 커밋 뒤에도 톰캣 요청 스레드가
 * 블로킹 HTTPS 호출인 {@code FirebaseMessaging.send()}를 끝까지 기다렸다.
 * 승인 응답이 그만큼 늦어져 앱에서 "승인이 오래 걸린다"로 보였다.
 *
 * <p>{@code @EnableAsync}는 {@code ChatbotAsyncConfig}에도 있지만
 * 스터디 도메인이 챗봇 설정의 존재에 의존하지 않도록 여기서도 켠다.
 * 중복 선언은 {@code ProxyAsyncConfiguration}이 한 번만 등록되어 안전하다.
 */
@Configuration
@EnableAsync
public class StudyPushAsyncConfig {

    public static final String EXECUTOR = "studyPushExecutor";

    @Bean(name = EXECUTOR)
    Executor studyPushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("study-push-");
        // 큐가 포화돼도 커밋된 공지·일정 푸시를 조용히 버리지 않는다. AFTER_COMMIT
        // 단계이므로 호출 스레드에서 실행해도 비즈니스 데이터 커밋은 되돌리지 않는다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
