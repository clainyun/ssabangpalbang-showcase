package com.ssafy.ssabangpalbang.home.weather;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

@Component
class KakaoRegionClient {

    private final RestClient restClient;
    private final HomeWeatherProperties properties;

    KakaoRegionClient(
            @Qualifier("homeWeatherRestClient") RestClient restClient,
            HomeWeatherProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    Optional<String> resolveSeoulDistrict(
            double latitude,
            double longitude
    ) {
        String apiKey = properties.kakaoRestApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "카카오 좌표 변환 API 설정이 없습니다."
            );
        }

        JsonNode response = restClient.get()
                .uri(uri(latitude, longitude))
                .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.path("documents").isArray()) {
            throw new IllegalStateException(
                    "카카오 좌표 변환 응답을 사용할 수 없습니다."
            );
        }

        for (JsonNode document : response.path("documents")) {
            if (!"B".equals(document.path("region_type").asText())) {
                continue;
            }
            String city = document.path("region_1depth_name").asText();
            String district = document.path("region_2depth_name").asText();
            if (city.startsWith("서울") && !district.isBlank()) {
                return Optional.of(district);
            }
        }
        return Optional.empty();
    }

    private URI uri(double latitude, double longitude) {
        return UriComponentsBuilder
                .fromUri(properties.kakaoLocalBaseUrl())
                .path("/v2/local/geo/coord2regioncode.json")
                .queryParam("x", longitude)
                .queryParam("y", latitude)
                .queryParam("input_coord", "WGS84")
                .build()
                .encode()
                .toUri();
    }
}
