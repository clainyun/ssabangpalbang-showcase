package com.ssafy.ssabangpalbang.home.weather;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Slf4j
@Component
public class OpenMeteoWeatherClient {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final RestClient restClient;
    private final HomeWeatherProperties properties;

    public OpenMeteoWeatherClient(
            @Qualifier("homeWeatherRestClient") RestClient restClient,
            HomeWeatherProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    OpenMeteoWeatherData fetch(double latitude, double longitude) {
        JsonNode weatherRoot = get(forecastUri(latitude, longitude));
        JsonNode current = weatherRoot.path("current");
        if (current.isMissingNode()
                || !current.hasNonNull("temperature_2m")
                || !current.hasNonNull("weather_code")
                || !current.hasNonNull("time")) {
            throw new IllegalStateException(
                    "Open-Meteo 현재 날씨 응답 필드가 부족합니다."
            );
        }

        AirQuality airQuality = fetchAirQuality(latitude, longitude);
        return new OpenMeteoWeatherData(
                current.path("temperature_2m").doubleValue(),
                current.path("weather_code").intValue(),
                current.path("is_day").asInt(1) == 1,
                current.hasNonNull("precipitation_probability")
                        ? current.path("precipitation_probability").intValue()
                        : null,
                airQuality.fineDustValue(),
                parseObservedAt(current.path("time").textValue())
        );
    }

    private AirQuality fetchAirQuality(
            double latitude,
            double longitude
    ) {
        try {
            JsonNode current = get(airQualityUri(latitude, longitude))
                    .path("current");
            return new AirQuality(
                    nullableDouble(current, "pm10")
            );
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("홈 대기질 조회에 실패해 날씨 정보만 반환합니다.");
            return AirQuality.empty();
        }
    }

    private JsonNode get(URI uri) {
        JsonNode response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || response.path("error").asBoolean(false)) {
            throw new IllegalStateException(
                    "Open-Meteo 응답을 사용할 수 없습니다."
            );
        }
        return response;
    }

    private URI forecastUri(double latitude, double longitude) {
        return UriComponentsBuilder
                .fromUri(properties.forecastBaseUrl())
                .path("/v1/forecast")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam(
                        "current",
                        "temperature_2m,weather_code,is_day,"
                                + "precipitation_probability"
                )
                .queryParam("timezone", "Asia/Seoul")
                .queryParam("forecast_days", 1)
                .build()
                .encode()
                .toUri();
    }

    private URI airQualityUri(double latitude, double longitude) {
        return UriComponentsBuilder
                .fromUri(properties.airQualityBaseUrl())
                .path("/v1/air-quality")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("current", "pm10")
                .queryParam("timezone", "Asia/Seoul")
                .queryParam("forecast_days", 1)
                .build()
                .encode()
                .toUri();
    }

    private Double nullableDouble(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).doubleValue() : null;
    }

    private OffsetDateTime parseObservedAt(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Open-Meteo 관측 시각이 없습니다."
            );
        }
        return LocalDateTime.parse(value)
                .atZone(SEOUL_ZONE_ID)
                .toOffsetDateTime();
    }

    private record AirQuality(
            Double fineDustValue
    ) {
        private static AirQuality empty() {
            return new AirQuality(null);
        }
    }
}
