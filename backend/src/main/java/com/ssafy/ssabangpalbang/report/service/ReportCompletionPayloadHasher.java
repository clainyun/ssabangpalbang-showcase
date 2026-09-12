package com.ssafy.ssabangpalbang.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.ssabangpalbang.report.dto.request.ReportCompleteRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class ReportCompletionPayloadHasher {

    private final ObjectMapper objectMapper;

    String hash(ReportCompleteRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set(
                "generationResult",
                objectMapper.valueToTree(request.generationResult())
        );
        payload.set(
                "evidenceResult",
                objectMapper.valueToTree(request.evidenceResult())
        );
        try {
            return sha256(objectMapper.writeValueAsBytes(canonical(payload)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Report completion payload serialization failed",
                    exception
            );
        }
    }

    boolean matches(String actualHash, String expectedHash) {
        if (actualHash == null || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                actualHash.getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(
                    entry.getKey(),
                    entry.getValue()
            ));
            fields.forEach((name, value) -> sorted.set(name, canonical(value)));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            node.forEach(value -> array.add(canonical(value)));
            return array;
        }
        return node;
    }

    private String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }
}
