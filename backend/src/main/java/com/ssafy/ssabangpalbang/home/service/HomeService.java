package com.ssafy.ssabangpalbang.home.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.home.dto.response.HomeResponse;
import com.ssafy.ssabangpalbang.home.weather.HomeWeatherLocation;
import com.ssafy.ssabangpalbang.home.weather.HomeWeatherProperties;
import com.ssafy.ssabangpalbang.home.weather.HomeWeatherAlertService;
import com.ssafy.ssabangpalbang.home.weather.HomeWeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final HomeCoreService homeCoreService;
    private final HomeWeatherService homeWeatherService;
    private final HomeWeatherAlertService homeWeatherAlertService;
    private final HomeWeatherProperties weatherProperties;

    public HomeResponse getHome(
            Long memberId,
            String latitude,
            String longitude
    ) {
        Coordinates coordinates = Coordinates.parse(latitude, longitude);
        HomeCoreData core = homeCoreService.load(memberId);
        HomeWeatherLocation weatherLocation = selectWeatherLocation(
                coordinates,
                core.nextVisitWeatherLocation()
        );

        return new HomeResponse(
                core.today(),
                core.unreadNotificationCount(),
                core.nextVisit(),
                homeWeatherService.getWeather(weatherLocation),
                homeWeatherAlertService.getAlerts(weatherLocation)
        );
    }

    private HomeWeatherLocation selectWeatherLocation(
            Coordinates coordinates,
            HomeWeatherLocation nextVisitLocation
    ) {
        if (coordinates != null) {
            return new HomeWeatherLocation(
                    coordinates.latitude(),
                    coordinates.longitude(),
                    "현재 위치",
                    HomeWeatherLocation.Basis.CURRENT_LOCATION
            );
        }
        if (nextVisitLocation != null) {
            return nextVisitLocation;
        }
        return new HomeWeatherLocation(
                weatherProperties.defaultLatitude(),
                weatherProperties.defaultLongitude(),
                weatherProperties.defaultLocationName(),
                HomeWeatherLocation.Basis.DEFAULT_LOCATION
        );
    }

    private record Coordinates(double latitude, double longitude) {

        private static Coordinates parse(
                String latitude,
                String longitude
        ) {
            if (latitude == null && longitude == null) {
                return null;
            }
            if (latitude == null || longitude == null) {
                throw new BusinessException(
                        ErrorCode.HOME_LOCATION_INCOMPLETE,
                        Map.of(
                                "reason",
                                "위도와 경도는 함께 전달해야 합니다."
                        )
                );
            }
            return new Coordinates(
                    parseLatitude(latitude),
                    parseLongitude(longitude)
            );
        }

        private static double parseLatitude(String value) {
            double parsed = parseNumber(
                    value,
                    ErrorCode.HOME_LATITUDE_INVALID,
                    "latitude",
                    "위도는 -90 이상 90 이하이어야 합니다."
            );
            if (parsed < -90 || parsed > 90) {
                throw invalidCoordinate(
                        ErrorCode.HOME_LATITUDE_INVALID,
                        "latitude",
                        "위도는 -90 이상 90 이하이어야 합니다."
                );
            }
            return parsed;
        }

        private static double parseLongitude(String value) {
            double parsed = parseNumber(
                    value,
                    ErrorCode.HOME_LONGITUDE_INVALID,
                    "longitude",
                    "경도는 -180 이상 180 이하이어야 합니다."
            );
            if (parsed < -180 || parsed > 180) {
                throw invalidCoordinate(
                        ErrorCode.HOME_LONGITUDE_INVALID,
                        "longitude",
                        "경도는 -180 이상 180 이하이어야 합니다."
                );
            }
            return parsed;
        }

        private static double parseNumber(
                String value,
                ErrorCode errorCode,
                String field,
                String reason
        ) {
            try {
                double parsed = Double.parseDouble(value);
                if (!Double.isFinite(parsed)) {
                    throw invalidCoordinate(errorCode, field, reason);
                }
                return parsed;
            } catch (NumberFormatException exception) {
                throw invalidCoordinate(errorCode, field, reason);
            }
        }

        private static BusinessException invalidCoordinate(
                ErrorCode errorCode,
                String field,
                String reason
        ) {
            return new BusinessException(
                    errorCode,
                    Map.of("field", field, "reason", reason)
            );
        }
    }
}
