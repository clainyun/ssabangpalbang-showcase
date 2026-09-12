package com.ssafy.ssabangpalbang.apartment.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportResultJsonParser {

    private final ObjectMapper objectMapper;

    public ParsedReport parse(Long reportId, String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return ParsedReport.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            return new ParsedReport(
                    textOrNull(root.get("title")),
                    textOrNull(root.get("summary")),
                    stringList(root.get("analysisTags")),
                    booleanOrTrue(root.get("isAiGenerated"))
            );
        } catch (JsonProcessingException exception) {
            log.warn("아파트 리포트 결과 JSON을 읽지 못했습니다. reportId={}", reportId);
            return ParsedReport.empty();
        }
    }

    private String textOrNull(JsonNode node) {
        return node != null && node.isTextual() ? node.textValue() : null;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (value.isTextual()) {
                values.add(value.textValue());
            }
        });
        return List.copyOf(values);
    }

    private boolean booleanOrTrue(JsonNode node) {
        return node == null || !node.isBoolean() || node.booleanValue();
    }

    public record ParsedReport(
            String title,
            String summary,
            List<String> analysisTags,
            boolean aiGenerated
    ) {
        private static ParsedReport empty() {
            return new ParsedReport(null, null, List.of(), true);
        }
    }
}
