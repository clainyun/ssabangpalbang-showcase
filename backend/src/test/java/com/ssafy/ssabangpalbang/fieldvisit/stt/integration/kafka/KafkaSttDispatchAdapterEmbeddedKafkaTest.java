package com.ssafy.ssabangpalbang.fieldvisit.stt.integration.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchCommand;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(
        KafkaSttDispatchAdapterEmbeddedKafkaTest.TestConfiguration.class
)
@EmbeddedKafka(
        partitions = 1,
        topics = KafkaSttDispatchAdapterEmbeddedKafkaTest.REQUEST_TOPIC
)
class KafkaSttDispatchAdapterEmbeddedKafkaTest {

    static final String REQUEST_TOPIC = "field-visit.stt.request.v1";
    @Configuration
    static class TestConfiguration {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private DefaultKafkaProducerFactory<String, String> producerFactory;
    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProperties =
                KafkaTestUtils.producerProps(embeddedKafka);
        producerFactory = new DefaultKafkaProducerFactory<>(
                producerProperties,
                new StringSerializer(),
                new StringSerializer()
        );

        Map<String, Object> consumerProperties =
                KafkaTestUtils.consumerProps(
                        "spring-stt-contract-test",
                        "true",
                        embeddedKafka
                );
        consumer = new DefaultKafkaConsumerFactory<>(
                consumerProperties,
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, REQUEST_TOPIC);
    }

    @AfterEach
    void tearDown() {
        consumer.close();
        producerFactory.destroy();
    }

    @Test
    void 실제_Kafka에_type_header_없는_UTF8_JSON과_sttId_key를_발행한다()
            throws Exception {
        KafkaSttDispatchAdapter adapter = new KafkaSttDispatchAdapter(
                new KafkaTemplate<>(producerFactory),
                objectMapper,
                new SttKafkaProperties(
                        true,
                        REQUEST_TOPIC,
                        "field-visit.stt.result.v1",
                        "field-visit.stt.request.dlq.v1",
                        "field-visit.stt.result.dlt.v1",
                        "spring-stt-contract-test",
                        Duration.ofSeconds(10),
                        Duration.ofMillis(100),
                        2
                )
        );

        adapter.dispatch(new SttDispatchCommand(
                "stt-embedded",
                1,
                90L,
                "stt/audio.webm",
                "audio/webm",
                "ko-KR"
        ));

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(
                        consumer,
                        REQUEST_TOPIC,
                        Duration.ofSeconds(10)
                );
        assertThat(record.key()).isEqualTo("stt-embedded");
        assertThat(record.headers()).isEmpty();
        JsonNode payload = objectMapper.readTree(record.value());
        assertThat(payload.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(payload.path("sttId").asText())
                .isEqualTo("stt-embedded");
        assertThat(payload.path("attemptNo").asInt()).isEqualTo(1);
        assertThat(payload.path("audioFileId").asLong()).isEqualTo(90L);
        assertThat(payload.path("objectKey").asText())
                .isEqualTo("stt/audio.webm");
        assertThat(payload.path("contentType").asText())
                .isEqualTo("audio/webm");
        assertThat(payload.path("language").asText()).isEqualTo("ko-KR");
        assertThat(record.headers()
                .lastHeader("spring_json_header_types"))
                .isNull();
    }
}
