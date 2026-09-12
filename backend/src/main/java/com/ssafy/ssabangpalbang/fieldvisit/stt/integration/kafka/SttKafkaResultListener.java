package com.ssafy.ssabangpalbang.fieldvisit.stt.integration.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttFailureResult;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttProcessingResult;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttResultHandler;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttSuccessResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ssabangpalbang.fieldvisit.stt.kafka",
        name = "enabled",
        havingValue = "true"
)
public class SttKafkaResultListener {

    private static final Logger log =
            LoggerFactory.getLogger(SttKafkaResultListener.class);
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final int MAX_STT_ID_LENGTH = 50;
    private static final int MAX_FAIL_CODE_LENGTH = 100;
    private static final int MAX_FAIL_REASON_LENGTH = 500;

    private final ObjectMapper objectMapper;
    private final SttResultHandler resultHandler;

    @KafkaListener(
            topics = "${ssabangpalbang.fieldvisit.stt.kafka.result-topic}",
            groupId = "${ssabangpalbang.fieldvisit.stt.kafka.consumer-group}"
    )
    public void receive(
            @Payload String payload,
            @Header(
                    name = KafkaHeaders.RECEIVED_KEY,
                    required = false
            ) String key,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        SttResultMessage message = deserialize(payload);
        validateCommon(key, message);

        SttResultHandler.HandlingOutcome outcome = switch (message.status()) {
            case PROCESSING -> handleProcessing(message);
            case DONE -> handleDone(message);
            case FAILED -> handleFailed(message);
        };

        log.info(
                "STT Kafka 결과를 수신했습니다. "
                        + "sttId={}, attemptNo={}, status={}, outcome={}, "
                        + "topic={}, partition={}, offset={}",
                message.sttId(),
                message.attemptNo(),
                message.status(),
                outcome,
                topic,
                partition,
                offset
        );
    }

    private SttResultHandler.HandlingOutcome handleProcessing(
            SttResultMessage message
    ) {
        require(message.startedAt() != null, "startedAt");
        requireAbsentForProcessing(message);
        return resultHandler.markProcessing(new SttProcessingResult(
                message.sttId(),
                message.attemptNo(),
                message.startedAt()
        ));
    }

    private SttResultHandler.HandlingOutcome handleDone(
            SttResultMessage message
    ) {
        requireText(message.textContent(), "textContent", Integer.MAX_VALUE);
        require(message.completedAt() != null, "completedAt");
        requireAbsentForDone(message);
        return resultHandler.handleSuccess(new SttSuccessResult(
                message.sttId(),
                message.attemptNo(),
                message.textContent(),
                message.completedAt()
        ));
    }

    private SttResultHandler.HandlingOutcome handleFailed(
            SttResultMessage message
    ) {
        requireText(
                message.failCode(),
                "failCode",
                MAX_FAIL_CODE_LENGTH
        );
        requireText(
                message.failReason(),
                "failReason",
                MAX_FAIL_REASON_LENGTH
        );
        require(message.retryable() != null, "retryable");
        require(message.failedAt() != null, "failedAt");
        requireAbsentForFailed(message);
        return resultHandler.handleFailure(new SttFailureResult(
                message.sttId(),
                message.attemptNo(),
                message.failCode(),
                message.failReason(),
                message.retryable(),
                message.failedAt()
        ));
    }

    private SttResultMessage deserialize(String payload) {
        try {
            return objectMapper.readerFor(SttResultMessage.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .without(
                            DeserializationFeature
                                    .ADJUST_DATES_TO_CONTEXT_TIME_ZONE
                    )
                    .readValue(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "STT Kafka 결과 JSON 형식이 올바르지 않습니다."
            );
        }
    }

    private void validateCommon(String key, SttResultMessage message) {
        require(message != null, "message");
        require(
                Integer.valueOf(CURRENT_SCHEMA_VERSION)
                        .equals(message.schemaVersion()),
                "schemaVersion"
        );
        requireText(message.sttId(), "sttId", MAX_STT_ID_LENGTH);
        require(
                message.attemptNo() != null && message.attemptNo() >= 1,
                "attemptNo"
        );
        require(message.status() != null, "status");
        require(message.sttId().equals(key), "Kafka key");
    }

    private void requireAbsentForProcessing(SttResultMessage message) {
        require(message.textContent() == null, "textContent");
        require(message.completedAt() == null, "completedAt");
        require(message.failCode() == null, "failCode");
        require(message.failReason() == null, "failReason");
        require(message.retryable() == null, "retryable");
        require(message.failedAt() == null, "failedAt");
    }

    private void requireAbsentForDone(SttResultMessage message) {
        require(message.startedAt() == null, "startedAt");
        require(message.failCode() == null, "failCode");
        require(message.failReason() == null, "failReason");
        require(message.retryable() == null, "retryable");
        require(message.failedAt() == null, "failedAt");
    }

    private void requireAbsentForFailed(SttResultMessage message) {
        require(message.startedAt() == null, "startedAt");
        require(message.textContent() == null, "textContent");
        require(message.completedAt() == null, "completedAt");
    }

    private void requireText(
            String value,
            String field,
            int maxLength
    ) {
        require(
                value != null
                        && !value.isBlank()
                        && value.length() <= maxLength,
                field
        );
    }

    private void require(boolean condition, String field) {
        if (!condition) {
            throw new IllegalArgumentException(
                    "STT Kafka 결과 필드가 올바르지 않습니다. field=" + field
            );
        }
    }
}
