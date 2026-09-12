package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.response.ReportStatusResponse;
import com.ssafy.ssabangpalbang.report.service.ReportStatusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportStatusController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReportStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportStatusService reportStatusService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedMember(7L),
                        null
                )
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 리포트_생성_상태_조회_성공_응답을_반환한다() throws Exception {
        when(reportStatusService.getStatus(7L, 48L))
                .thenReturn(response());

        mockMvc.perform(get("/api/v1/reports/48/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("REPORT_STATUS_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("리포트 생성 상태 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.reportId").value(48))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.progressRate").value(70))
                .andExpect(jsonPath("$.data.progressStage")
                        .value("ANALYZE"))
                .andExpect(jsonPath("$.data.progressMessage")
                        .value("참여자들의 현장 의견을 분석하고 있습니다."))
                .andExpect(jsonPath("$.data.detailAvailable").value(false))
                .andExpect(jsonPath("$.data.retryAvailable").value(false))
                .andExpect(jsonPath("$.data.failReason").isEmpty())
                .andExpect(jsonPath("$.data.createdAt")
                        .value("2026-07-25T14:58:00+09:00"))
                .andExpect(jsonPath("$.data.completedAt").isEmpty())
                .andExpect(jsonPath("$.data.updatedAt")
                        .value("2026-07-25T15:00:02+09:00"));

        verify(reportStatusService).getStatus(7L, 48L);
    }

    @Test
    void 리포트_ID가_올바르지_않으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/reports/0/status"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("reportId"))
                .andExpect(jsonPath("$.data.reason")
                         .value("리포트 ID는 1 이상의 숫자여야 합니다."));
    }

    @Test
    void 숫자가_아닌_리포트_ID도_동일한_400_계약을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/reports/not-a-number/status"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("reportId"))
                .andExpect(jsonPath("$.data.reason")
                        .value("리포트 ID는 1 이상의 숫자여야 합니다."));
    }

    @Test
    void 접근할_수_없는_리포트는_403을_반환한다() throws Exception {
        when(reportStatusService.getStatus(7L, 48L))
                .thenThrow(new BusinessException(
                        ErrorCode.REPORT_ACCESS_DENIED
                ));

        mockMvc.perform(get("/api/v1/reports/48/status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message")
                        .value("해당 리포트에 접근할 권한이 없습니다."));
    }

    @Test
    void 존재하지_않는_리포트는_404를_반환한다() throws Exception {
        when(reportStatusService.getStatus(7L, 999L))
                .thenThrow(new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/reports/999/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    void 계약에_없는_리포트_내부_필드는_노출하지_않는다() throws Exception {
        when(reportStatusService.getStatus(7L, 48L))
                .thenReturn(response());

        mockMvc.perform(get("/api/v1/reports/48/status"))
                .andExpect(jsonPath("$.data.studyId").doesNotExist())
                .andExpect(jsonPath("$.data.apartmentId").doesNotExist())
                .andExpect(jsonPath("$.data.resultJson").doesNotExist())
                .andExpect(jsonPath("$.data.publishedAt").doesNotExist());
    }

    private ReportStatusResponse response() {
        return new ReportStatusResponse(
                48L,
                ReportStatus.IN_PROGRESS,
                70,
                "ANALYZE",
                "참여자들의 현장 의견을 분석하고 있습니다.",
                false,
                false,
                null,
                OffsetDateTime.parse("2026-07-25T14:58:00+09:00"),
                null,
                OffsetDateTime.parse("2026-07-25T15:00:02+09:00")
        );
    }
}
