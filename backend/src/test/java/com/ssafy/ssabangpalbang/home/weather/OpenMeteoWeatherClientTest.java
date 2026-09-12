package com.ssafy.ssabangpalbang.home.weather;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenMeteoWeatherClientTest {

    @Test
    void 공식_현재_날씨와_PM10_필드를_읽는다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(builder)
                .build();
        OpenMeteoWeatherClient client = new OpenMeteoWeatherClient(
                builder.build(),
                properties()
        );
        server.expect(requestTo(containsString("/v1/forecast")))
                .andRespond(withSuccess(
                        """
                                {
                                  "current": {
                                    "time": "2026-07-29T11:00",
                                    "temperature_2m": 29.4,
                                    "weather_code": 2,
                                    "is_day": 1,
                                    "precipitation_probability": 20
                                  }
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo(containsString("/v1/air-quality")))
                .andRespond(withSuccess(
                        """
                                {
                                  "current": {
                                    "pm10": 31.2
                                  }
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        OpenMeteoWeatherData result = client.fetch(37.5665, 126.9780);

        assertThat(result.temperatureCelsius()).isEqualTo(29.4);
        assertThat(result.weatherCode()).isEqualTo(2);
        assertThat(result.rainProbability()).isEqualTo(20);
        assertThat(result.fineDustValue()).isEqualTo(31.2);
        assertThat(result.observedAt().toString())
                .isEqualTo("2026-07-29T11:00+09:00");
        server.verify();
    }

    private HomeWeatherProperties properties() {
        return new HomeWeatherProperties();
    }
}
