package com.ssafy.ssabangpalbang.apartment.dataload.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AptListClientTest {

    private MockRestServiceServer server;
    private AptListClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new AptListClient(restTemplate, "test-key", 0);
    }

    @Test
    void parsesApartmentList() {
        server.expect(requestTo(containsString("sigunguCode=11680")))
                .andRespond(withSuccess(response(1, """
                        {"kaptCode":"A1001","kaptName":"테스트아파트","bjdCode":"1168010100",
                         "as2":"강남구","as3":"역삼동"}
                        """), MediaType.APPLICATION_JSON));

        var result = client.findBySigunguCode("11680");

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.kaptCode()).isEqualTo("A1001");
            assertThat(item.kaptName()).isEqualTo("테스트아파트");
            assertThat(item.bjdCode()).isEqualTo("1168010100");
            assertThat(item.as2()).isEqualTo("강남구");
            assertThat(item.as3()).isEqualTo("역삼동");
        });
        server.verify();
    }

    @Test
    void requestsNextPageWhenTotalCountExceedsPageItems() {
        server.expect(requestTo(containsString("pageNo=1")))
                .andRespond(withSuccess(response(2,
                        "{\"kaptCode\":\"A1\",\"kaptName\":\"첫째\"}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("pageNo=2")))
                .andRespond(withSuccess(response(2,
                        "{\"kaptCode\":\"A2\",\"kaptName\":\"둘째\"}"), MediaType.APPLICATION_JSON));

        assertThat(client.findBySigunguCode("11680")).hasSize(2);
        server.verify();
    }

    @Test
    void returnsEmptyListOnServiceKeyError() {
        server.expect(requestTo(containsString("serviceKey=test-key")))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"30","resultMsg":"SERVICE_KEY_ERROR"}}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findBySigunguCode("11680")).isEmpty();
        server.verify();
    }

    @Test
    void stopsWhenPageReturnsNoItems() {
        server.expect(requestTo(containsString("pageNo=1")))
                .andRespond(withSuccess(response(5,
                        "{\"kaptCode\":\"A1\"}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("pageNo=2")))
                .andRespond(withSuccess(response(5, ""), MediaType.APPLICATION_JSON));

        assertThat(client.findBySigunguCode("11680")).hasSize(1);
        server.verify();
    }

    private String response(int totalCount, String items) {
        return """
                {"response":{"header":{"resultCode":"00"},"body":{
                  "totalCount":%d,"items":[%s]
                }}}
                """.formatted(totalCount, items);
    }
}
