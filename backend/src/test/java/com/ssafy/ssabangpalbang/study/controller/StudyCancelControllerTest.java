package com.ssafy.ssabangpalbang.study.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.study.dto.response.StudyCancelResponse;
import com.ssafy.ssabangpalbang.study.service.StudyCancelService;
import com.ssafy.ssabangpalbang.study.service.StudyService;
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

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudyController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StudyCancelControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean StudyService studyService;
    @MockitoBean StudyCancelService studyCancelService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedMember(7L), null));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 취소하면_200과_고정된_응답_필드를_반환한다() throws Exception {
        when(studyCancelService.cancel(7L, 10L)).thenReturn(
                new StudyCancelResponse(
                        10L, "CANCELED", "2026-07-30T15:00:00+09:00"));

        mockMvc.perform(delete("/api/v1/studies/{studyId}", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("STUDY_CANCEL_SUCCESS"))
                .andExpect(jsonPath("$.message").value("스터디가 취소되었습니다."))
                .andExpect(jsonPath("$.data.studyId").value(10L))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.canceledAt")
                        .value("2026-07-30T15:00:00+09:00"))
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.aMapWithSize(3)))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 스터디장이_아니면_403을_반환한다() throws Exception {
        when(studyCancelService.cancel(7L, 10L))
                .thenThrow(new BusinessException(ErrorCode.STUDY_CANCEL_FORBIDDEN));

        mockMvc.perform(delete("/api/v1/studies/{studyId}", 10L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDY_CANCEL_FORBIDDEN"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 취소할_수_없는_상태면_409와_현재_상태를_반환한다() throws Exception {
        when(studyCancelService.cancel(7L, 10L)).thenThrow(new BusinessException(
                ErrorCode.STUDY_CANCEL_NOT_ALLOWED,
                Map.of("studyId", 10L, "status", "IN_PROGRESS")
        ));

        mockMvc.perform(delete("/api/v1/studies/{studyId}", 10L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDY_CANCEL_NOT_ALLOWED"))
                .andExpect(jsonPath("$.data.studyId").value(10L))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }
}
