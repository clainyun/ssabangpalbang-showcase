package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestChecklistAiClientTest {

    private MockRestServiceServer server;
    private RestChecklistAiClient client;

    @BeforeEach
    void setUp() {
        FieldVisitAiProperties properties = new FieldVisitAiProperties();
        properties.setBaseUrl("http://localhost:8000");
        properties.setGeneratePath("/internal/v1/checklists/generate");
        properties.setConnectTimeout(Duration.ofSeconds(3));
        properties.setReadTimeout(Duration.ofSeconds(20));

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestChecklistAiClient(builder.build(), properties);
    }

    @Test
    void 정상_응답을_역직렬화한다() {
        server.expect(requestTo("http://localhost:8000/internal/v1/checklists/generate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "category": "교통",
                              "title": "역 접근성",
                              "subtitle": "도보 확인",
                              "displayOrder": 1
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ChecklistAiGenerateResponse response = client.generate(
                new ChecklistAiGenerateRequest(null)
        );

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).title()).isEqualTo("역 접근성");
        server.verify();
    }

    @Test
    void 오백_응답은_ServerErrorException이다() {
        server.expect(requestTo("http://localhost:8000/internal/v1/checklists/generate"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"detail\":\"provider failed\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(new ChecklistAiGenerateRequest(null)))
                .isInstanceOf(ChecklistAiServerErrorException.class);
        server.verify();
    }

    @Test
    void 사백_응답은_InvalidResponseException이다() {
        server.expect(requestTo("http://localhost:8000/internal/v1/checklists/generate"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"detail\":\"validation\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(new ChecklistAiGenerateRequest(null)))
                .isInstanceOf(ChecklistAiInvalidResponseException.class);
        server.verify();
    }
}
