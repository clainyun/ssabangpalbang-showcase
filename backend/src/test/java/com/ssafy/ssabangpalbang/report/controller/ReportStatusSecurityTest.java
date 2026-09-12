package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.response.ReportStatusResponse;
import com.ssafy.ssabangpalbang.report.service.ReportStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportStatusController.class)
@Import({
        AuthSecurityConfiguration.class,
        GlobalExceptionHandler.class
})
class ReportStatusSecurityTest {

    private static final String URI = "/api/v1/reports/48/status";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportStatusService reportStatusService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get(URI))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(reportStatusService, jwtTokenProvider);
    }

    @Test
    void 유효한_Access_Token의_회원_ID로_상태를_조회한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(7L);
        when(reportStatusService.getStatus(7L, 48L))
                .thenReturn(response());

        mockMvc.perform(get(URI)
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_STATUS_SUCCESS"));

        verify(jwtTokenProvider).parseAccessToken("access-token");
        verify(reportStatusService).getStatus(7L, 48L);
    }

    @Test
    void 위변조된_Access_Token은_401을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("tampered-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(get(URI)
                        .header("Authorization", "Bearer tampered-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(reportStatusService);
    }

    @Test
    void 만료된_Access_Token은_전용_401을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("expired-token"))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_ACCESS_TOKEN_EXPIRED
                ));

        mockMvc.perform(get(URI)
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_EXPIRED"));

        verifyNoInteractions(reportStatusService);
    }

    private ReportStatusResponse response() {
        return new ReportStatusResponse(
                48L,
                ReportStatus.PENDING,
                0,
                "COLLECT",
                "임장 기록을 수집할 준비를 하고 있습니다.",
                false,
                false,
                null,
                OffsetDateTime.parse("2026-07-25T14:58:00+09:00"),
                null,
                OffsetDateTime.parse("2026-07-25T15:00:00+09:00")
        );
    }
}
