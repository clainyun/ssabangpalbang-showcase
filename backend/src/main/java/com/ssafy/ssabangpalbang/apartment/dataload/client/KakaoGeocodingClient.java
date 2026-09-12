package com.ssafy.ssabangpalbang.apartment.dataload.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.ssabangpalbang.apartment.dataload.DataLoadProperties;
import com.ssafy.ssabangpalbang.apartment.dataload.dto.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("dataload")
public class KakaoGeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoGeocodingClient.class);
    private static final String URL = "https://dapi.kakao.com/v2/local/search/address.json";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final long delayMs;
    private final AtomicInteger callCount = new AtomicInteger();

    @Autowired
    public KakaoGeocodingClient(
            RestTemplate restTemplate,
            @Value("${kakao.rest-api-key:}") String apiKey,
            DataLoadProperties properties
    ) {
        this(restTemplate, apiKey, properties.getRequestDelayMs());
    }

    public KakaoGeocodingClient(RestTemplate restTemplate, String apiKey, long delayMs) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.delayMs = delayMs;
    }

    public Optional<Coordinate> find(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        URI uri = UriComponentsBuilder.fromUriString(URL)
                .queryParam("query", address)
                .build().encode().toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey);
        try {
            callCount.incrementAndGet();
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class
            );
            JsonNode documents = response.getBody() == null
                    ? null : response.getBody().path("documents");
            if (documents == null || !documents.isArray() || documents.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = documents.get(0);
            return Optional.of(new Coordinate(
                    Double.valueOf(first.path("x").asText()),
                    Double.valueOf(first.path("y").asText())
            ));
        } catch (RestClientException | NumberFormatException exception) {
            log.warn("[dataload] 좌표 조회 실패({}): {}", address, exception.getMessage());
            return Optional.empty();
        } finally {
            delay();
        }
    }

    public int getCallCount() {
        return callCount.get();
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
