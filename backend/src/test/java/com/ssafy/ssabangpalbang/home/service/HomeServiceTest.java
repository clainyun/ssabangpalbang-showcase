package com.ssafy.ssabangpalbang.home.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.home.dto.response.HomeResponse;
import com.ssafy.ssabangpalbang.home.weather.HomeWeatherLocation;
import com.ssafy.ssabangpalbang.home.weather.HomeWeatherProperties;
import com.ssafy.ssabangpalbang.home.weather.HomeWeatherAlertService;
import com.ssafy.ssabangpalbang.home.weather.HomeWeatherService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeServiceTest {

    private final HomeCoreService homeCoreService = mock(HomeCoreService.class);
    private final HomeWeatherService homeWeatherService = mock(
            HomeWeatherService.class
    );
    private final HomeWeatherAlertService homeWeatherAlertService = mock(
            HomeWeatherAlertService.class
    );
    private final HomeWeatherProperties properties = properties();
    private final HomeService homeService = new HomeService(
            homeCoreService,
            homeWeatherService,
            homeWeatherAlertService,
            properties
    );

    @Test
    void 현재_위치가_있으면_최우선으로_날씨를_조회한다() {
        when(homeCoreService.load(1L)).thenReturn(coreData(null));
        when(homeWeatherService.getWeather(
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(HomeResponse.Weather.unavailable(
                "현재 위치",
                "CURRENT_LOCATION"
        ));
        when(homeWeatherAlertService.getAlerts(
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(HomeResponse.WeatherAlerts.unavailable());

        HomeResponse response = homeService.getHome(
                1L,
                "37.5412",
                "127.0178"
        );

        assertThat(response.weather().locationBasis())
                .isEqualTo("CURRENT_LOCATION");
        verify(homeWeatherService).getWeather(argThat(location ->
                location.basis()
                        == HomeWeatherLocation.Basis.CURRENT_LOCATION
                        && location.latitude() == 37.5412
                        && location.longitude() == 127.0178
        ));
        verify(homeWeatherAlertService).getAlerts(argThat(location ->
                location.basis()
                        == HomeWeatherLocation.Basis.CURRENT_LOCATION
                        && location.latitude() == 37.5412
                        && location.longitude() == 127.0178
        ));
    }

    @Test
    void 현재_위치가_없으면_다음_임장_위치를_사용한다() {
        HomeWeatherLocation nextVisitLocation = new HomeWeatherLocation(
                37.54,
                127.01,
                "성동구",
                HomeWeatherLocation.Basis.NEXT_VISIT_APARTMENT
        );
        when(homeCoreService.load(1L))
                .thenReturn(coreData(nextVisitLocation));
        when(homeWeatherService.getWeather(nextVisitLocation))
                .thenReturn(HomeResponse.Weather.unavailable(
                        "성동구",
                        "NEXT_VISIT_APARTMENT"
                ));
        when(homeWeatherAlertService.getAlerts(nextVisitLocation))
                .thenReturn(HomeResponse.WeatherAlerts.unavailable());

        HomeResponse response = homeService.getHome(1L, null, null);

        assertThat(response.weather().locationBasis())
                .isEqualTo("NEXT_VISIT_APARTMENT");
    }

    @Test
    void 위치와_다음_임장이_없으면_합의된_서울_기본_위치를_사용한다() {
        when(homeCoreService.load(1L)).thenReturn(coreData(null));
        when(homeWeatherService.getWeather(
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(HomeResponse.Weather.unavailable(
                "서울특별시 중구",
                "DEFAULT_LOCATION"
        ));
        when(homeWeatherAlertService.getAlerts(
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(HomeResponse.WeatherAlerts.unavailable());

        homeService.getHome(1L, null, null);

        verify(homeWeatherService).getWeather(argThat(location ->
                location.basis()
                        == HomeWeatherLocation.Basis.DEFAULT_LOCATION
                        && location.latitude() == 37.5665
                        && location.longitude() == 126.9780
        ));
    }

    @Test
    void 위도와_경도_중_하나만_전달하면_거부한다() {
        assertThatThrownBy(() -> homeService.getHome(1L, "37.5", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HOME_LOCATION_INCOMPLETE);
    }

    @Test
    void 좌표_범위와_NaN을_거부한다() {
        assertThatThrownBy(() -> homeService.getHome(
                1L,
                "91",
                "127"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HOME_LATITUDE_INVALID);
        assertThatThrownBy(() -> homeService.getHome(
                1L,
                "37",
                "NaN"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HOME_LONGITUDE_INVALID);
    }

    private HomeCoreData coreData(HomeWeatherLocation nextVisitLocation) {
        return new HomeCoreData(
                LocalDate.of(2026, 7, 29),
                0,
                HomeResponse.NextVisit.empty(),
                nextVisitLocation
        );
    }

    private HomeWeatherProperties properties() {
        return new HomeWeatherProperties();
    }
}
