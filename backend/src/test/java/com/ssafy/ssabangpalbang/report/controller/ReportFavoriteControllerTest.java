package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.report.dto.response.ReportFavoriteResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportFavoriteResult;
import com.ssafy.ssabangpalbang.report.dto.response.ReportResponseCode;
import com.ssafy.ssabangpalbang.report.dto.response.ReportUnfavoriteResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportUnfavoriteResult;
import com.ssafy.ssabangpalbang.report.service.ReportFavoriteService;
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
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportFavoriteController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReportFavoriteControllerTest {

    private static final OffsetDateTime FAVORITED_AT = OffsetDateTime.parse(
            "2026-07-28T15:30:00+09:00"
    );
    private static final OffsetDateTime UNFAVORITED_AT = OffsetDateTime.parse(
            "2026-07-30T10:15:00+09:00"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportFavoriteService reportFavoriteService;

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
    void 리포트_찜_성공_응답을_반환한다() throws Exception {
        stubSuccess(ReportResponseCode.REPORT_FAVORITE_SUCCESS);

        mockMvc.perform(put("/api/v1/reports/48/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("REPORT_FAVORITE_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("리포트를 찜했습니다."))
                .andExpect(jsonPath("$.data.reportId").value(48))
                .andExpect(jsonPath("$.data.favoritedByMe").value(true))
                .andExpect(jsonPath("$.data.favoriteCount").value(3))
                .andExpect(jsonPath("$.data.favoritedAt")
                        .value("2026-07-28T15:30:00+09:00"));
    }

    @Test
    void 중복_찜은_이미_찜한_성공_응답을_반환한다() throws Exception {
        stubSuccess(ReportResponseCode.REPORT_FAVORITE_ALREADY_EXISTS);

        mockMvc.perform(put("/api/v1/reports/48/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_FAVORITE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message")
                        .value("이미 찜한 리포트입니다."))
                .andExpect(jsonPath("$.data.favoritedAt")
                        .value("2026-07-28T15:30:00+09:00"));
    }

    @Test
    void 리포트_ID가_올바르지_않으면_400을_반환한다() throws Exception {
        mockMvc.perform(put("/api/v1/reports/0/favorite"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("reportId"))
                .andExpect(jsonPath("$.data.reason")
                        .value("리포트 ID는 1 이상의 숫자여야 합니다."));
    }

    @Test
    void 존재하지_않는_리포트는_404를_반환한다() throws Exception {
        when(reportFavoriteService.addFavorite(7L, 999L))
                .thenThrow(new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        mockMvc.perform(put("/api/v1/reports/999/favorite"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    void 완료되지_않은_리포트는_409와_상태를_반환한다()
            throws Exception {
        when(reportFavoriteService.addFavorite(7L, 48L))
                .thenThrow(new BusinessException(
                        ErrorCode.REPORT_FAVORITE_NOT_ALLOWED,
                        Map.of("status", "IN_PROGRESS")
                ));

        mockMvc.perform(put("/api/v1/reports/48/favorite"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_FAVORITE_NOT_ALLOWED"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    void 접근할_수_없는_리포트는_403을_반환한다() throws Exception {
        when(reportFavoriteService.addFavorite(7L, 48L))
                .thenThrow(new BusinessException(
                        ErrorCode.REPORT_FAVORITE_ACCESS_DENIED
                ));

        mockMvc.perform(put("/api/v1/reports/48/favorite"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_FAVORITE_ACCESS_DENIED"));
    }

    @Test
    void 응답에_내부_식별자를_노출하지_않는다() throws Exception {
        stubSuccess(ReportResponseCode.REPORT_FAVORITE_SUCCESS);

        mockMvc.perform(put("/api/v1/reports/48/favorite"))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.memberId").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").doesNotExist());
    }

    @Test
    void 리포트_찜_해제_성공_응답을_반환한다() throws Exception {
        stubUnfavorite(ReportResponseCode.REPORT_UNFAVORITE_SUCCESS);

        mockMvc.perform(delete("/api/v1/reports/48/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("REPORT_UNFAVORITE_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("리포트 찜을 해제했습니다."))
                .andExpect(jsonPath("$.data.reportId").value(48))
                .andExpect(jsonPath("$.data.favoritedByMe").value(false))
                .andExpect(jsonPath("$.data.favoriteCount").value(2))
                .andExpect(jsonPath("$.data.unfavoritedAt")
                        .value("2026-07-30T10:15:00+09:00"))
                .andExpect(jsonPath("$.data.favoritedAt").doesNotExist());
    }

    @Test
    void 이미_해제된_찜은_현재_상태를_성공으로_반환한다()
            throws Exception {
        stubUnfavorite(ReportResponseCode.REPORT_ALREADY_UNFAVORITED);

        mockMvc.perform(delete("/api/v1/reports/48/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_ALREADY_UNFAVORITED"))
                .andExpect(jsonPath("$.message")
                        .value("이미 찜하지 않은 리포트입니다."))
                .andExpect(jsonPath("$.data.favoritedByMe").value(false));
    }

    @Test
    void 찜_해제할_리포트_ID가_올바르지_않으면_400을_반환한다()
            throws Exception {
        mockMvc.perform(delete("/api/v1/reports/0/favorite"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("reportId"))
                .andExpect(jsonPath("$.data.reason")
                        .value("리포트 ID는 1 이상의 숫자여야 합니다."));
    }

    @Test
    void 찜_해제할_리포트가_없으면_404를_반환한다()
            throws Exception {
        when(reportFavoriteService.removeFavorite(7L, 999L))
                .thenThrow(new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        mockMvc.perform(delete("/api/v1/reports/999/favorite"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    private void stubSuccess(ReportResponseCode responseCode) {
        when(reportFavoriteService.addFavorite(7L, 48L))
                .thenReturn(new ReportFavoriteResult(
                        responseCode,
                        new ReportFavoriteResponse(
                                48L,
                                true,
                                3L,
                                FAVORITED_AT
                        )
                ));
    }

    private void stubUnfavorite(ReportResponseCode responseCode) {
        when(reportFavoriteService.removeFavorite(7L, 48L))
                .thenReturn(new ReportUnfavoriteResult(
                        responseCode,
                        new ReportUnfavoriteResponse(
                                48L,
                                false,
                                2L,
                                UNFAVORITED_AT
                        )
                ));
    }
}
