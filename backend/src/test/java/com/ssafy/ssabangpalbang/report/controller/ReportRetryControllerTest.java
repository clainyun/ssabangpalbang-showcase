package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.response.ReportResponseCode;
import com.ssafy.ssabangpalbang.report.dto.response.ReportRetryResponse;
import com.ssafy.ssabangpalbang.report.service.ReportRetryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportRetryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReportRetryControllerTest {

    private static final String URI = "/api/v1/reports/{reportId}/retry";
    private static final OffsetDateTime RETRY_REQUESTED_AT =
            OffsetDateTime.parse("2026-07-25T15:30:00+09:00");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportRetryService reportRetryService;

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
    void 신규_재생성_요청은_202와_확정_응답을_반환한다()
            throws Exception {
        when(reportRetryService.retry(7L, 48L)).thenReturn(result(
                HttpStatus.ACCEPTED,
                ReportResponseCode.REPORT_RETRY_ACCEPTED,
                ReportStatus.PENDING,
                0,
                "RECORD_COLLECTION"
        ));

        mockMvc.perform(post(URI, 48L))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("REPORT_RETRY_ACCEPTED"))
                .andExpect(jsonPath("$.message")
                        .value("리포트 재생성을 시작했습니다."))
                .andExpect(jsonPath("$.data.reportId").value(48L))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.progressRate").value(0))
                .andExpect(jsonPath("$.data.progressStage")
                        .value("RECORD_COLLECTION"))
                .andExpect(jsonPath("$.data.retryRequestedAt").value(
                        "2026-07-25T15:30:00+09:00"
                ))
                .andExpect(jsonPath("$.data.statusApi")
                        .value("/api/v1/reports/48/status"));

        verify(reportRetryService).retry(7L, 48L);
    }

    @Test
    void 이미_진행_중인_재생성은_200과_최초_요청_시각을_반환한다()
            throws Exception {
        when(reportRetryService.retry(7L, 48L)).thenReturn(result(
                HttpStatus.OK,
                ReportResponseCode.REPORT_RETRY_ALREADY_IN_PROGRESS,
                ReportStatus.IN_PROGRESS,
                40,
                "STT_VALIDATION"
        ));

        mockMvc.perform(post(URI, 48L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(
                        "REPORT_RETRY_ALREADY_IN_PROGRESS"
                ))
                .andExpect(jsonPath("$.message").value(
                        "리포트 재생성이 이미 진행 중입니다."
                ))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.progressRate").value(40))
                .andExpect(jsonPath("$.data.progressStage")
                        .value("STT_VALIDATION"))
                .andExpect(jsonPath("$.data.retryRequestedAt").value(
                        "2026-07-25T15:30:00+09:00"
                ));
    }

    @Test
    void 리포트_ID가_올바르지_않으면_400을_반환한다()
            throws Exception {
        mockMvc.perform(post(URI, 0L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("reportId"))
                .andExpect(jsonPath("$.data.reason").value(
                        "리포트 ID는 1 이상의 숫자여야 합니다."
                ));

        verifyNoInteractions(reportRetryService);
    }

    @Test
    void 숫자가_아닌_리포트_ID도_동일한_400_계약을_반환한다()
            throws Exception {
        mockMvc.perform(post(URI, "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("reportId"))
                .andExpect(jsonPath("$.data.reason").value(
                        "리포트 ID는 1 이상의 숫자여야 합니다."
                ));

        verifyNoInteractions(reportRetryService);
    }

    @Test
    void 재생성_업무_예외를_HTTP_status로_매핑한다()
            throws Exception {
        assertMapped(ErrorCode.REPORT_RETRY_ACCESS_DENIED, 403);
        assertMapped(ErrorCode.REPORT_NOT_FOUND, 404);
        assertMapped(ErrorCode.REPORT_RETRY_NOT_ALLOWED, 409);
        assertMapped(ErrorCode.REPORT_RETRY_NOT_RETRYABLE, 409);
        assertMapped(ErrorCode.REPORT_SOURCE_DATA_INSUFFICIENT, 409);
    }

    @Test
    void 상태_충돌_응답은_문서화된_컨텍스트를_유지한다()
            throws Exception {
        when(reportRetryService.retry(7L, 48L)).thenThrow(
                new BusinessException(
                        ErrorCode.REPORT_RETRY_NOT_ALLOWED,
                        Map.of("reportId", 48L, "status", "DONE")
                )
        );

        mockMvc.perform(post(URI, 48L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_RETRY_NOT_ALLOWED"))
                .andExpect(jsonPath("$.data.reportId").value(48L))
                .andExpect(jsonPath("$.data.status").value("DONE"));
    }

    private void assertMapped(ErrorCode errorCode, int expectedStatus)
            throws Exception {
        reset(reportRetryService);
        when(reportRetryService.retry(7L, 48L))
                .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(post(URI, 48L))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.getCode()));
    }

    private ReportRetryService.RetryResult result(
            HttpStatus httpStatus,
            ReportResponseCode responseCode,
            ReportStatus reportStatus,
            int progressRate,
            String progressStage
    ) {
        return new ReportRetryService.RetryResult(
                httpStatus,
                responseCode,
                new ReportRetryResponse(
                        48L,
                        reportStatus,
                        progressRate,
                        progressStage,
                        RETRY_REQUESTED_AT,
                        "/api/v1/reports/48/status"
                )
        );
    }
}
