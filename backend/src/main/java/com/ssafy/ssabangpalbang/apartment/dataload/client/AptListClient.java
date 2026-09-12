package com.ssafy.ssabangpalbang.apartment.dataload.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.ssabangpalbang.apartment.dataload.DataLoadProperties;
import com.ssafy.ssabangpalbang.apartment.dataload.dto.AptListItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("dataload")
public class AptListClient {

    private static final Logger log = LoggerFactory.getLogger(AptListClient.class);
    private static final String URL =
            "https://apis.data.go.kr/1613000/AptListService3/getSigunguAptList3";
    private static final int PAGE_SIZE = 1000;

    private final RestTemplate restTemplate;
    private final String serviceKey;
    private final long delayMs;
    private final AtomicInteger callCount = new AtomicInteger();
    private int lastTotalCount;

    @Autowired
    public AptListClient(
            RestTemplate restTemplate,
            @Value("${public-data.service-key:}") String serviceKey,
            DataLoadProperties properties
    ) {
        this(restTemplate, serviceKey, properties.getRequestDelayMs());
    }

    public AptListClient(RestTemplate restTemplate, String serviceKey, long delayMs) {
        this.restTemplate = restTemplate;
        this.serviceKey = serviceKey;
        this.delayMs = delayMs;
    }

    public List<AptListItem> findBySigunguCode(String sigunguCode) {
        List<AptListItem> result = new ArrayList<>();
        lastTotalCount = 0;
        int page = 1;
        while (true) {
            JsonNode body = get(uri(sigunguCode, page));
            if (body == null || isError(body)) {
                return List.of();
            }
            JsonNode responseBody = body.path("response").path("body");
            lastTotalCount = responseBody.path("totalCount").asInt(0);
            int before = result.size();
            JsonNode itemsNode = responseBody.path("items");
            JsonNode itemNode = itemsNode.isArray() ? itemsNode : itemsNode.path("item");
            if (itemNode.isArray()) {
                itemNode.forEach(node -> result.add(toItem(node)));
            } else if (itemNode.isObject()) {
                result.add(toItem(itemNode));
            }
            if (result.size() == before || result.size() >= lastTotalCount) {
                break;
            }
            page++;
        }
        return result;
    }

    public int getLastTotalCount() {
        return lastTotalCount;
    }

    public int getCallCount() {
        return callCount.get();
    }

    private JsonNode get(URI uri) {
        try {
            callCount.incrementAndGet();
            return restTemplate.getForObject(uri, JsonNode.class);
        } catch (RestClientException exception) {
            log.warn("[dataload] 단지목록 조회 실패: {}", exception.getMessage());
            return null;
        } finally {
            delay();
        }
    }

    private URI uri(String sigunguCode, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL)
                .queryParam("sigunguCode", sigunguCode)
                .queryParam("pageNo", page)
                .queryParam("numOfRows", PAGE_SIZE);
        return PublicDataUri.build(builder, serviceKey);
    }

    private boolean isError(JsonNode root) {
        String resultCode = root.path("response").path("header").path("resultCode").asText();
        boolean error = !resultCode.isBlank() && !"00".equals(resultCode) && !"000".equals(resultCode);
        if (error) {
            log.warn("[dataload] 단지목록 API 오류: {}", resultCode);
        }
        return error;
    }

    private AptListItem toItem(JsonNode node) {
        return new AptListItem(
                text(node, "kaptCode"), text(node, "kaptName"), text(node, "bjdCode"),
                text(node, "as2"), text(node, "as3")
        );
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

    static final class PublicDataUri {

        private PublicDataUri() {
        }

        static URI build(UriComponentsBuilder builder, String serviceKey) {
            if (serviceKey != null && serviceKey.matches(".*%[0-9A-Fa-f]{2}.*")) {
                String query = builder.build().getQuery();
                return URI.create(builder.replaceQuery(null).build().toUriString()
                        + "?serviceKey=" + serviceKey + "&" + query);
            }
            return builder.queryParam("serviceKey", serviceKey).build().encode().toUri();
        }
    }
}
