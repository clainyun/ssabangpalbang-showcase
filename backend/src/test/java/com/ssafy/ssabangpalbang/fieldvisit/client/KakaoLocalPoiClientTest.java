package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitRouteProperties;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoLocalPoiClientTest {

    @Test
    void SC4는_최대반경으로_캐시하고_초등학교만_분류한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FieldVisitRouteProperties properties = properties("test-key");
        KakaoLocalPoiClient client = new KakaoLocalPoiClient(
                builder.build(), properties, Caffeine.newBuilder().build()
        );
        server.expect(requestTo(containsString("category_group_code=SC4")))
                .andExpect(requestTo(containsString("radius=1500")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-key"))
                .andRespond(withSuccess(
                        """
                                {"documents":[
                                  {"id":"m","place_name":"중학교","category_name":"교육 > 학교 > 중학교","road_address_name":"서울","x":"127.0","y":"37.001","distance":"100"},
                                  {"id":"e","place_name":"초등학교","category_name":"교육 > 학교 > 초등학교","road_address_name":"서울","x":"127.0","y":"37.002","distance":"200"}
                                ]}
                                """,
                        MediaType.APPLICATION_JSON
                ));

        assertThat(client.findNearest(FacilityType.ELEMENTARY_SCHOOL, 1L, 37.0, 127.0))
                .map(KakaoLocalPoi::id)
                .contains("e");
        assertThat(client.findNearest(FacilityType.MIDDLE_SCHOOL, 1L, 37.0, 127.0))
                .map(KakaoLocalPoi::id)
                .contains("m");
        server.verify();
    }

    @Test
    void 어린이집은_유치원을_제외하고_장소명_접미사를_보조로_사용한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoLocalPoiClient client = new KakaoLocalPoiClient(
                builder.build(), properties("test-key"), Caffeine.newBuilder().build()
        );
        server.expect(requestTo(containsString("category_group_code=PS3")))
                .andRespond(withSuccess(
                        """
                                {"documents":[
                                  {"id":"k","place_name":"행복유치원","category_name":"교육 > 유치원","x":"127.0","y":"37.001","distance":"100"},
                                  {"id":"d","place_name":"행복어린이집","category_name":"","x":"127.0","y":"37.002","distance":"200"}
                                ]}
                                """,
                        MediaType.APPLICATION_JSON
                ));

        assertThat(client.findNearest(FacilityType.DAYCARE, 1L, 37.0, 127.0))
                .map(KakaoLocalPoi::id)
                .contains("d");
        server.verify();
    }

    @Test
    void 병원은_더_가까운_동물병원을_제외한_뒤_사람_병원을_고른다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoLocalPoiClient client = new KakaoLocalPoiClient(
                builder.build(), properties("test-key"), Caffeine.newBuilder().build()
        );
        server.expect(requestTo(containsString("category_group_code=HP8")))
                .andRespond(withSuccess(
                        """
                                {"documents":[
                                  {"id":"animal","place_name":"행복동물의료센터","category_name":"의료 > 동물병원","x":"127.0001","y":"37.0001","distance":"20"},
                                  {"id":"human","place_name":"행복내과","category_name":"의료 > 병원 > 내과","x":"127.0002","y":"37.0002","distance":"40"}
                                ]}
                                """,
                        MediaType.APPLICATION_JSON
                ));

        assertThat(client.findNearest(FacilityType.HOSPITAL, 1L, 37.0, 127.0))
                .map(KakaoLocalPoi::id)
                .contains("human");
        server.verify();
    }

    @Test
    void 좌표가_NaN이거나_거리가_없으면_유효하지_않은_응답으로_처리한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoLocalPoiClient client = new KakaoLocalPoiClient(
                builder.build(), properties("test-key"), Caffeine.newBuilder().build()
        );
        server.expect(requestTo(containsString("category_group_code=MT1")))
                .andRespond(withSuccess(
                        """
                                {"documents":[
                                  {"id":"nan","place_name":"잘못된 마트","category_name":"마트","x":"127.0","y":"NaN","distance":"100"},
                                  {"id":"missing","place_name":"거리 없는 마트","category_name":"마트","x":"127.0","y":"37.0"}
                                ]}
                                """,
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> client.findNearest(
                FacilityType.MART, 1L, 37.0, 127.0
        )).isInstanceOf(KakaoLocalPoiUnavailableException.class);
        server.verify();
    }

    @Test
    void API_키가_없으면_호출하지_않고_장애로_분류한다() {
        KakaoLocalPoiClient client = new KakaoLocalPoiClient(
                RestClient.create(), properties(""), Caffeine.newBuilder().build()
        );

        assertThatThrownBy(() -> client.findNearest(
                FacilityType.MART, 1L, 37.0, 127.0
        )).isInstanceOf(KakaoLocalPoiUnavailableException.class);
    }

    private static FieldVisitRouteProperties properties(String apiKey) {
        FieldVisitRouteProperties properties = new FieldVisitRouteProperties();
        properties.getPoi().setRestApiKey(apiKey);
        properties.getPoi().setPageSize(15);
        return properties;
    }
}
