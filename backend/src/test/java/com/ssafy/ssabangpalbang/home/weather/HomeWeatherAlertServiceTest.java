package com.ssafy.ssabangpalbang.home.weather;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ssafy.ssabangpalbang.home.dto.response.HomeResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HomeWeatherAlertServiceTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-29T02:00:00Z"
    );
    private static final HomeWeatherLocation LOCATION =
            new HomeWeatherLocation(
                    37.5412,
                    127.0178,
                    "현재 위치",
                    HomeWeatherLocation.Basis.CURRENT_LOCATION
            );
    private static final SeoulWeatherAlertArea AREA =
            new SeoulWeatherAlertArea("L1100200", "서울동북권");

    @Test
    void 공식특보를_홈_응답으로_변환하고_신선한_캐시를_재사용한다() {
        SeoulWeatherAlertAreaResolver resolver = mock(
                SeoulWeatherAlertAreaResolver.class
        );
        KmaWeatherAlertClient client = mock(KmaWeatherAlertClient.class);
        when(resolver.resolve(LOCATION)).thenReturn(Optional.of(AREA));
        when(client.fetch(AREA, NOW)).thenReturn(snapshot());
        HomeWeatherAlertService service = service(
                resolver,
                client,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        HomeResponse.WeatherAlerts first = service.getAlerts(LOCATION);
        HomeResponse.WeatherAlerts cached = service.getAlerts(LOCATION);

        assertThat(first.available()).isTrue();
        assertThat(first.source()).isEqualTo("KMA");
        assertThat(first.freshness()).isEqualTo("FRESH");
        assertThat(first.items())
                .extracting(HomeResponse.WeatherAlert::title)
                .containsExactly("폭염주의보");
        assertThat(cached).isEqualTo(first);
        org.mockito.Mockito.verify(client).fetch(AREA, NOW);
    }

    @Test
    void 특보구역을_확인할_수_없으면_UNAVAILABLE을_반환한다() {
        SeoulWeatherAlertAreaResolver resolver = mock(
                SeoulWeatherAlertAreaResolver.class
        );
        KmaWeatherAlertClient client = mock(KmaWeatherAlertClient.class);
        when(resolver.resolve(LOCATION)).thenReturn(Optional.empty());
        HomeWeatherAlertService service = service(
                resolver,
                client,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        HomeResponse.WeatherAlerts result = service.getAlerts(LOCATION);

        assertThat(result.available()).isFalse();
        assertThat(result.items()).isEmpty();
        assertThat(result.source()).isEqualTo("KMA");
        assertThat(result.freshness()).isEqualTo("UNAVAILABLE");
    }

    private HomeWeatherAlertService service(
            SeoulWeatherAlertAreaResolver resolver,
            KmaWeatherAlertClient client,
            Clock clock
    ) {
        Cache<HomeWeatherAlertCacheKey, HomeWeatherAlertCacheEntry> cache =
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(30))
                        .build();
        return new HomeWeatherAlertService(
                resolver,
                client,
                new HomeWeatherProperties(),
                cache,
                clock
        );
    }

    private KmaWeatherAlertSnapshot snapshot() {
        OffsetDateTime issuedAt = OffsetDateTime.parse(
                "2026-07-29T10:00:00+09:00"
        );
        return new KmaWeatherAlertSnapshot(List.of(
                new KmaWeatherAlertData(
                        "HEAT_WAVE",
                        "ADVISORY",
                        "폭염주의보",
                        "L1100200",
                        "서울동북권",
                        issuedAt,
                        issuedAt,
                        1
                )
        ));
    }
}
