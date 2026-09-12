package com.ssafy.ssabangpalbang.study.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.study.dto.response.StudyApplicationApproveResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyApplicationListResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyApplicationRejectResponse;
import com.ssafy.ssabangpalbang.study.service.StudyApplicationDecisionService;
import com.ssafy.ssabangpalbang.study.service.StudyApplicationQueryService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudyApplicationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StudyApplicationContractTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean StudyApplicationQueryService queryService;
    @MockitoBean StudyApplicationDecisionService decisionService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthenticatedMember(7L), null));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 목록_응답이_정본_필드를_유지하고_profileImageUrl은_없다() throws Exception {
        when(queryService.getApplications(eq(7L), eq(10L), any(), any(), any(Integer.class))).thenReturn(list());

        mockMvc.perform(get("/api/v1/studies/10/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_APPLICATION_LIST_SUCCESS"))
                .andExpect(jsonPath("$.data.content[0].applicationId").exists())
                .andExpect(jsonPath("$.data.content[0].canApprove").exists())
                .andExpect(jsonPath("$.data.content[0].applicant.profileImageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.summary.pendingCount").exists())
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    void 승인과_거절_응답은_각각의_회원_필드_계약을_유지한다() throws Exception {
        when(decisionService.approve(7L, 10L, 25L)).thenReturn(approve());
        when(decisionService.reject(7L, 10L, 26L)).thenReturn(reject());

        mockMvc.perform(patch("/api/v1/studies/10/applications/25/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_APPLICATION_APPROVE_SUCCESS"))
                .andExpect(jsonPath("$.data.currentMemberCount").exists())
                .andExpect(jsonPath("$.data.applicant.profileImageUrl").exists());
        mockMvc.perform(patch("/api/v1/studies/10/applications/26/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_APPLICATION_REJECT_SUCCESS"))
                .andExpect(jsonPath("$.data.applicant.profileImageUrl").doesNotExist())
                .andExpect(jsonPath("$.error.code").doesNotExist());
    }

    @Test
    void 오류_봉투는_평탄하다() throws Exception {
        when(queryService.getApplications(eq(7L), eq(10L), any(), any(), any(Integer.class)))
                .thenThrow(new BusinessException(ErrorCode.STUDY_APPLICATION_STATUS_INVALID));

        mockMvc.perform(get("/api/v1/studies/10/applications?status=WRONG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STUDY_APPLICATION_STATUS_INVALID"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    private StudyApplicationListResponse list() {
        return new StudyApplicationListResponse(List.of(new StudyApplicationListResponse.ApplicationItem(
                25L, new StudyApplicationListResponse.Applicant(8L, "신청자", "PALBANG"),
                "소개", "RESIDENCE", "PENDING", true, true, time(), null)),
                new StudyApplicationListResponse.Summary(1, 0, 0, 1, 2), null, false);
    }

    private StudyApplicationApproveResponse approve() {
        return new StudyApplicationApproveResponse(25L, 10L,
                new StudyApplicationApproveResponse.Applicant(8L, "신청자", "https://example.com/p.png", "PALBANG"),
                "APPROVED", 2, 2, "CLOSED", time());
    }

    private StudyApplicationRejectResponse reject() {
        return new StudyApplicationRejectResponse(26L, 10L,
                new StudyApplicationRejectResponse.Applicant(8L, "신청자", "PALBANG"), "REJECTED", time());
    }

    private String time() {
        return "2026-07-30T10:00:00+09:00";
    }
}
