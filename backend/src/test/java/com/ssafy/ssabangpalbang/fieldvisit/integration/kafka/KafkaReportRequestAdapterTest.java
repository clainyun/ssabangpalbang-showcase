package com.ssafy.ssabangpalbang.fieldvisit.integration.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.ssabangpalbang.fieldvisit.integration.ReportRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaReportRequestAdapterTest {

    private static final String TOPIC = "field-visit.report.request.v1";
    private static final Instant OCCURRED_AT =
            Instant.parse("2026-08-01T09:15:30.123Z");

    private final KafkaTemplate<String, String> kafkaTemplate = mock();
    private final ObjectMapper objectMapper = createObjectMapper();
    private ReportKafkaProperties properties;
    private KafkaReportRequestAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new ReportKafkaProperties(
                true,
                TOPIC,
                1,
                (short) 1,
                604_800_000L,
                "delete",
                Duration.ofMillis(200)
        );
        adapter = new KafkaReportRequestAdapter(
                kafkaTemplate,
                objectMapper,
                properties
        );
    }

    @Test
    void topic과_studyId_key로_4필드_payload를_발행한다() throws Exception {
        when(kafkaTemplate.send(eq(TOPIC), eq("42"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock()));

        adapter.request(event());

        ArgumentCaptor<String> payloadCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(
                eq(TOPIC),
                eq("42"),
                payloadCaptor.capture()
        );

        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.isObject()).isTrue();
        assertThat(fieldNames(payload)).containsExactlyInAnyOrder(
                "studyId",
                "sessionId",
                "apartmentId",
                "occurredAt"
        );
        assertThat(payload.get("studyId").asLong()).isEqualTo(42L);
        assertThat(payload.get("sessionId").asLong()).isEqualTo(7L);
        assertThat(payload.get("apartmentId").asLong()).isEqualTo(100L);
        assertThat(payload.get("occurredAt").asText())
                .isEqualTo("2026-08-01T09:15:30.123Z");
        assertThat(payloadCaptor.getValue().trim()).startsWith("{");
    }

    @Test
    void 미완료_Future여도_request는_기다리지_않고_반환한다() {
        CompletableFuture<SendResult<String, String>> pending =
                new CompletableFuture<>();
        when(kafkaTemplate.send(eq(TOPIC), eq("42"), anyString()))
                .thenReturn(pending);

        long startedAt = System.nanoTime();
        adapter.request(event());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(elapsed).isLessThan(Duration.ofMillis(500));
        assertThat(pending).isNotDone();
    }

    @Test
    void 성공_로그를_한_번만_남기고_payload를_출력하지_않는다() throws Exception {
        ListAppender<ILoggingEvent> appender = attachLogAppender();
        when(kafkaTemplate.send(eq(TOPIC), eq("42"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock()));

        adapter.request(event());
        waitUntil(() -> countMessages(appender, "REPORT Kafka 발행 성공") >= 1);

        assertThat(countMessages(appender, "REPORT Kafka 발행 성공")).isEqualTo(1);
        assertThat(appender.list)
                .filteredOn(event -> event.getFormattedMessage()
                        .contains("REPORT Kafka 발행 성공"))
                .allSatisfy(event -> assertThat(event.getFormattedMessage())
                        .contains("topic=" + TOPIC)
                        .contains("key=42")
                        .doesNotContain("\"apartmentId\"")
                        .doesNotContain(OCCURRED_AT.toString()));
    }

    @Test
    void broker_ack_실패_로그를_한_번만_남기고_예외를_던지지_않는다()
            throws Exception {
        ListAppender<ILoggingEvent> appender = attachLogAppender();
        CompletableFuture<SendResult<String, String>> failed =
                new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker"));
        when(kafkaTemplate.send(eq(TOPIC), eq("42"), anyString()))
                .thenReturn(failed);

        adapter.request(event());
        waitUntil(() -> countMessages(appender, "REPORT Kafka 발행 실패") >= 1);
        Thread.sleep(100);

        assertThat(countMessages(appender, "REPORT Kafka 발행 실패")).isEqualTo(1);
        assertThat(countMessages(appender, "REPORT Kafka 발행 timeout")).isZero();
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.ERROR)
                .allSatisfy(event -> assertThat(event.getFormattedMessage())
                        .contains("studyId=42")
                        .contains("sessionId=7")
                        .doesNotContain("2026-08-01T09:15:30.123Z")
                        .doesNotContain("\"apartmentId\""));
    }

    @Test
    void timeout_로그를_한_번만_남기고_request는_기다리지_않는다()
            throws Exception {
        ListAppender<ILoggingEvent> appender = attachLogAppender();
        CompletableFuture<SendResult<String, String>> pending =
                new CompletableFuture<>();
        when(kafkaTemplate.send(eq(TOPIC), eq("42"), anyString()))
                .thenReturn(pending);

        long startedAt = System.nanoTime();
        adapter.request(event());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(elapsed).isLessThan(Duration.ofMillis(500));
        waitUntil(() -> countMessages(appender, "REPORT Kafka 발행 timeout") >= 1);
        Thread.sleep(150);

        assertThat(countMessages(appender, "REPORT Kafka 발행 timeout")).isEqualTo(1);
        assertThat(countMessages(appender, "REPORT Kafka 발행 실패")).isZero();
        assertThat(countMessages(appender, "REPORT Kafka 발행 성공")).isZero();
    }

    @Test
    void 필수_필드가_없으면_발행하지_않는다() {
        assertThatThrownBy(() -> adapter.request(
                new ReportRequestedEvent(null, 7L, 100L, OCCURRED_AT)
        )).isInstanceOf(IllegalArgumentException.class);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void send_호출이_즉시_예외를_던지면_RuntimeException으로_전파한다() {
        when(kafkaTemplate.send(eq(TOPIC), eq("42"), anyString()))
                .thenThrow(new IllegalStateException("send failed"));

        assertThatThrownBy(() -> adapter.request(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("send failed");
    }

    private ReportRequestedEvent event() {
        return new ReportRequestedEvent(42L, 7L, 100L, OCCURRED_AT);
    }

    private List<String> fieldNames(JsonNode payload) {
        List<String> names = new ArrayList<>();
        Iterator<String> iterator = payload.fieldNames();
        while (iterator.hasNext()) {
            names.add(iterator.next());
        }
        return names;
    }

    private ListAppender<ILoggingEvent> attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(
                KafkaReportRequestAdapter.class
        );
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private long countMessages(
            ListAppender<ILoggingEvent> appender,
            String fragment
    ) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains(fragment))
                .count();
    }

    private void waitUntil(Check check) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            if (check.ok()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("condition not met within timeout");
    }

    @FunctionalInterface
    private interface Check {
        boolean ok();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
