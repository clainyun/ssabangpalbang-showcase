package com.ssafy.ssabangpalbang.fieldvisit.stt.integration.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaSttDispatchAdapterTest {

    private static final String REQUEST_TOPIC =
            "field-visit.stt.request.v1";
    private final KafkaTemplate<String, String> kafkaTemplate = mock();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SttKafkaProperties properties = new SttKafkaProperties(
            true,
            REQUEST_TOPIC,
            "field-visit.stt.result.v1",
            "field-visit.stt.request.dlq.v1",
            "field-visit.stt.result.dlt.v1",
            "backend-stt-test",
            java.time.Duration.ofSeconds(1),
            java.time.Duration.ofMillis(100),
            2
    );
    private KafkaSttDispatchAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new KafkaSttDispatchAdapter(
                kafkaTemplate,
                objectMapper,
                properties
        );
    }

    @Test
    void v1_JSON을_sttId_key로_발행하고_발행_시각을_기록한다()
            throws Exception {
        CompletableFuture<SendResult<String, String>> published =
                CompletableFuture.completedFuture(mock());
        when(kafkaTemplate.send(
                org.mockito.ArgumentMatchers.eq(REQUEST_TOPIC),
                org.mockito.ArgumentMatchers.eq("stt-123"),
                anyString()
        )).thenReturn(published);

        adapter.dispatch(command());

        var payloadCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(
                org.mockito.ArgumentMatchers.eq(REQUEST_TOPIC),
                org.mockito.ArgumentMatchers.eq("stt-123"),
                payloadCaptor.capture()
        );
        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.fieldNames())
                .toIterable()
                .containsExactly(
                        "schemaVersion",
                        "sttId",
                        "attemptNo",
                        "audioFileId",
                        "objectKey",
                        "contentType",
                        "language"
                );
        assertThat(payload.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(payload.get("sttId").asText()).isEqualTo("stt-123");
        assertThat(payload.get("attemptNo").asInt()).isEqualTo(2);
        assertThat(payload.get("audioFileId").asLong()).isEqualTo(90L);
        assertThat(payload.get("objectKey").asText())
                .isEqualTo("stt/audio.webm");
        assertThat(payload.get("contentType").asText())
                .isEqualTo("audio/webm");
        assertThat(payload.get("language").asText()).isEqualTo("ko-KR");
    }

    @Test
    void broker_ack가_실패하면_발행_기록을_남기지_않는다() {
        CompletableFuture<SendResult<String, String>> failed =
                new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker"));
        when(kafkaTemplate.send(
                org.mockito.ArgumentMatchers.eq(REQUEST_TOPIC),
                org.mockito.ArgumentMatchers.eq("stt-123"),
                anyString()
        )).thenReturn(failed);

        assertThatThrownBy(() -> adapter.dispatch(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sttId=stt-123")
                .hasMessageNotContaining("stt/audio.webm");
    }

    @Test
    void sttId가_DB_계약보다_길면_발행하지_않는다() {
        SttDispatchCommand invalid = new SttDispatchCommand(
                "s".repeat(51),
                1,
                90L,
                "stt/audio.webm",
                "audio/webm",
                "ko-KR"
        );

        assertThatThrownBy(() -> adapter.dispatch(invalid))
                .isInstanceOf(IllegalArgumentException.class);
        verify(kafkaTemplate, never()).send(
                anyString(),
                anyString(),
                anyString()
        );
    }

    private SttDispatchCommand command() {
        return new SttDispatchCommand(
                "stt-123",
                2,
                90L,
                "stt/audio.webm",
                "audio/webm",
                "ko-KR"
        );
    }
}
