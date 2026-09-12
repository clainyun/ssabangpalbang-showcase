package com.ssafy.ssabangpalbang.home.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.home.dto.response.HomeResponse;
import com.ssafy.ssabangpalbang.home.service.HomeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeController.class)
@Import({
        GlobalExceptionHandler.class,
        AuthSecurityConfiguration.class
})
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HomeService homeService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 홈을_조회하면_200과_명세_응답을_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(homeService.getHome(1L, "37.5", "127.0"))
                .thenReturn(response());

        mockMvc.perform(get("/api/v1/home")
                        .header("Authorization", "Bearer access-token")
                        .queryParam("latitude", "37.5")
                        .queryParam("longitude", "127.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("HOME_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("홈 화면 정보 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.unreadNotificationCount")
                        .value(2))
                .andExpect(jsonPath("$.data.nextVisit.exists")
                        .value(false))
                .andExpect(jsonPath("$.data.member").doesNotExist())
                .andExpect(jsonPath("$.data.activeStudies").doesNotExist())
                .andExpect(jsonPath("$.data.reports").doesNotExist())
                .andExpect(jsonPath("$.data.weather.available")
                        .value(false))
                .andExpect(jsonPath("$.data.weather.temperatureCelsius")
                        .value(nullValue()))
                .andExpect(jsonPath("$.data.weatherAlerts.available")
                        .value(false))
                .andExpect(jsonPath("$.data.weatherAlerts.items")
                        .isEmpty())
                .andExpect(jsonPath("$.data.weatherAlerts.source")
                        .value("KMA"))
                .andExpect(jsonPath("$.data.weatherAlerts.freshness")
                        .value("UNAVAILABLE"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(homeService).getHome(1L, "37.5", "127.0");
    }

    @Test
    void Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verify(homeService, never()).getHome(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void 위도만_전달하면_명세_오류를_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(homeService.getHome(1L, "37.5", null))
                .thenThrow(new BusinessException(
                        ErrorCode.HOME_LOCATION_INCOMPLETE,
                        Map.of(
                                "reason",
                                "위도와 경도는 함께 전달해야 합니다."
                        )
                ));

        mockMvc.perform(get("/api/v1/home")
                        .header("Authorization", "Bearer access-token")
                        .queryParam("latitude", "37.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("HOME_LOCATION_INCOMPLETE"))
                .andExpect(jsonPath("$.data.reason")
                        .value("위도와 경도는 함께 전달해야 합니다."));
    }

    private void authenticate(String accessToken, Long memberId) {
        when(jwtTokenProvider.parseAccessToken(accessToken))
                .thenReturn(memberId);
    }

    private HomeResponse response() {
        return new HomeResponse(
                LocalDate.of(2026, 7, 29),
                2,
                HomeResponse.NextVisit.empty(),
                HomeResponse.Weather.unavailable(
                        "서울특별시 중구",
                        "DEFAULT_LOCATION"
                ),
                HomeResponse.WeatherAlerts.unavailable()
        );
    }
}
