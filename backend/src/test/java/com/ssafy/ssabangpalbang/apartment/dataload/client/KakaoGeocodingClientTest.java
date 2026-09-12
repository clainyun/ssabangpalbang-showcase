package com.ssafy.ssabangpalbang.apartment.dataload.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoGeocodingClientTest {

    private MockRestServiceServer server;
    private KakaoGeocodingClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new KakaoGeocodingClient(restTemplate, "kakao-test-key", 0);
    }

    @Test
    void returnsCoordinateAndSendsAuthorizationHeader() {
        server.expect(requestTo(containsString("query=")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK kakao-test-key"))
                .andRespond(withSuccess("""
                        {"documents":[{"x":"127.0654","y":"37.4812"}]}
                        """, MediaType.APPLICATION_JSON));

        var coordinate = client.find("서울 강남구");

        assertThat(coordinate).isPresent().get().satisfies(value -> {
            assertThat(value.longitude()).isEqualTo(127.0654);
            assertThat(value.latitude()).isEqualTo(37.4812);
        });
        server.verify();
    }

    @Test
    void returnsEmptyWhenDocumentsAreEmpty() {
        server.expect(requestTo(containsString("query=")))
                .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.find("없는 주소")).isEmpty();
        server.verify();
    }
}
