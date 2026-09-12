package com.ssafy.ssabangpalbang.fieldvisit.stt.integration.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchCommand;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("stt-kafka-e2e")
class SttKafkaRoundTripE2eTest {

    private static final String REQUEST_TOPIC =
            "field-visit.stt.request.v1";
    private static final String RESULT_TOPIC =
            "field-visit.stt.result.v1";

    @Test
    void Spring이_발행한_요청을_FastAPI가_수신하고_결과를_반환한다()
            throws Exception {
        String bootstrapServers = System.getenv()
                .getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String sttId = "stt-e2e-"
                + UUID.randomUUID().toString().replace("-", "");
        ObjectMapper objectMapper = new ObjectMapper();

        var producerFactory =
                new DefaultKafkaProducerFactory<String, String>(Map.of(
                        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        bootstrapServers,
                        ProducerConfig.ACKS_CONFIG,
                        "all",
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                        StringSerializer.class,
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                        StringSerializer.class
                ));
        Map<String, Object> consumerProperties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG,
                "spring-stt-e2e-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "latest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        try (KafkaConsumer<String, String> resultConsumer =
                     new KafkaConsumer<>(consumerProperties)) {
            resultConsumer.subscribe(List.of(RESULT_TOPIC));
            waitForAssignment(resultConsumer);

            KafkaSttDispatchAdapter adapter = new KafkaSttDispatchAdapter(
                    new KafkaTemplate<>(producerFactory),
                    objectMapper,
                    new SttKafkaProperties(
                            true,
                            REQUEST_TOPIC,
                            RESULT_TOPIC,
                            "field-visit.stt.request.dlq.v1",
                            "field-visit.stt.result.dlt.v1",
                            "spring-stt-e2e",
                            Duration.ofSeconds(10),
                            Duration.ofSeconds(1),
                            2
                    )
            );
            adapter.dispatch(new SttDispatchCommand(
                    sttId,
                    1,
                    90L,
                    "e2e/fake-audio.m4a",
                    "audio/m4a",
                    "ko-KR"
            ));

            List<String> statuses = new ArrayList<>();
            String textContent = null;
            long deadline = System.nanoTime()
                    + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline
                    && !statuses.contains("DONE")) {
                ConsumerRecords<String, String> records =
                        resultConsumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (!sttId.equals(record.key())) {
                        continue;
                    }
                    JsonNode event = objectMapper.readTree(record.value());
                    statuses.add(event.path("status").asText());
                    if ("DONE".equals(event.path("status").asText())) {
                        textContent = event.path("textContent").asText();
                    }
                }
            }

            assertThat(statuses).containsExactly("PROCESSING", "DONE");
            assertThat(textContent).isNotBlank();
        } finally {
            producerFactory.destroy();
        }
    }

    private void waitForAssignment(KafkaConsumer<String, String> consumer) {
        long deadline = System.nanoTime()
                + Duration.ofSeconds(10).toNanos();
        while (consumer.assignment().isEmpty()
                && System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(250));
        }
        assertThat(consumer.assignment()).isNotEmpty();
    }
}
