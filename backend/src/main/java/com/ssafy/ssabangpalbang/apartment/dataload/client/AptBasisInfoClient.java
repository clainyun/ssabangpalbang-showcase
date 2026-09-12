package com.ssafy.ssabangpalbang.apartment.dataload.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.ssabangpalbang.apartment.dataload.DataLoadProperties;
import com.ssafy.ssabangpalbang.apartment.dataload.dto.AptBasisInfo;
import com.ssafy.ssabangpalbang.apartment.dataload.dto.AptDetailInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("dataload")
public class AptBasisInfoClient {

    private static final Logger log = LoggerFactory.getLogger(AptBasisInfoClient.class);
    private static final String BASE_URL = "https://apis.data.go.kr/1613000/AptBasisInfoServiceV4/";

    private final RestTemplate restTemplate;
    private final String serviceKey;
    private final long delayMs;
    private final AtomicInteger basicCallCount = new AtomicInteger();
    private final AtomicInteger detailCallCount = new AtomicInteger();

    public AptBasisInfoClient(
            RestTemplate restTemplate,
            @Value("${public-data.service-key:}") String serviceKey,
            DataLoadProperties properties
    ) {
        this.restTemplate = restTemplate;
        this.serviceKey = serviceKey;
        this.delayMs = properties.getRequestDelayMs();
    }

    public Optional<AptBasisInfo> getBasic(String complexCode) {
        return item("getAphusBassInfoV4", complexCode, basicCallCount).map(node -> new AptBasisInfo(
                text(node, "doroJuso"), text(node, "kaptAddr"),
                text(node, "kaptdaCnt"), text(node, "kaptUsedate")
        ));
    }

    public Optional<AptDetailInfo> getDetail(String complexCode) {
        return item("getAphusDtlInfoV4", complexCode, detailCallCount).map(node -> new AptDetailInfo(
                text(node, "kaptdPcnt"), text(node, "kaptdPcntu")
        ));
    }

    public int getBasicCallCount() {
        return basicCallCount.get();
    }

    public int getDetailCallCount() {
        return detailCallCount.get();
    }

    private Optional<JsonNode> item(
            String endpoint,
            String complexCode,
            AtomicInteger counter
    ) {
        URI uri = AptListClient.PublicDataUri.build(
                UriComponentsBuilder.fromUriString(BASE_URL + endpoint)
                        .queryParam("kaptCode", complexCode),
                serviceKey
        );
        try {
            counter.incrementAndGet();
            JsonNode root = restTemplate.getForObject(uri, JsonNode.class);
            if (root == null) {
                return Optional.empty();
            }
            String resultCode = root.path("response").path("header").path("resultCode").asText();
            if (!resultCode.isBlank() && !"00".equals(resultCode) && !"000".equals(resultCode)) {
                return Optional.empty();
            }
            JsonNode item = root.path("response").path("body").path("item");
            return item.isObject() ? Optional.of(item) : Optional.empty();
        } catch (RestClientException exception) {
            log.warn("[dataload] 단지정보 조회 실패({}): {}", complexCode, exception.getMessage());
            return Optional.empty();
        } finally {
            delay();
        }
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private void delay() {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
