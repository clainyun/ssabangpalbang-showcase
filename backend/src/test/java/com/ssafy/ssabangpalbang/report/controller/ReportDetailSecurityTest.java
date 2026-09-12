package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.response.ReportDetailResponse;
import com.ssafy.ssabangpalbang.report.service.ReportDetailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportDetailController.class)
@Import({
        AuthSecurityConfiguration.class,
        GlobalExceptionHandler.class
})
class ReportDetailSecurityTest {

    private static final String URI = "/api/v1/reports/48";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportDetailService reportDetailService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get(URI))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(reportDetailService, jwtTokenProvider);
    }

    @Test
    void 유효한_Access_Token의_회원_ID로_상세를_조회한다()
            throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(7L);
        when(reportDetailService.getDetail(7L, 48L))
                .thenReturn(response());

        mockMvc.perform(get(URI)
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_DETAIL_SUCCESS"));

        verify(jwtTokenProvider).parseAccessToken("access-token");
        verify(reportDetailService).getDetail(7L, 48L);
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

        verifyNoInteractions(reportDetailService);
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

        verifyNoInteractions(reportDetailService);
    }

    private ReportDetailResponse response() {
        return new ReportDetailResponse(
                48L,
                ReportStatus.DONE,
                "COMPLETED",
                "리포트 제목",
                "리포트 요약",
                new ReportDetailResponse.ReportApartmentSummary(
                        100L,
                        "반포 자이아파트",
                        "서울특별시 서초구 반포동",
                        3410,
                        "200812",
                        4200
                ),
                new ReportDetailResponse.ReportStudySummary(
                        13L,
                        "반포 임장 스터디",
                        "실거주 관점 비교",
                        OffsetDateTime.parse("2026-07-25T14:00:00+09:00"),
                        3
                ),
                new ReportDetailResponse.DetailMetrics(
                        6,
                        5,
                        83.3,
                        7,
                        0,
                        false
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new ReportDetailResponse.ViewerPermissions(
                        false,
                        false,
                        false,
                        true
                ),
                false,
                0,
                null,
                OffsetDateTime.parse("2026-07-25T15:01:30+09:00"),
                OffsetDateTime.parse("2026-07-25T15:01:31+09:00")
        );
    }
}
