package com.ssafy.ssabangpalbang.fieldvisit.stt.integration.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "ssabangpalbang.fieldvisit.stt.kafka")
public record SttKafkaProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("field-visit.stt.request.v1") String requestTopic,
        @DefaultValue("field-visit.stt.result.v1") String resultTopic,
        @DefaultValue("field-visit.stt.request.dlq.v1") String requestDlqTopic,
        @DefaultValue("field-visit.stt.result.dlt.v1") String resultDltTopic,
        @DefaultValue("ssabangpalbang-backend-stt-v1") String consumerGroup,
        @DefaultValue("10s") Duration publishTimeout,
        @DefaultValue("1s") Duration resultRetryBackoff,
        @DefaultValue("2") long resultMaxRetries
) {

    public SttKafkaProperties {
        if (enabled) {
            requireText(requestTopic, "request-topic");
            requireText(resultTopic, "result-topic");
            requireText(requestDlqTopic, "request-dlq-topic");
            requireText(resultDltTopic, "result-dlt-topic");
            requireText(consumerGroup, "consumer-group");
            if (publishTimeout == null
                    || publishTimeout.isZero()
                    || publishTimeout.isNegative()) {
                throw new IllegalArgumentException(
                        "publish-timeout은 0보다 커야 합니다."
                );
            }
            if (requestTopic.equals(resultTopic)
                    || requestTopic.equals(requestDlqTopic)
                    || requestTopic.equals(resultDltTopic)
                    || resultTopic.equals(requestDlqTopic)
                    || resultTopic.equals(resultDltTopic)
                    || requestDlqTopic.equals(resultDltTopic)) {
                throw new IllegalArgumentException(
                        "STT Kafka topic 이름은 서로 달라야 합니다."
                );
            }
            if (resultRetryBackoff == null
                    || resultRetryBackoff.isNegative()
                    || resultMaxRetries < 0) {
                throw new IllegalArgumentException(
                        "STT result retry settings are invalid"
                );
            }
        }
    }

    private static void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    propertyName + "은 비어 있을 수 없습니다."
            );
        }
    }
}
