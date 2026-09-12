package com.ssafy.ssabangpalbang.home.weather;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoRegionClientTest {

    @Test
    void 좌표를_서울_법정동_구_이름으로_변환한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(builder)
                .build();
        HomeWeatherProperties properties = new HomeWeatherProperties();
        properties.setKakaoRestApiKey("test-kakao-key");
        KakaoRegionClient client = new KakaoRegionClient(
                builder.build(),
                properties
        );

        server.expect(requestTo(containsString(
                        "/v2/local/geo/coord2regioncode.json"
                )))
                .andExpect(requestTo(containsString("x=127.0178")))
                .andExpect(requestTo(containsString("y=37.5412")))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "KakaoAK test-kakao-key"
                ))
                .andRespond(withSuccess(
                        """
                                {
                                  "documents": [
                                    {
                                      "region_type": "H",
                                      "region_1depth_name": "서울특별시",
                                      "region_2depth_name": "성동구"
                                    },
                                    {
                                      "region_type": "B",
                                      "region_1depth_name": "서울특별시",
                                      "region_2depth_name": "성동구"
                                    }
                                  ]
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        assertThat(client.resolveSeoulDistrict(37.5412, 127.0178))
                .contains("성동구");
        server.verify();
    }
}
