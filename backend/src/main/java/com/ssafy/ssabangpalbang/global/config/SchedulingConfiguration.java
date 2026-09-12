package com.ssafy.ssabangpalbang.global.config;

import com.ssafy.ssabangpalbang.chatbot.config.ChatbotAnswerRecoveryProperties;
import com.ssafy.ssabangpalbang.study.service.ScheduleReminderProperties;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttReliabilityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({
        ScheduleReminderProperties.class,
        SttReliabilityProperties.class,
        ChatbotAnswerRecoveryProperties.class
})
public class SchedulingConfiguration {
}
