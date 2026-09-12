package com.ssafy.ssabangpalbang.home.weather;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KmaWeatherAlertClientTest {

    @Test
    void 최신_발효중_공식특보만_심각도순으로_반환한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(builder)
                .build();
        HomeWeatherProperties properties = new HomeWeatherProperties();
        properties.setKmaWeatherAlertServiceKey("test-service-key");
        KmaWeatherAlertClient client = new KmaWeatherAlertClient(
                builder.build(),
                properties
        );

        server.expect(requestTo(containsString("/getPwnCd")))
                .andExpect(requestTo(containsString(
                        "areaCode=L1100200"
                )))
                .andExpect(requestTo(containsString(
                        "serviceKey=test-service-key"
                )))
                .andRespond(withSuccess(
                        """
                                {
                                  "response": {
                                    "header": {
                                      "resultCode": "00",
                                      "resultMsg": "NORMAL_SERVICE"
                                    },
                                    "body": {
                                      "items": {
                                        "item": [
                                          {
                                            "warnVar": 12,
                                            "warnStress": 0,
                                            "command": 1,
                                            "cancel": 0,
                                            "tmFc": "202607291000",
                                            "tmSeq": 10,
                                            "areaCode": "L1100200",
                                            "areaName": "서울동북권",
                                            "startTime": "202607291000"
                                          },
                                          {
                                            "warnVar": 2,
                                            "warnStress": 1,
                                            "command": 1,
                                            "cancel": 0,
                                            "tmFc": "202607291030",
                                            "tmSeq": 11,
                                            "areaCode": "L1100200",
                                            "areaName": "서울동북권",
                                            "startTime": "202607291030"
                                          },
                                          {
                                            "warnVar": 1,
                                            "warnStress": 0,
                                            "command": 1,
                                            "cancel": 0,
                                            "tmFc": "202607290800",
                                            "tmSeq": 8,
                                            "areaCode": "L1100200",
                                            "areaName": "서울동북권",
                                            "startTime": "202607290800"
                                          },
                                          {
                                            "warnVar": 1,
                                            "warnStress": 0,
                                            "command": 2,
                                            "cancel": 0,
                                            "tmFc": "202607290900",
                                            "tmSeq": 9,
                                            "areaCode": "L1100200",
                                            "areaName": "서울동북권",
                                            "startTime": "202607290900"
                                          },
                                          {
                                            "warnVar": 8,
                                            "warnStress": 0,
                                            "command": 1,
                                            "cancel": 0,
                                            "tmFc": "202607290600",
                                            "tmSeq": 6,
                                            "areaCode": "L1100200",
                                            "areaName": "서울동북권",
                                            "startTime": "202607290600",
                                            "endTime": "202607291000"
                                          }
                                        ]
                                      }
                                    }
                                  }
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        KmaWeatherAlertSnapshot result = client.fetch(
                new SeoulWeatherAlertArea("L1100200", "서울동북권"),
                Instant.parse("2026-07-29T02:00:00Z")
        );

        assertThat(result.alerts())
                .extracting(KmaWeatherAlertData::title)
                .containsExactly("호우경보", "폭염주의보");
        assertThat(result.alerts().get(0).type()).isEqualTo("HEAVY_RAIN");
        assertThat(result.alerts().get(0).level()).isEqualTo("WARNING");
        server.verify();
    }
}
