package com.ssafy.ssabangpalbang.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "ssabangpalbang.chatbot.recovery")
public record ChatbotAnswerRecoveryProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("30s") Duration pollInterval,
        @DefaultValue("20") int batchSize,
        @DefaultValue("2m") Duration pendingTimeout,
        @DefaultValue("3m") Duration processingTimeout
) {

    public ChatbotAnswerRecoveryProperties {
        positive(pollInterval, "poll-interval");
        positive(pendingTimeout, "pending-timeout");
        positive(processingTimeout, "processing-timeout");
        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "chatbot recovery batch-size must be at least 1"
            );
        }
    }

    private static void positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    "chatbot recovery " + name + " must be positive"
            );
        }
    }
}
