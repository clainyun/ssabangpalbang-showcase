package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.response.ReportResponseCode;
import com.ssafy.ssabangpalbang.report.dto.response.ReportRetryResponse;
import com.ssafy.ssabangpalbang.report.service.ReportRetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportRetryController.class)
@Import({
        AuthSecurityConfiguration.class,
        GlobalExceptionHandler.class
})
class ReportRetrySecurityTest {

    private static final String URI = "/api/v1/reports/48/retry";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportRetryService reportRetryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void Access_Token이_없으면_재생성을_401로_거부한다()
            throws Exception {
        mockMvc.perform(post(URI))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(reportRetryService, jwtTokenProvider);
    }

    @Test
    void 유효한_Access_Token의_회원_ID로_재생성을_요청한다()
            throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(7L);
        when(reportRetryService.retry(7L, 48L)).thenReturn(
                new ReportRetryService.RetryResult(
                        HttpStatus.ACCEPTED,
                        ReportResponseCode.REPORT_RETRY_ACCEPTED,
                        new ReportRetryResponse(
                                48L,
                                ReportStatus.PENDING,
                                0,
                                "RECORD_COLLECTION",
                                OffsetDateTime.parse(
                                        "2026-07-25T15:30:00+09:00"
                                ),
                                "/api/v1/reports/48/status"
                        )
                )
        );

        mockMvc.perform(post(URI)
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_RETRY_ACCEPTED"));

        verify(jwtTokenProvider).parseAccessToken("access-token");
        verify(reportRetryService).retry(7L, 48L);
    }

    @Test
    void 위변조된_Access_Token은_401을_반환한다()
            throws Exception {
        when(jwtTokenProvider.parseAccessToken("tampered-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(post(URI)
                        .header("Authorization", "Bearer tampered-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(reportRetryService);
    }
}
