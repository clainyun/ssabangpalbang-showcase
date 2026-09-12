package com.ssafy.ssabangpalbang.fieldvisit.stt.integration.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableConfigurationProperties(SttKafkaProperties.class)
public class SttKafkaConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "ssabangpalbang.fieldvisit.stt.kafka",
            name = "enabled",
            havingValue = "true"
    )
    DefaultErrorHandler sttResultErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            SttKafkaProperties properties
    ) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) -> new TopicPartition(
                                properties.resultDltTopic(),
                                -1
                        )
                );
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(properties.publishTimeout());
        recoverer.setLogRecoveryRecord(false);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(
                        properties.resultRetryBackoff().toMillis(),
                        properties.resultMaxRetries()
                )
        );
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }
}
