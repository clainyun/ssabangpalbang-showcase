package com.ssafy.ssabangpalbang.fieldvisit.integration.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "ssabangpalbang.fieldvisit.report.kafka")
public record ReportKafkaProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("field-visit.report.request.v1") String topic,
        @DefaultValue("1") int partitions,
        @DefaultValue("1") short replicationFactor,
        @DefaultValue("604800000") long retentionMs,
        @DefaultValue("delete") String cleanupPolicy,
        @DefaultValue("10s") Duration publishTimeout
) {

    public ReportKafkaProperties {
        if (enabled) {
            requireText(topic, "topic");
            requireText(cleanupPolicy, "cleanup-policy");
            if (partitions < 1) {
                throw new IllegalArgumentException("partitions는 1 이상이어야 합니다.");
            }
            if (replicationFactor < 1) {
                throw new IllegalArgumentException(
                        "replication-factor는 1 이상이어야 합니다."
                );
            }
            if (retentionMs < 1) {
                throw new IllegalArgumentException(
                        "retention-ms는 1 이상이어야 합니다."
                );
            }
            if (!"delete".equals(cleanupPolicy)) {
                throw new IllegalArgumentException(
                        "cleanup-policy는 delete만 허용됩니다."
                );
            }
            if (publishTimeout == null
                    || publishTimeout.isZero()
                    || publishTimeout.isNegative()) {
                throw new IllegalArgumentException(
                        "publish-timeout은 0보다 커야 합니다."
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
