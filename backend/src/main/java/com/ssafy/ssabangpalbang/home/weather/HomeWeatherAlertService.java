package com.ssafy.ssabangpalbang.home.weather;

import com.github.benmanes.caffeine.cache.Cache;
import com.ssafy.ssabangpalbang.home.dto.response.HomeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
public class HomeWeatherAlertService {

    private final SeoulWeatherAlertAreaResolver areaResolver;
    private final KmaWeatherAlertClient alertClient;
    private final HomeWeatherProperties properties;
    private final Cache<HomeWeatherAlertCacheKey,
            HomeWeatherAlertCacheEntry> cache;
    private final Clock clock;

    public HomeWeatherAlertService(
            SeoulWeatherAlertAreaResolver areaResolver,
            KmaWeatherAlertClient alertClient,
            HomeWeatherProperties properties,
            Cache<HomeWeatherAlertCacheKey,
                    HomeWeatherAlertCacheEntry> cache,
            Clock clock
    ) {
        this.areaResolver = areaResolver;
        this.alertClient = alertClient;
        this.properties = properties;
        this.cache = cache;
        this.clock = clock;
    }

    public HomeResponse.WeatherAlerts getAlerts(
            HomeWeatherLocation location
    ) {
        Instant now = clock.instant();
        HomeWeatherAlertCacheKey key = HomeWeatherAlertCacheKey.from(location);
        HomeWeatherAlertCacheEntry cached = cache.getIfPresent(key);
        if (cached != null && isFresh(cached, now)) {
            return toResponse(cached.snapshot(), "FRESH");
        }

        try {
            SeoulWeatherAlertArea area = areaResolver.resolve(location)
                    .orElse(null);
            if (area == null) {
                return staleOrUnavailable(cached);
            }
            KmaWeatherAlertSnapshot snapshot = alertClient.fetch(area, now);
            cache.put(
                    key,
                    new HomeWeatherAlertCacheEntry(snapshot, now)
            );
            return toResponse(snapshot, "FRESH");
        } catch (RestClientException | IllegalStateException exception) {
            log.warn(
                    "기상청 공식 특보 조회에 실패했습니다. locationBasis={}",
                    location.basis()
            );
            return staleOrUnavailable(cached);
        }
    }

    private boolean isFresh(
            HomeWeatherAlertCacheEntry cached,
            Instant now
    ) {
        Duration age = Duration.between(cached.fetchedAt(), now);
        return !age.isNegative()
                && age.compareTo(properties.alertFreshTtl()) <= 0;
    }

    private HomeResponse.WeatherAlerts staleOrUnavailable(
            HomeWeatherAlertCacheEntry cached
    ) {
        if (cached != null) {
            return toResponse(cached.snapshot(), "STALE");
        }
        return HomeResponse.WeatherAlerts.unavailable();
    }

    private HomeResponse.WeatherAlerts toResponse(
            KmaWeatherAlertSnapshot snapshot,
            String freshness
    ) {
        return new HomeResponse.WeatherAlerts(
                true,
                snapshot.alerts().stream()
                        .map(alert -> new HomeResponse.WeatherAlert(
                                alert.type(),
                                alert.level(),
                                alert.title(),
                                alert.areaCode(),
                                alert.areaName(),
                                alert.issuedAt(),
                                alert.effectiveAt()
                        ))
                        .toList(),
                "KMA",
                freshness
        );
    }
}

record HomeWeatherAlertCacheKey(
        BigDecimal latitude,
        BigDecimal longitude
) {
    static HomeWeatherAlertCacheKey from(HomeWeatherLocation location) {
        return new HomeWeatherAlertCacheKey(
                rounded(location.latitude()),
                rounded(location.longitude())
        );
    }

    private static BigDecimal rounded(double value) {
        return BigDecimal.valueOf(value)
                .setScale(3, RoundingMode.HALF_UP);
    }
}

record HomeWeatherAlertCacheEntry(
        KmaWeatherAlertSnapshot snapshot,
        Instant fetchedAt
) {
}
