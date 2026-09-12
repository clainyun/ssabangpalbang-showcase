package com.ssafy.ssabangpalbang.report.client;

import com.ssafy.ssabangpalbang.report.config.ReportRagIndexProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestReportRagIndexClientTest {

    private MockRestServiceServer server;
    private RestReportRagIndexClient client;

    @BeforeEach
    void setUp() {
        ReportRagIndexProperties properties = new ReportRagIndexProperties();
        properties.setBaseUrl("http://localhost:8000");
        properties.setIndexPath("/internal/v1/rag/reports/{reportId}");
        properties.setConnectTimeout(Duration.ofSeconds(3));
        properties.setReadTimeout(Duration.ofSeconds(60));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestReportRagIndexClient(builder.build(), properties);
    }

    @Test
    void 색인_성공_응답을_그대로_매핑한다() {
        server.expect(requestTo(
                        "http://localhost:8000/internal/v1/rag/reports/73"
                ))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "reportId": 73,
                          "apartmentId": 44,
                          "indexed": true,
                          "chunkCount": 1,
                          "deletedCount": 0,
                          "skipReason": null
                        }
                        """, MediaType.APPLICATION_JSON));

        ReportRagIndexResponse response = client.index(73L);

        assertThat(response.reportId()).isEqualTo(73L);
        assertThat(response.apartmentId()).isEqualTo(44L);
        assertThat(response.indexed()).isTrue();
        assertThat(response.chunkCount()).isEqualTo(1);
        assertThat(response.deletedCount()).isZero();
        assertThat(response.skipReason()).isNull();
        server.verify();
    }

    @Test
    void 미색인_응답도_예외_없이_매핑한다() {
        server.expect(requestTo(
                        "http://localhost:8000/internal/v1/rag/reports/73"
                ))
                .andRespond(withSuccess("""
                        {
                          "reportId": 73,
                          "apartmentId": null,
                          "indexed": false,
                          "chunkCount": 0,
                          "deletedCount": 0,
                          "skipReason": "NOT_VISIBLE"
                        }
                        """, MediaType.APPLICATION_JSON));

        ReportRagIndexResponse response = client.index(73L);

        assertThat(response.indexed()).isFalse();
        assertThat(response.skipReason()).isEqualTo("NOT_VISIBLE");
        server.verify();
    }

    @Test
    void 오백삼_응답은_색인_예외다() {
        server.expect(requestTo(
                        "http://localhost:8000/internal/v1/rag/reports/73"
                ))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"detail\":{\"code\":\"RAG_DISABLED\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.index(73L))
                .isInstanceOf(ReportRagIndexException.class);
        server.verify();
    }

    @Test
    void 오백_응답은_색인_예외다() {
        server.expect(requestTo(
                        "http://localhost:8000/internal/v1/rag/reports/73"
                ))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.index(73L))
                .isInstanceOf(ReportRagIndexException.class);
        server.verify();
    }

    @Test
    void 연결_실패는_색인_예외다() {
        server.expect(requestTo(
                        "http://localhost:8000/internal/v1/rag/reports/73"
                ))
                .andRespond(request -> {
                    throw new ResourceAccessException("connection refused");
                });

        assertThatThrownBy(() -> client.index(73L))
                .isInstanceOf(ReportRagIndexException.class);
        server.verify();
    }

    @Test
    void 빈_응답은_색인_예외다() {
        server.expect(requestTo(
                        "http://localhost:8000/internal/v1/rag/reports/73"
                ))
                .andRespond(withSuccess());

        assertThatThrownBy(() -> client.index(73L))
                .isInstanceOf(ReportRagIndexException.class);
        server.verify();
    }
}
