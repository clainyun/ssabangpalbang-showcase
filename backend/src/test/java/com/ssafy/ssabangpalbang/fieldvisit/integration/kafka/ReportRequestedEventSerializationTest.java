package com.ssafy.ssabangpalbang.fieldvisit.integration.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.fieldvisit.integration.ReportRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class ReportRequestedEventSerializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 실제_Spring_ObjectMapper로_4필드_ISO8601_UTC_object를_직렬화한다()
            throws Exception {
        ReportRequestedEvent event = new ReportRequestedEvent(
                1L,
                2L,
                3L,
                Instant.parse("2026-08-01T00:00:00Z")
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode payload = objectMapper.readTree(json);

        assertThat(json.trim()).startsWith("{");
        assertThat(payload.isObject()).isTrue();
        assertThat(payload.isTextual()).isFalse();
        assertThat(fieldNames(payload)).containsExactlyInAnyOrder(
                "studyId",
                "sessionId",
                "apartmentId",
                "occurredAt"
        );
        assertThat(payload.size()).isEqualTo(4);
        assertThat(payload.get("occurredAt").asText())
                .isEqualTo("2026-08-01T00:00:00Z");
        assertThat(payload.get("occurredAt").isTextual()).isTrue();

        // Python consumer can parse once into an object (not a quoted JSON string).
        JsonNode reparsed = objectMapper.readTree(json);
        assertThat(reparsed.get("studyId").asLong()).isEqualTo(1L);
    }

    private List<String> fieldNames(JsonNode payload) {
        List<String> names = new ArrayList<>();
        Iterator<String> iterator = payload.fieldNames();
        while (iterator.hasNext()) {
            names.add(iterator.next());
        }
        return names;
    }
}
