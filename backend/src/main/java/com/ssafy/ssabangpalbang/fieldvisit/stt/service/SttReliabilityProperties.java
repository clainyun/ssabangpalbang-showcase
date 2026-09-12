package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(
        prefix = "ssabangpalbang.fieldvisit.stt.reliability"
)
public record SttReliabilityProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("1s") Duration outboxPollInterval,
        @DefaultValue("50") int batchSize,
        @DefaultValue("10") int dispatchMaxAttempts,
        @DefaultValue("1s") Duration dispatchRetryInitialDelay,
        @DefaultValue("1m") Duration dispatchRetryMaxDelay,
        @DefaultValue("30s") Duration watchdogInterval,
        @DefaultValue("5m") Duration pendingTimeout,
        @DefaultValue("15m") Duration processingTimeout,
        @DefaultValue("10s") Duration cleanupPollInterval,
        @DefaultValue("10") int cleanupMaxAttempts,
        @DefaultValue("30s") Duration cleanupRetryInitialDelay,
        @DefaultValue("1h") Duration cleanupRetryMaxDelay
) {

    public SttReliabilityProperties {
        positive(outboxPollInterval, "outbox-poll-interval");
        positive(watchdogInterval, "watchdog-interval");
        positive(pendingTimeout, "pending-timeout");
        positive(processingTimeout, "processing-timeout");
        positive(cleanupPollInterval, "cleanup-poll-interval");
        positive(dispatchRetryInitialDelay, "dispatch-retry-initial-delay");
        positive(dispatchRetryMaxDelay, "dispatch-retry-max-delay");
        positive(cleanupRetryInitialDelay, "cleanup-retry-initial-delay");
        positive(cleanupRetryMaxDelay, "cleanup-retry-max-delay");
        if (batchSize < 1 || dispatchMaxAttempts < 1 || cleanupMaxAttempts < 1) {
            throw new IllegalArgumentException(
                    "STT reliability counts must be at least 1"
            );
        }
        if (dispatchRetryInitialDelay.compareTo(dispatchRetryMaxDelay) > 0
                || cleanupRetryInitialDelay.compareTo(cleanupRetryMaxDelay) > 0) {
            throw new IllegalArgumentException(
                    "STT reliability initial retry delay must not exceed max delay"
            );
        }
    }

    public Duration dispatchRetryDelay(int failedAttempts) {
        return backoff(
                dispatchRetryInitialDelay,
                dispatchRetryMaxDelay,
                failedAttempts
        );
    }

    public Duration cleanupRetryDelay(int failedAttempts) {
        return backoff(
                cleanupRetryInitialDelay,
                cleanupRetryMaxDelay,
                failedAttempts
        );
    }

    private static Duration backoff(
            Duration initial,
            Duration maximum,
            int failedAttempts
    ) {
        int exponent = Math.max(0, Math.min(failedAttempts - 1, 30));
        long multiplier = 1L << exponent;
        try {
            Duration delay = initial.multipliedBy(multiplier);
            return delay.compareTo(maximum) > 0 ? maximum : delay;
        } catch (ArithmeticException exception) {
            return maximum;
        }
    }

    private static void positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
