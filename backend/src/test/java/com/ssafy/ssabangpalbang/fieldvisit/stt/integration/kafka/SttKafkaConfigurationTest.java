package com.ssafy.ssabangpalbang.fieldvisit.stt.integration.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SttKafkaConfigurationTest {

    @Test
    void poison_result_is_non_retryable_and_handler_recovers_it() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        SttKafkaProperties properties = new SttKafkaProperties(
                true,
                "stt-request",
                "stt-result",
                "stt-request-dlq",
                "stt-result-dlt",
                "stt-backend",
                Duration.ofSeconds(1),
                Duration.ofMillis(100),
                2
        );

        DefaultErrorHandler handler = new SttKafkaConfiguration()
                .sttResultErrorHandler(kafkaTemplate, properties);
        BinaryExceptionClassifier classifier = ReflectionTestUtils.invokeMethod(
                handler,
                "getClassifier"
        );

        assertThat(classifier).isNotNull();
        assertThat(classifier.classify(new IllegalArgumentException()))
                .isFalse();
        assertThat(classifier.classify(new IllegalStateException()))
                .isTrue();
        assertThat(handler.isAckAfterHandle()).isTrue();
        assertThat(properties.resultDltTopic()).isEqualTo("stt-result-dlt");
    }
}
