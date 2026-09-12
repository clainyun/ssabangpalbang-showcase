package com.ssafy.ssabangpalbang.fieldvisit.integration.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.fieldvisit.integration.ReportRequestPort;
import com.ssafy.ssabangpalbang.fieldvisit.integration.ReportRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ssabangpalbang.fieldvisit.report.kafka",
        name = "enabled",
        havingValue = "true"
)
public class KafkaReportRequestAdapter implements ReportRequestPort {

    private static final Logger log =
            LoggerFactory.getLogger(KafkaReportRequestAdapter.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ReportKafkaProperties properties;

    @Override
    public void request(ReportRequestedEvent event) {
        validate(event);
        String key = Long.toString(event.studyId());
        String payload = serialize(event);

        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(properties.topic(), key, payload);

        future.orTimeout(
                        properties.publishTimeout().toMillis(),
                        TimeUnit.MILLISECONDS
                )
                .whenComplete((result, exception) ->
                        handleCompletion(event, key, exception));
    }

    private void handleCompletion(
            ReportRequestedEvent event,
            String key,
            Throwable exception
    ) {
        if (exception == null) {
            log.info(
                    "REPORT Kafka 발행 성공. topic={}, key={}, studyId={}, sessionId={}",
                    properties.topic(),
                    key,
                    event.studyId(),
                    event.sessionId()
            );
            return;
        }

        Throwable cause = unwrap(exception);
        if (cause instanceof TimeoutException) {
            log.error(
                    "REPORT Kafka 발행 timeout. topic={}, key={}, studyId={}, sessionId={}",
                    properties.topic(),
                    key,
                    event.studyId(),
                    event.sessionId(),
                    cause
            );
            return;
        }

        log.error(
                "REPORT Kafka 발행 실패. topic={}, key={}, studyId={}, sessionId={}",
                properties.topic(),
                key,
                event.studyId(),
                event.sessionId(),
                cause
        );
    }

    private static Throwable unwrap(Throwable exception) {
        Throwable current = exception;
        while (current instanceof CompletionException
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String serialize(ReportRequestedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "REPORT Kafka 요청 직렬화에 실패했습니다. studyId="
                            + event.studyId()
                            + ", sessionId="
                            + event.sessionId(),
                    exception
            );
        }
    }

    private void validate(ReportRequestedEvent event) {
        if (event == null
                || event.studyId() == null
                || event.studyId() < 1
                || event.sessionId() == null
                || event.sessionId() < 1
                || event.apartmentId() == null
                || event.apartmentId() < 1
                || event.occurredAt() == null) {
            throw new IllegalArgumentException(
                    "REPORT Kafka 요청 필드가 올바르지 않습니다."
            );
        }
    }
}
