package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.report.config.ReportInternalSecurityConfiguration;
import com.ssafy.ssabangpalbang.report.service.ReportAcquireService;
import com.ssafy.ssabangpalbang.report.service.ReportCompleteService;
import com.ssafy.ssabangpalbang.report.service.ReportFailService;
import com.ssafy.ssabangpalbang.report.service.ReportInputService;
import com.ssafy.ssabangpalbang.report.service.ReportProgressService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportInternalController.class)
@Import({
        ReportInternalSecurityConfiguration.class,
        AuthSecurityConfiguration.class
})
@TestPropertySource(properties = {
        "ssabangpalbang.report.internal.token=",
        "ssabangpalbang.report.internal.lease-duration=30m"
})
class ReportInternalBlankTokenSecurityTest {

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
    void 설정_Token이_비어있으면_어떤_Bearer도_401로_거부한다()
            throws Exception {
        mockMvc.perform(post("/internal/v1/reports/acquire")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer arbitrary-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studyId": 7,
                                  "sessionId": 3,
                                  "apartmentId": 100,
                                  "occurredAt": "2026-08-02T12:30:00+09:00"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INTERNAL_AUTHENTICATION_FAILED"));

        verifyNoInteractions(
                reportAcquireService,
                reportInputService,
                jwtTokenProvider
        );
    }

    @Test
    void 설정_Token이_비어있으면_정규화_입력_API도_401로_거부한다()
            throws Exception {
        mockMvc.perform(get(
                        "/internal/v1/reports/{reportId}/input",
                        48L
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer arbitrary-token"
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INTERNAL_AUTHENTICATION_FAILED"));

        verifyNoInteractions(
                reportAcquireService,
                reportInputService,
                jwtTokenProvider
        );
    }

    @Test
    void 설정_Token이_비어있으면_실패_API도_401로_거부한다()
            throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer arbitrary-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processingToken": "opaque-token",
                                  "processingAttempt": 1,
                                  "failedStage": "NORMALIZATION",
                                  "errorCode": "NORMALIZATION_FAILED",
                                  "message": "리포트 입력 정규화에 실패했습니다.",
                                  "retryable": false
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INTERNAL_AUTHENTICATION_FAILED"));

        verifyNoInteractions(
                reportFailService,
                jwtTokenProvider
        );
    }
}
