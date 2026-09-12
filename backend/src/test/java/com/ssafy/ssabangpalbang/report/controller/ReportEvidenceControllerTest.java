package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.report.dto.response.ReportEvidenceDetailResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportEvidenceListResponse;
import com.ssafy.ssabangpalbang.report.service.ReportEvidenceListService;
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
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportEvidenceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReportEvidenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportEvidenceListService reportEvidenceListService;

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
    void 리포트_근거_목록_성공_응답을_반환한다() throws Exception {
        when(reportEvidenceListService.getEvidenceList(
                7L,
                48L,
                "TEXT",
                "교통",
                "201,205",
                "100",
                "20"
        )).thenReturn(response());

        mockMvc.perform(get("/api/v1/reports/48/evidences")
                        .queryParam("sourceType", "TEXT")
                        .queryParam("category", "교통")
                        .queryParam("sourceIds", "201,205")
                        .queryParam("cursor", "100")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("REPORT_EVIDENCE_LIST_SUCCESS"))
                .andExpect(jsonPath("$.data.reportId").value(48))
                .andExpect(jsonPath("$.data.filters.sourceType")
                        .value("TEXT"))
                .andExpect(jsonPath("$.data.content[0].sourceId")
                        .value(201))
                .andExpect(jsonPath("$.data.content[0].participantLabel")
                        .value("참여자 1"))
                .andExpect(jsonPath("$.data.content[0].usedIn[0].claimKey")
                        .value("feature.transport"))
                .andExpect(jsonPath("$.data.summary.photoCount").value(0))
                .andExpect(jsonPath("$.data.content[0].memberId")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.content[0].participantRef")
                        .doesNotExist());

        verify(reportEvidenceListService).getEvidenceList(
                7L,
                48L,
                "TEXT",
                "교통",
                "201,205",
                "100",
                "20"
        );
    }

    @Test
    void 리포트_근거_원문은_no_store와_함께_반환한다() throws Exception {
        when(reportEvidenceListService.getEvidenceDetail(7L, 48L, 201L))
                .thenReturn(detailResponse());

        mockMvc.perform(get("/api/v1/reports/48/evidences/201"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("REPORT_EVIDENCE_DETAIL_SUCCESS"))
                .andExpect(jsonPath("$.data.reportId").value(48))
                .andExpect(jsonPath("$.data.sourceId").value(201))
                .andExpect(jsonPath("$.data.textContent")
                        .value("전체 원문\n두 번째 줄"))
                .andExpect(jsonPath("$.data.usedIn").isEmpty())
                .andExpect(jsonPath("$.data.memberId").doesNotExist());

        verify(reportEvidenceListService).getEvidenceDetail(7L, 48L, 201L);
    }

    @Test
    void sourceId가_올바르지_않으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/reports/48/evidences/zero"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("sourceId"));
    }

    @Test
    void 리포트_ID가_올바르지_않으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/reports/not-a-number/evidences"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("reportId"));
    }

    private ReportEvidenceListResponse response() {
        return new ReportEvidenceListResponse(
                48L,
                new ReportEvidenceListResponse.AppliedFilters(
                        "TEXT",
                        "교통",
                        List.of(201L, 205L)
                ),
                List.of(new ReportEvidenceListResponse.EvidenceItem(
                        201L,
                        "TEXT",
                        "교통",
                        new ReportEvidenceListResponse.ChecklistItemSummary(
                                501L,
                                "지하철역 접근성",
                                null
                        ),
                        "참여자 1",
                        "역까지 약 8분이 걸렸습니다.",
                        null,
                        false,
                        true,
                        List.of(new ReportEvidenceListResponse.UsedIn(
                                "feature.transport",
                                "TOP_POSITIVE_FEATURE",
                                "지하철 접근성"
                        )),
                        OffsetDateTime.parse(
                                "2026-07-20T14:30:00+09:00"
                        )
                )),
                new ReportEvidenceListResponse.EvidenceSummary(2, 1, 0, 1),
                201L,
                true
        );
    }

    private ReportEvidenceDetailResponse detailResponse() {
        return new ReportEvidenceDetailResponse(
                48L,
                201L,
                "TEXT",
                "교통",
                new ReportEvidenceListResponse.ChecklistItemSummary(
                        501L,
                        "지하철역 접근성",
                        null
                ),
                "참여자 1",
                "전체 원문\n두 번째 줄",
                null,
                null,
                List.of(),
                OffsetDateTime.parse("2026-07-20T14:30:00+09:00")
        );
    }
}
