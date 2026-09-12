package com.ssafy.ssabangpalbang.home.weather;

import java.time.OffsetDateTime;

record OpenMeteoWeatherData(
        Double temperatureCelsius,
        int weatherCode,
        boolean day,
        Integer rainProbability,
        Double fineDustValue,
        OffsetDateTime observedAt
) {
}
