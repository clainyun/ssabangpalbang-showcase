package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest;
import com.ssafy.ssabangpalbang.report.dto.response.ReportDetailResponse;
import com.ssafy.ssabangpalbang.report.service.ReportDetailService;
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
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportDetailController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReportDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportDetailService reportDetailService;

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
    void 리포트_상세_조회_성공_응답을_반환한다() throws Exception {
        when(reportDetailService.getDetail(7L, 48L))
                .thenReturn(response());

        mockMvc.perform(get("/api/v1/reports/48"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("REPORT_DETAIL_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("리포트 상세 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.reportId").value(48))
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.progressStage")
                        .value("COMPLETED"))
                .andExpect(jsonPath("$.data.apartment.apartmentId")
                        .value(100))
                .andExpect(jsonPath("$.data.study.participantCount")
                        .value(3))
                .andExpect(jsonPath("$.data.metrics.evidenceCount")
                        .value(2))
                .andExpect(jsonPath("$.data.metrics.hasAiEvidence")
                        .value(true))
                .andExpect(jsonPath(
                        "$.data.topPositiveFeatures[0].mentionRate"
                ).value(66.7))
                .andExpect(jsonPath(
                        "$.data.topPositiveFeatures[0].sourceIds[0]"
                ).value(101))
                .andExpect(jsonPath(
                        "$.data.categories[0].checklistItemCount"
                ).value(6))
                .andExpect(jsonPath(
                        "$.data.categories[0].unrecordedOpinionCount"
                ).value(1))
                .andExpect(jsonPath("$.data.viewer.isParticipant")
                        .value(true))
                .andExpect(jsonPath("$.data.favoritedByMe").value(true))
                .andExpect(jsonPath("$.data.favoriteCount").value(4))
                .andExpect(jsonPath("$.data.completedAt")
                        .value("2026-07-25T15:01:30+09:00"));

        verify(reportDetailService).getDetail(7L, 48L);
    }

    @Test
    void 리포트_ID가_0이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/reports/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("reportId"))
                .andExpect(jsonPath("$.data.reason")
                        .value("리포트 ID는 1 이상의 숫자여야 합니다."));
    }

    @Test
    void 숫자가_아닌_리포트_ID도_동일한_400_계약을_반환한다()
            throws Exception {
        mockMvc.perform(get("/api/v1/reports/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("reportId"));
    }

    @Test
    void Long_범위를_넘은_리포트_ID도_400을_반환한다()
            throws Exception {
        mockMvc.perform(get(
                        "/api/v1/reports/9223372036854775808"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void 생성_중_리포트는_상태_조회_경로를_포함한_409를_반환한다()
            throws Exception {
        when(reportDetailService.getDetail(7L, 48L))
                .thenThrow(new BusinessException(
                        ErrorCode.REPORT_NOT_DONE,
                        Map.of(
                                "reportId", 48L,
                                "status", "IN_PROGRESS",
                                "statusApi", "/api/v1/reports/48/status"
                        )
                ));

        mockMvc.perform(get("/api/v1/reports/48"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_DONE"))
                .andExpect(jsonPath("$.data.reportId").value(48))
                .andExpect(jsonPath("$.data.status")
                        .value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.statusApi")
                        .value("/api/v1/reports/48/status"));
    }

    @Test
    void 생성_실패_리포트는_재시도_가능_여부를_포함한_409를_반환한다()
            throws Exception {
        when(reportDetailService.getDetail(7L, 48L))
                .thenThrow(new BusinessException(
                        ErrorCode.REPORT_GENERATION_FAILED,
                        Map.of(
                                "reportId", 48L,
                                "retryAvailable", true
                        )
                ));

        mockMvc.perform(get("/api/v1/reports/48"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_GENERATION_FAILED"))
                .andExpect(jsonPath("$.data.reportId").value(48))
                .andExpect(jsonPath("$.data.retryAvailable").value(true));
    }

    @Test
    void 응답에_내부_회원_식별자를_노출하지_않는다() throws Exception {
        when(reportDetailService.getDetail(7L, 48L))
                .thenReturn(response());

        mockMvc.perform(get("/api/v1/reports/48"))
                .andExpect(jsonPath("$.data.memberId").doesNotExist())
                .andExpect(jsonPath("$.data.participantRefs").doesNotExist())
                .andExpect(jsonPath("$.data.resultJson").doesNotExist())
                .andExpect(jsonPath("$.data.visibility").doesNotExist())
                .andExpect(jsonPath("$.data.publicId").doesNotExist());
    }

    private ReportDetailResponse response() {
        return new ReportDetailResponse(
                48L,
                ReportStatus.DONE,
                "COMPLETED",
                "반포 자이아파트 임장 리포트",
                "교통 접근성이 좋습니다.",
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
                        "반포 실거주 임장 스터디",
                        "실거주 관점 비교",
                        OffsetDateTime.parse("2026-07-25T14:00:00+09:00"),
                        3
                ),
                new ReportDetailResponse.DetailMetrics(
                        6,
                        5,
                        83.3,
                        7,
                        2,
                        true
                ),
                List.of(new ReportDetailResponse.DetailFeature(
                        1,
                        "교통",
                        "역과 가깝다는 의견이 많았습니다.",
                        2,
                        66.7,
                        List.of(101L, 102L)
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ReportDetailResponse.DetailCategory(
                        "TRANSPORT",
                        "교통에 대한 다양한 의견입니다.",
                        6,
                        2,
                        66.7,
                        0,
                        0.0,
                        1,
                        33.3,
                        true,
                        List.of(new ReportDetailResponse
                                .DetailParticipantOpinion(
                                "참여자 1",
                                ReportGenerationResultRequest.OpinionType
                                        .POSITIVE,
                                "지하철 접근성이 좋습니다.",
                                List.of(101L)
                        ))
                )),
                new ReportDetailResponse.ViewerPermissions(
                        true,
                        true,
                        true,
                        true
                ),
                true,
                4,
                700L,
                OffsetDateTime.parse("2026-07-25T15:01:30+09:00"),
                OffsetDateTime.parse("2026-07-25T15:01:31+09:00")
        );
    }
}
