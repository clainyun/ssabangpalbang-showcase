package com.ssafy.ssabangpalbang.fieldvisit.integration.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ReportKafkaConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            ReportKafkaConfiguration.class,
                            KafkaReportRequestAdapter.class,
                            StubKafkaBeans.class
                    );

    @Test
    void enabled이면_topic_bean이_계약값으로_생성된다() {
        contextRunner
                .withPropertyValues(
                        "ssabangpalbang.fieldvisit.report.kafka.enabled=true",
                        "ssabangpalbang.fieldvisit.report.kafka.topic=field-visit.report.request.v1",
                        "ssabangpalbang.fieldvisit.report.kafka.partitions=1",
                        "ssabangpalbang.fieldvisit.report.kafka.replication-factor=1",
                        "ssabangpalbang.fieldvisit.report.kafka.retention-ms=604800000",
                        "ssabangpalbang.fieldvisit.report.kafka.cleanup-policy=delete"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(NewTopic.class);
                    NewTopic topic = context.getBean(NewTopic.class);
                    assertThat(topic.name())
                            .isEqualTo("field-visit.report.request.v1");
                    assertThat(topic.numPartitions()).isEqualTo(1);
                    assertThat(topic.replicationFactor()).isEqualTo((short) 1);
                    Map<String, String> configs = topic.configs();
                    assertThat(configs.get(TopicConfig.RETENTION_MS_CONFIG))
                            .isEqualTo("604800000");
                    assertThat(configs.get(TopicConfig.CLEANUP_POLICY_CONFIG))
                            .isEqualTo("delete");
                    assertThat(context)
                            .hasSingleBean(KafkaReportRequestAdapter.class);
                });
    }

    @Test
    void disabled이면_topic과_adapter_bean이_없다() {
        contextRunner
                .withPropertyValues(
                        "ssabangpalbang.fieldvisit.report.kafka.enabled=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(NewTopic.class);
                    assertThat(context)
                            .doesNotHaveBean(KafkaReportRequestAdapter.class);
                });
    }

    @Test
    void enabled_상태에서_잘못된_retention이면_properties_생성이_실패한다() {
        assertThatThrownBy(() -> new ReportKafkaProperties(
                true,
                "field-visit.report.request.v1",
                1,
                (short) 1,
                0L,
                "delete",
                Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Configuration
    static class StubKafkaBeans {

        @Bean
        KafkaTemplate<String, String> kafkaTemplate() {
            return mock();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
