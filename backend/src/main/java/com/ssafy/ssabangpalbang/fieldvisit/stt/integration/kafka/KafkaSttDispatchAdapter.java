package com.ssafy.ssabangpalbang.fieldvisit.stt.integration.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchCommand;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ssabangpalbang.fieldvisit.stt.kafka",
        name = "enabled",
        havingValue = "true"
)
public class KafkaSttDispatchAdapter implements SttDispatchPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SttKafkaProperties properties;

    @Override
    public void dispatch(SttDispatchCommand command) {
        validate(command);
        String payload = serialize(SttRequestMessage.from(command));

        try {
            kafkaTemplate.send(
                            properties.requestTopic(),
                            command.sttId(),
                            payload
                    )
                    .get(
                            properties.publishTimeout().toMillis(),
                            TimeUnit.MILLISECONDS
                    );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw publishFailure(command, exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw publishFailure(command, exception);
        }
    }

    private String serialize(SttRequestMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "STT Kafka 요청 직렬화에 실패했습니다. sttId="
                            + message.sttId()
                            + ", attemptNo="
                            + message.attemptNo(),
                    exception
            );
        }
    }

    private void validate(SttDispatchCommand command) {
        if (command == null
                || isBlank(command.sttId())
                || command.sttId().length() > 50
                || command.attemptNo() < 1
                || command.audioFileId() == null
                || command.audioFileId() < 1
                || isBlank(command.objectKey())
                || isBlank(command.contentType())
                || isBlank(command.language())) {
            throw new IllegalArgumentException(
                    "STT Kafka 요청 필드가 올바르지 않습니다."
            );
        }
    }

    private IllegalStateException publishFailure(
            SttDispatchCommand command,
            Exception cause
    ) {
        return new IllegalStateException(
                "STT Kafka 요청 발행에 실패했습니다. sttId="
                        + command.sttId()
                        + ", attemptNo="
                        + command.attemptNo(),
                cause
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
