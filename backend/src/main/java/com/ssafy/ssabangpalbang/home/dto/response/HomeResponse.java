package com.ssafy.ssabangpalbang.home.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record HomeResponse(
        LocalDate today,
        long unreadNotificationCount,
        NextVisit nextVisit,
        Weather weather,
        WeatherAlerts weatherAlerts
) {

    public record NextVisit(
            boolean exists,
            Long dDay,
            Long studyId,
            String apartmentName,
            String meetingPlace,
            OffsetDateTime startAt,
            Long currentMemberCount,
            Integer capacity
    ) {
        public static NextVisit empty() {
            return new NextVisit(
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    public record Weather(
            boolean available,
            Double temperatureCelsius,
            String conditionCode,
            String conditionText,
            String iconKey,
            Integer rainProbability,
            Double fineDustValue,
            String fineDustGrade,
            OffsetDateTime observedAt,
            String locationName,
            String locationBasis,
            String source,
            String freshness
    ) {
        public static Weather unavailable(
                String locationName,
                String locationBasis
        ) {
            return new Weather(
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    locationName,
                    locationBasis,
                    null,
                    "UNAVAILABLE"
            );
        }
    }

    public record WeatherAlerts(
            boolean available,
            List<WeatherAlert> items,
            String source,
            String freshness
    ) {
        public WeatherAlerts {
            items = List.copyOf(items);
        }

        public static WeatherAlerts unavailable() {
            return new WeatherAlerts(
                    false,
                    List.of(),
                    "KMA",
                    "UNAVAILABLE"
            );
        }
    }

    public record WeatherAlert(
            String type,
            String level,
            String title,
            String areaCode,
            String areaName,
            OffsetDateTime issuedAt,
            OffsetDateTime effectiveAt
    ) {
    }
}
