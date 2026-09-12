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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestChecklistSelectAiClientTest {

    private MockRestServiceServer server;
    private RestChecklistSelectAiClient client;

    @BeforeEach
    void setUp() {
        FieldVisitAiProperties properties = new FieldVisitAiProperties();
        properties.setBaseUrl("http://localhost:8000");
        properties.setSelectPath("/internal/v1/checklists/select");
        properties.setConnectTimeout(Duration.ofSeconds(3));
        properties.setReadTimeout(Duration.ofSeconds(20));

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestChecklistSelectAiClient(builder.build(), properties);
    }

    @Test
    void 정상_응답을_역직렬화한다() {
        server.expect(requestTo("http://localhost:8000/internal/v1/checklists/select"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "itemCodes": ["PED_018","ENV_001","SUM_001","SUM_002","TRN_001","TRN_002","TRN_003","SAF_001","EXT_001","EXT_003","GRN_001","CON_001","BLD_001","BLD_002","COM_001","COM_002","EDU_001","EDU_002","MGT_001","MGT_002","PRK_001","PRK_002","UNT_001","ENV_002","GRN_002"]
                        }
                        """, MediaType.APPLICATION_JSON));

        ChecklistAiSelectResponse response = client.select(sampleRequest());
        assertThat(response.itemCodes()).hasSize(25);
        server.verify();
    }

    @Test
    void 잘못된_JSON은_InvalidResponseException이다() {
        server.expect(requestTo("http://localhost:8000/internal/v1/checklists/select"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.select(sampleRequest()))
                .isInstanceOf(ChecklistAiInvalidResponseException.class);
        server.verify();
    }

    @Test
    void 오백_응답은_ServerErrorException이다() {
        server.expect(requestTo("http://localhost:8000/internal/v1/checklists/select"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"detail\":\"provider failed\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.select(sampleRequest()))
                .isInstanceOf(ChecklistAiServerErrorException.class);
        server.verify();
    }

    @Test
    void 빈_body는_InvalidResponseException이다() {
        server.expect(requestTo("http://localhost:8000/internal/v1/checklists/select"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.select(sampleRequest()))
                .isInstanceOf(ChecklistAiInvalidResponseException.class);
        server.verify();
    }

    private ChecklistAiSelectRequest sampleRequest() {
        return new ChecklistAiSelectRequest(
                "v3-select-1",
                25,
                "LIVE",
                List.of("TRANSPORTATION"),
                List.of(new ChecklistAiSelectRequest.ShortlistItem(
                        "PED_018",
                        "PED",
                        "title",
                        List.of(),
                        List.of(),
                        1,
                        true
                ))
        );
    }
}
