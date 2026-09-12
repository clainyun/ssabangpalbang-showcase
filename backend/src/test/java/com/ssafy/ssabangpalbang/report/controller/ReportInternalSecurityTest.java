package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.report.config.ReportInternalSecurityConfiguration;
import com.ssafy.ssabangpalbang.report.dto.response.ReportAcquireResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportAcquireStatus;
import com.ssafy.ssabangpalbang.report.dto.response.ReportInputResponse;
import com.ssafy.ssabangpalbang.report.service.ReportAcquireService;
import com.ssafy.ssabangpalbang.report.service.ReportCompleteService;
import com.ssafy.ssabangpalbang.report.service.ReportFailService;
import com.ssafy.ssabangpalbang.report.service.ReportInputService;
import com.ssafy.ssabangpalbang.report.service.ReportProgressService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportInternalController.class)
@Import({
        ReportInternalSecurityConfiguration.class,
        AuthSecurityConfiguration.class
})
@TestPropertySource(properties = {
        "ssabangpalbang.report.internal.token=internal-test-token",
        "ssabangpalbang.report.internal.lease-duration=30m"
})
class ReportInternalSecurityTest {

    private static final String URI = "/internal/v1/reports/acquire";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportAcquireService reportAcquireService;

    @MockitoBean
    private ReportProgressService reportProgressService;

    @MockitoBean
    private ReportInputService reportInputService;

    @MockitoBean
    private ReportCompleteService reportCompleteService;

    @MockitoBean
    private ReportFailService reportFailService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 내부_Token이_없으면_전용_401을_반환한다() throws Exception {
        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INTERNAL_AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));

        verifyNoInteractions(reportAcquireService, jwtTokenProvider);
    }

    @Test
    void Bearer가_아닌_인증_형식도_거부한다() throws Exception {
        mockMvc.perform(post(URI)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Basic internal-test-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INTERNAL_AUTHENTICATION_FAILED"));

        verifyNoInteractions(reportAcquireService, jwtTokenProvider);
    }

    @Test
    void 잘못된_내부_Token과_사용자_JWT는_모두_거부한다() throws Exception {
        mockMvc.perform(post(URI)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INTERNAL_AUTHENTICATION_FAILED"));

        verifyNoInteractions(reportAcquireService, jwtTokenProvider);
    }

    @Test
    void 올바른_내부_Token은_JWT_검증없이_Controller에_도달한다()
            throws Exception {
        when(reportAcquireService.acquire(any()))
                .thenReturn(ReportAcquireResponse.terminal(
                        ReportAcquireStatus.ALREADY_COMPLETED,
                        48L
                ));

        mockMvc.perform(post(URI)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer internal-test-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("ALREADY_COMPLETED"));

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void 정규화_입력_API도_내부_Token_없이는_401이다()
            throws Exception {
        mockMvc.perform(get(
                        "/internal/v1/reports/{reportId}/input",
                        48L
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INTERNAL_AUTHENTICATION_FAILED"));

        verifyNoInteractions(reportInputService, jwtTokenProvider);
    }

    @Test
    void 사용자_JWT로는_정규화_입력_API를_호출할_수_없다()
            throws Exception {
        mockMvc.perform(get(
                        "/internal/v1/reports/{reportId}/input",
                        48L
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer access-token"
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INTERNAL_AUTHENTICATION_FAILED"));

        verifyNoInteractions(reportInputService, jwtTokenProvider);
    }

    @Test
    void 올바른_내부_Token은_정규화_입력_API를_조회한다()
            throws Exception {
        when(reportInputService.getInput(48L))
                .thenReturn(sampleInputResponse());

        mockMvc.perform(get(
                        "/internal/v1/reports/{reportId}/input",
                        48L
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer internal-test-token"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INPUT_SUCCESS"));

        verify(reportInputService).getInput(48L);
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void 진행_단계_API도_내부_Token_없이는_401이다() throws Exception {
        mockMvc.perform(patch("/internal/v1/reports/{reportId}/progress", 48L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProgressRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INTERNAL_AUTHENTICATION_FAILED"));

        verifyNoInteractions(reportProgressService, jwtTokenProvider);
    }

    @Test
    void 사용자_JWT로는_진행_단계_API를_호출할_수_없다() throws Exception {
        mockMvc.perform(patch("/internal/v1/reports/{reportId}/progress", 48L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProgressRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INTERNAL_AUTHENTICATION_FAILED"));

        verifyNoInteractions(reportProgressService, jwtTokenProvider);
    }

    @Test
    void 올바른_내부_Token은_진행_단계_API의_빈_204를_반환한다()
            throws Exception {
        mockMvc.perform(patch("/internal/v1/reports/{reportId}/progress", 48L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer internal-test-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProgressRequest()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(""));

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void 완료_API도_내부_Token_없이는_401이다() throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/complete",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCompleteRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INTERNAL_AUTHENTICATION_FAILED"));

        verifyNoInteractions(reportCompleteService, jwtTokenProvider);
    }

    @Test
    void 사용자_JWT로는_완료_API를_호출할_수_없다() throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/complete",
                        48L
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCompleteRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INTERNAL_AUTHENTICATION_FAILED"));

        verifyNoInteractions(reportCompleteService, jwtTokenProvider);
    }

    @Test
    void 올바른_내부_Token은_완료_API의_빈_204를_반환한다()
            throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/complete",
                        48L
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer internal-test-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCompleteRequest()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(""));

        verify(reportCompleteService).complete(any(), any());
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void 실패_API도_내부_Token_없이는_401이다() throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INTERNAL_AUTHENTICATION_FAILED"));

        verifyNoInteractions(reportFailService, jwtTokenProvider);
    }

    @Test
    void 잘못된_내부_Token으로는_실패_API를_호출할_수_없다()
            throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INTERNAL_AUTHENTICATION_FAILED"));

        verifyNoInteractions(reportFailService, jwtTokenProvider);
    }

    @Test
    void 사용자_JWT로는_실패_API를_호출할_수_없다() throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INTERNAL_AUTHENTICATION_FAILED"));

        verifyNoInteractions(reportFailService, jwtTokenProvider);
    }

    @Test
    void 올바른_내부_Token은_실패_API의_빈_204를_반환한다()
            throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer internal-test-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(""));

        verify(reportFailService).fail(any(), any());
        verifyNoInteractions(jwtTokenProvider);
    }

    private String validRequest() {
        return """
                {
                  "studyId": 7,
                  "sessionId": 3,
                  "apartmentId": 100,
                  "occurredAt": "2026-08-02T12:30:00+09:00"
                }
                """;
    }

    private String validProgressRequest() {
        return """
                {
                  "processingToken": "opaque-token",
                  "processingAttempt": 1,
                  "stage": "NORMALIZATION"
                }
                """;
    }

    private String validCompleteRequest() {
        return """
                {
                  "processingToken": "opaque-token",
                  "processingAttempt": 1,
                  "generationResult": {
                    "title": "반포 자이 임장 리포트",
                    "summary": "참여자 의견을 종합한 요약입니다.",
                    "metrics": {
                      "totalChecklistItemCount": 0,
                      "completedChecklistItemCount": 0,
                      "averageCompletionRate": 0.0,
                      "fieldRecordCount": 0
                    },
                    "topPositiveFeatures": [],
                    "topCautionFeatures": [],
                    "commonOpinions": [],
                    "conflictingOpinions": [],
                    "categories": []
                  },
                  "evidenceResult": {
                    "claims": []
                  }
                }
                """;
    }

    private String validFailRequest() {
        return """
                {
                  "processingToken": "opaque-token",
                  "processingAttempt": 1,
                  "failedStage": "NORMALIZATION",
                  "errorCode": "NORMALIZATION_FAILED",
                  "message": "리포트 입력 정규화에 실패했습니다.",
                  "retryable": false
                }
                """;
    }

    private ReportInputResponse sampleInputResponse() {
        OffsetDateTime startedAt = OffsetDateTime.of(
                2026,
                7,
                20,
                14,
                0,
                0,
                0,
                ZoneOffset.ofHours(9)
        );
        return new ReportInputResponse(
                ReportInputResponse.SCHEMA_VERSION,
                48L,
                7L,
                15L,
                900L,
                FieldSessionStatus.ENDED,
                startedAt,
                startedAt.plusHours(2),
                startedAt.plusHours(2).plusMinutes(5),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
