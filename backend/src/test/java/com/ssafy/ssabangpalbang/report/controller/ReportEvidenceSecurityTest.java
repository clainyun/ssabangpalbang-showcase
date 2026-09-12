package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.report.dto.response.ReportEvidenceListResponse;
import com.ssafy.ssabangpalbang.report.service.ReportEvidenceListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportEvidenceController.class)
@Import({AuthSecurityConfiguration.class, GlobalExceptionHandler.class})
class ReportEvidenceSecurityTest {

    private static final String URI = "/api/v1/reports/48/evidences";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportEvidenceListService reportEvidenceListService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get(URI))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(reportEvidenceListService, jwtTokenProvider);
    }

    @Test
    void 원문_상세도_Access_Token이_없으면_401을_반환한다()
            throws Exception {
        mockMvc.perform(get(URI + "/201"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(reportEvidenceListService, jwtTokenProvider);
    }

    @Test
    void 유효한_Access_Token의_회원_ID로_근거를_조회한다()
            throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(7L);
        when(reportEvidenceListService.getEvidenceList(
                7L, 48L, null, null, null, null, null
        )).thenReturn(emptyResponse());

        mockMvc.perform(get(URI)
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_EVIDENCE_LIST_SUCCESS"));

        verify(reportEvidenceListService).getEvidenceList(
                7L, 48L, null, null, null, null, null
        );
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

        verifyNoInteractions(reportEvidenceListService);
    }

    private ReportEvidenceListResponse emptyResponse() {
        return new ReportEvidenceListResponse(
                48L,
                new ReportEvidenceListResponse.AppliedFilters(
                        null, null, List.of()
                ),
                List.of(),
                new ReportEvidenceListResponse.EvidenceSummary(0, 0, 0, 0),
                null,
                false
        );
    }
}
