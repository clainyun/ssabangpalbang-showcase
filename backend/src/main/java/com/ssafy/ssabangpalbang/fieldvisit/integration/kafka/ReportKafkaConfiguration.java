package com.ssafy.ssabangpalbang.fieldvisit.integration.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableConfigurationProperties(ReportKafkaProperties.class)
public class ReportKafkaConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "ssabangpalbang.fieldvisit.report.kafka",
            name = "enabled",
            havingValue = "true"
    )
    NewTopic reportRequestTopic(ReportKafkaProperties properties) {
        return TopicBuilder.name(properties.topic())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .config(
                        TopicConfig.RETENTION_MS_CONFIG,
                        Long.toString(properties.retentionMs())
                )
                .config(
                        TopicConfig.CLEANUP_POLICY_CONFIG,
                        properties.cleanupPolicy()
                )
                .build();
    }
}
