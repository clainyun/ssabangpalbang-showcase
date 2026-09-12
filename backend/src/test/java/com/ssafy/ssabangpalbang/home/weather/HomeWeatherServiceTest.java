package com.ssafy.ssabangpalbang.home.weather;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ssafy.ssabangpalbang.home.dto.response.HomeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HomeWeatherServiceTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-29T02:00:00Z"
    );
    private static final HomeWeatherLocation LOCATION =
            new HomeWeatherLocation(
                    37.5665,
                    126.9780,
                    "서울특별시 중구",
                    HomeWeatherLocation.Basis.DEFAULT_LOCATION
            );

    @Test
    void 날씨와_PM10을_홈_카드_응답으로_변환한다() {
        OpenMeteoWeatherClient client = mock(OpenMeteoWeatherClient.class);
        when(client.fetch(37.5665, 126.9780)).thenReturn(weatherData());
        HomeWeatherService service = service(client, Clock.fixed(
                NOW,
                ZoneOffset.UTC
        ));

        HomeResponse.Weather result = service.getWeather(LOCATION);

        assertThat(result.available()).isTrue();
        assertThat(result.conditionCode()).isEqualTo("PARTLY_CLOUDY");
        assertThat(result.iconKey()).isEqualTo("partly_cloudy_day");
        assertThat(result.fineDustGrade()).isEqualTo("NORMAL");
        assertThat(result.source()).isEqualTo("OPEN_METEO");
        assertThat(result.freshness()).isEqualTo("FRESH");
    }

    @Test
    void 최신_조회가_실패하면_캐시를_STALE로_반환한다() {
        OpenMeteoWeatherClient client = mock(OpenMeteoWeatherClient.class);
        when(client.fetch(37.5665, 126.9780))
                .thenReturn(weatherData())
                .thenThrow(new RestClientException("timeout"));
        Clock clock = mock(Clock.class);
        when(clock.instant())
                .thenReturn(NOW, NOW.plus(Duration.ofMinutes(20)));
        HomeWeatherService service = service(client, clock);

        HomeResponse.Weather fresh = service.getWeather(LOCATION);
        HomeResponse.Weather stale = service.getWeather(LOCATION);

        assertThat(fresh.freshness()).isEqualTo("FRESH");
        assertThat(stale.available()).isTrue();
        assertThat(stale.freshness()).isEqualTo("STALE");
    }

    @Test
    void 조회와_캐시가_모두_없으면_UNAVAILABLE을_반환한다() {
        OpenMeteoWeatherClient client = mock(OpenMeteoWeatherClient.class);
        when(client.fetch(37.5665, 126.9780))
                .thenThrow(new RestClientException("timeout"));
        HomeWeatherService service = service(client, Clock.fixed(
                NOW,
                ZoneOffset.UTC
        ));

        HomeResponse.Weather result = service.getWeather(LOCATION);

        assertThat(result.available()).isFalse();
        assertThat(result.freshness()).isEqualTo("UNAVAILABLE");
        assertThat(result.locationBasis()).isEqualTo("DEFAULT_LOCATION");
    }

    private HomeWeatherService service(
            OpenMeteoWeatherClient client,
            Clock clock
    ) {
        Cache<HomeWeatherCacheKey, HomeWeatherCacheEntry> cache =
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofHours(1))
                        .build();
        return new HomeWeatherService(
                client,
                properties(),
                cache,
                clock
        );
    }

    private OpenMeteoWeatherData weatherData() {
        return new OpenMeteoWeatherData(
                33.4,
                2,
                true,
                20,
                31.0,
                OffsetDateTime.parse("2026-07-29T11:00:00+09:00")
        );
    }

    private HomeWeatherProperties properties() {
        return new HomeWeatherProperties();
    }
}
