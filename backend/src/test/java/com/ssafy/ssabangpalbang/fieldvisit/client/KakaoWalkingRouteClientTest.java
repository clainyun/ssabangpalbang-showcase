package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitRouteProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class KakaoWalkingRouteClientTest {

    @Test
    void 경유지와_도착지를_SHORTEST_한번으로_조회하고_LineString을_조립한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoWalkingRouteClient client = new KakaoWalkingRouteClient(
                builder.build(), properties("test-key")
        );
        server.expect(requestTo(containsString("/v2/routing/walk")))
                .andExpect(queryParam("start_x", "127.0"))
                .andExpect(queryParam("start_y", "37.0"))
                .andExpect(queryParam("via_x", "127.1,127.2"))
                .andExpect(queryParam("via_y", "37.1,37.2"))
                .andExpect(queryParam("end_x", "127.3"))
                .andExpect(queryParam("end_y", "37.3"))
                .andExpect(queryParam("route_mode", "SHORTEST"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-key"))
                .andRespond(withSuccess(
                        """
                                {
                                  "status":"OK",
                                  "route":{
                                    "properties":{"totalDistance":1500,"totalTime":900},
                                    "legs":[
                                      {
                                        "properties":{"distance":500,"time":241},
                                        "steps":[{"path":{"points":[[127.0,37.0],[127.1,37.1]]}}]
                                      },
                                      {
                                        "properties":{"distance":734,"time":360},
                                        "steps":[{"path":{"points":[[127.1,37.1],[127.2,37.2]]}}]
                                      },
                                      {
                                        "properties":{"distance":266,"time":299},
                                        "steps":[{"path":{"points":[[127.2,37.2],[127.3,37.3]]}}]
                                      }
                                    ]
                                  }
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        KakaoWalkingRoute route = client.findRoute(
                new WalkingRoutePoint(37.0, 127.0),
                List.of(
                        new WalkingRoutePoint(37.1, 127.1),
                        new WalkingRoutePoint(37.2, 127.2),
                        new WalkingRoutePoint(37.3, 127.3)
                )
        );

        assertThat(route.totalDistanceMeters()).isEqualTo(1500);
        assertThat(route.totalTimeSeconds()).isEqualTo(900);
        assertThat(route.legs()).extracting(KakaoWalkingRoute.Leg::distanceMeters)
                .containsExactly(500, 734, 266);
        assertThat(route.geometry().path("type").asText()).isEqualTo("LineString");
        assertThat(route.geometry().path("coordinates")).hasSize(4);
        server.verify();
    }

    @Test
    void leg_수가_경유지_수와_다르면_응답을_거부한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoWalkingRouteClient client = new KakaoWalkingRouteClient(
                builder.build(), properties("test-key")
        );
        server.expect(requestTo(containsString("/v2/routing/walk")))
                .andRespond(withSuccess(
                        """
                                {
                                  "status":"OK",
                                  "route":{
                                    "properties":{"totalDistance":100,"totalTime":60},
                                    "legs":[]
                                  }
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> client.findRoute(
                new WalkingRoutePoint(37.0, 127.0),
                List.of(
                        new WalkingRoutePoint(37.1, 127.1),
                        new WalkingRoutePoint(37.2, 127.2)
                )
        )).isInstanceOf(KakaoWalkingRouteUnavailableException.class);
        server.verify();
    }

    @Test
    void API_키가_없으면_호출하지_않고_장애로_분류한다() {
        KakaoWalkingRouteClient client = new KakaoWalkingRouteClient(
                RestClient.create(), properties("")
        );

        assertThatThrownBy(() -> client.findRoute(
                new WalkingRoutePoint(37.0, 127.0),
                List.of(
                        new WalkingRoutePoint(37.1, 127.1),
                        new WalkingRoutePoint(37.2, 127.2)
                )
        )).isInstanceOf(KakaoWalkingRouteUnavailableException.class);
    }

    @Test
    void 한개_경유지나_유효하지_않은_좌표는_호출전에_거부한다() {
        KakaoWalkingRouteClient client = new KakaoWalkingRouteClient(
                RestClient.create(), properties("test-key")
        );

        assertThatThrownBy(() -> client.findRoute(
                new WalkingRoutePoint(37.0, 127.0),
                List.of(new WalkingRoutePoint(37.1, 127.1))
        )).isInstanceOf(KakaoWalkingRouteUnavailableException.class);
        assertThatThrownBy(() -> client.findRoute(
                new WalkingRoutePoint(Double.NaN, 127.0),
                List.of(
                        new WalkingRoutePoint(37.1, 127.1),
                        new WalkingRoutePoint(37.2, 127.2)
                )
        )).isInstanceOf(KakaoWalkingRouteUnavailableException.class);
    }

    @Test
    void route_총합과_leg_합계가_다르면_응답을_거부한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoWalkingRouteClient client = new KakaoWalkingRouteClient(
                builder.build(), properties("test-key")
        );
        server.expect(requestTo(containsString("/v2/routing/walk")))
                .andRespond(withSuccess(
                        """
                                {
                                  "status":"OK",
                                  "route":{
                                    "properties":{"totalDistance":101,"totalTime":60},
                                    "legs":[
                                      {"properties":{"distance":50,"time":30},"steps":[{"path":{"points":[[127.0,37.0],[127.1,37.1]]}}]},
                                      {"properties":{"distance":50,"time":30},"steps":[{"path":{"points":[[127.1,37.1],[127.2,37.2]]}}]}
                                    ]
                                  }
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> client.findRoute(
                new WalkingRoutePoint(37.0, 127.0),
                List.of(
                        new WalkingRoutePoint(37.1, 127.1),
                        new WalkingRoutePoint(37.2, 127.2)
                )
        )).isInstanceOf(KakaoWalkingRouteUnavailableException.class);
        server.verify();
    }

    @Test
    void 출발점과_같은_첫_경유지의_0거리_leg는_허용한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoWalkingRouteClient client = new KakaoWalkingRouteClient(
                builder.build(), properties("test-key")
        );
        server.expect(requestTo(containsString("/v2/routing/walk")))
                .andRespond(withSuccess(
                        """
                                {
                                  "status":"OK",
                                  "route":{
                                    "properties":{"totalDistance":100,"totalTime":60},
                                    "legs":[
                                      {"properties":{"distance":0,"time":0},"steps":[]},
                                      {"properties":{"distance":100,"time":60},"steps":[{"path":{"points":[[127.0,37.0],[127.2,37.2]]}}]}
                                    ]
                                  }
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        KakaoWalkingRoute route = client.findRoute(
                new WalkingRoutePoint(37.0, 127.0),
                List.of(
                        new WalkingRoutePoint(37.0, 127.0),
                        new WalkingRoutePoint(37.2, 127.2)
                )
        );

        assertThat(route.legs()).extracting(KakaoWalkingRoute.Leg::distanceMeters)
                .containsExactly(0, 100);
        assertThat(route.geometry().path("coordinates")).hasSize(2);
        server.verify();
    }

    @Test
    void HTTP_오류와_범위밖_응답_좌표는_장애로_분류한다() {
        RestClient.Builder errorBuilder = RestClient.builder();
        MockRestServiceServer errorServer = MockRestServiceServer.bindTo(errorBuilder).build();
        KakaoWalkingRouteClient errorClient = new KakaoWalkingRouteClient(
                errorBuilder.build(), properties("test-key")
        );
        errorServer.expect(requestTo(containsString("/v2/routing/walk")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> errorClient.findRoute(
                new WalkingRoutePoint(37.0, 127.0),
                List.of(
                        new WalkingRoutePoint(37.1, 127.1),
                        new WalkingRoutePoint(37.2, 127.2)
                )
        )).isInstanceOf(KakaoWalkingRouteUnavailableException.class);
        errorServer.verify();

        RestClient.Builder coordinateBuilder = RestClient.builder();
        MockRestServiceServer coordinateServer = MockRestServiceServer
                .bindTo(coordinateBuilder).build();
        KakaoWalkingRouteClient coordinateClient = new KakaoWalkingRouteClient(
                coordinateBuilder.build(), properties("test-key")
        );
        coordinateServer.expect(requestTo(containsString("/v2/routing/walk")))
                .andRespond(withSuccess(
                        """
                                {
                                  "status":"OK",
                                  "route":{
                                    "properties":{"totalDistance":100,"totalTime":60},
                                    "legs":[
                                      {"properties":{"distance":50,"time":30},"steps":[{"path":{"points":[[127.0,91.0],[127.1,37.1]]}}]},
                                      {"properties":{"distance":50,"time":30},"steps":[{"path":{"points":[[127.1,37.1],[127.2,37.2]]}}]}
                                    ]
                                  }
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> coordinateClient.findRoute(
                new WalkingRoutePoint(37.0, 127.0),
                List.of(
                        new WalkingRoutePoint(37.1, 127.1),
                        new WalkingRoutePoint(37.2, 127.2)
                )
        )).isInstanceOf(KakaoWalkingRouteUnavailableException.class);
        coordinateServer.verify();
    }

    private static FieldVisitRouteProperties properties(String apiKey) {
        FieldVisitRouteProperties properties = new FieldVisitRouteProperties();
        properties.getPoi().setRestApiKey(apiKey);
        return properties;
    }
}
