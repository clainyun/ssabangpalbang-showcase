package com.ssafy.ssabangpalbang.home.weather;

import com.github.benmanes.caffeine.cache.Cache;
import com.ssafy.ssabangpalbang.home.dto.response.HomeResponse;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class HomeWeatherService {

    private final OpenMeteoWeatherClient weatherClient;
    private final HomeWeatherProperties properties;
    private final Cache<HomeWeatherCacheKey, HomeWeatherCacheEntry> cache;
    private final Clock clock;

    public HomeResponse.Weather getWeather(HomeWeatherLocation location) {
        Instant now = clock.instant();
        HomeWeatherCacheKey key = HomeWeatherCacheKey.from(location);
        HomeWeatherCacheEntry cached = cache.getIfPresent(key);

        if (cached != null && isFresh(cached, now)) {
            return toResponse(cached.data(), location, "FRESH");
        }

        try {
            OpenMeteoWeatherData data = weatherClient.fetch(
                    location.latitude(),
                    location.longitude()
            );
            cache.put(key, new HomeWeatherCacheEntry(data, now));
            return toResponse(data, location, "FRESH");
        } catch (RestClientException | IllegalStateException exception) {
            log.warn(
                    "홈 날씨 조회에 실패했습니다. locationBasis={}",
                    location.basis()
            );
            if (cached != null) {
                return toResponse(cached.data(), location, "STALE");
            }
            return HomeResponse.Weather.unavailable(
                    location.name(),
                    location.basis().name()
            );
        }
    }

    private boolean isFresh(
            HomeWeatherCacheEntry cached,
            Instant now
    ) {
        Duration age = Duration.between(cached.fetchedAt(), now);
        return !age.isNegative() && age.compareTo(properties.freshTtl()) <= 0;
    }

    private HomeResponse.Weather toResponse(
            OpenMeteoWeatherData data,
            HomeWeatherLocation location,
            String freshness
    ) {
        WeatherCondition condition = WeatherCondition.from(
                data.weatherCode(),
                data.day()
        );
        DustGrade fineDustGrade = DustGrade.forFineDust(
                data.fineDustValue()
        );

        return new HomeResponse.Weather(
                true,
                data.temperatureCelsius(),
                condition.code(),
                condition.text(),
                condition.iconKey(),
                data.rainProbability(),
                data.fineDustValue(),
                fineDustGrade == null ? null : fineDustGrade.name(),
                data.observedAt(),
                location.name(),
                location.basis().name(),
                "OPEN_METEO",
                freshness
        );
    }

    private enum DustGrade {
        GOOD,
        NORMAL,
        BAD,
        VERY_BAD;

        private static DustGrade forFineDust(Double value) {
            if (value == null) {
                return null;
            }
            if (value <= 30) {
                return GOOD;
            }
            if (value <= 80) {
                return NORMAL;
            }
            if (value <= 150) {
                return BAD;
            }
            return VERY_BAD;
        }

    }

    private record WeatherCondition(
            String code,
            String text,
            String iconKey
    ) {
        private static WeatherCondition from(int weatherCode, boolean day) {
            String suffix = day ? "day" : "night";
            return switch (weatherCode) {
                case 0 -> new WeatherCondition(
                        "CLEAR_SKY", "맑음", "clear_" + suffix);
                case 1, 2 -> new WeatherCondition(
                        "PARTLY_CLOUDY", "구름 조금",
                        "partly_cloudy_" + suffix);
                case 3 -> new WeatherCondition(
                        "CLOUDY", "흐림", "cloudy");
                case 45, 48 -> new WeatherCondition(
                        "FOG", "안개", "fog");
                case 51, 53, 55, 56, 57 -> new WeatherCondition(
                        "DRIZZLE", "이슬비", "drizzle");
                case 61, 63, 65, 66, 67 -> new WeatherCondition(
                        "RAIN", "비", "rain");
                case 71, 73, 75, 77 -> new WeatherCondition(
                        "SNOW", "눈", "snow");
                case 80, 81, 82 -> new WeatherCondition(
                        "RAIN_SHOWER", "소나기", "rain_shower");
                case 85, 86 -> new WeatherCondition(
                        "SNOW_SHOWER", "눈보라", "snow_shower");
                case 95, 96, 99 -> new WeatherCondition(
                        "THUNDERSTORM", "뇌우", "thunderstorm");
                default -> new WeatherCondition(
                        "UNKNOWN", "날씨 정보 없음", "unknown");
            };
        }
    }
}

record HomeWeatherCacheKey(
        BigDecimal latitude,
        BigDecimal longitude
) {
    static HomeWeatherCacheKey from(HomeWeatherLocation location) {
        return new HomeWeatherCacheKey(
                rounded(location.latitude()),
                rounded(location.longitude())
        );
    }

    private static BigDecimal rounded(double value) {
        return BigDecimal.valueOf(value)
                .setScale(3, RoundingMode.HALF_UP);
    }
}

record HomeWeatherCacheEntry(
        OpenMeteoWeatherData data,
        Instant fetchedAt
) {
}
