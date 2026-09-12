package com.ssafy.ssabangpalbang.fieldvisit.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistDetailResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistGenerationStatusResponse;
import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistService;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChecklistController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ChecklistControllerTest {

    private static final String GENERATE_URI =
            "/api/v1/studies/{studyId}/field-visit/checklist/generate";
    private static final String DETAIL_URI =
            "/api/v1/studies/{studyId}/field-visit/checklist";
    private static final String GENERATION_STATUS_URI =
            "/api/v1/studies/{studyId}/field-visit/checklist/generate/status";
    private static final String GENERATION_ATTEMPT_HEADER =
            "X-Checklist-Generation-Attempt-Id";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChecklistService checklistService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedMember(1L),
                        null
                )
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 생성_성공은_201과_CHECKLIST_GENERATE_SUCCESS를_반환한다() throws Exception {
        when(checklistService.generate(eq(7L), eq(1L), eq("attempt-1")))
                .thenReturn(new ChecklistService.GenerateResult(
                        HttpStatus.CREATED,
                        ChecklistResponseCode.CHECKLIST_GENERATE_SUCCESS,
                        sampleGenerateBody(false)
                ));

        mockMvc.perform(post(GENERATE_URI, 7L)
                        .header(GENERATION_ATTEMPT_HEADER, "attempt-1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("CHECKLIST_GENERATE_SUCCESS"))
                .andExpect(jsonPath("$.data.checklistId").value(55))
                .andExpect(jsonPath("$.data.isFallback").value(false));
    }

    @Test
    void 이미_존재하면_200과_ALREADY_EXISTS를_반환한다() throws Exception {
        when(checklistService.generate(eq(7L), eq(1L), isNull()))
                .thenReturn(new ChecklistService.GenerateResult(
                        HttpStatus.OK,
                        ChecklistResponseCode.CHECKLIST_ALREADY_EXISTS,
                        sampleGenerateBody(false)
                ));

        mockMvc.perform(post(GENERATE_URI, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CHECKLIST_ALREADY_EXISTS"));
    }

    @Test
    void fallback_생성은_201과_FALLBACK_SUCCESS를_반환한다() throws Exception {
        when(checklistService.generate(eq(7L), eq(1L), isNull()))
                .thenReturn(new ChecklistService.GenerateResult(
                        HttpStatus.CREATED,
                        ChecklistResponseCode.CHECKLIST_FALLBACK_GENERATE_SUCCESS,
                        sampleGenerateBody(true)
                ));

        mockMvc.perform(post(GENERATE_URI, 7L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code")
                        .value("CHECKLIST_FALLBACK_GENERATE_SUCCESS"))
                .andExpect(jsonPath("$.data.isFallback").value(true));
    }

    @Test
    void 참여자_종료는_409다() throws Exception {
        when(checklistService.generate(eq(7L), eq(1L), isNull()))
                .thenThrow(new BusinessException(
                        ErrorCode.FIELD_PARTICIPANT_ALREADY_ENDED,
                        "임장을 종료한 뒤에는 체크리스트를 생성할 수 없습니다."
                ));

        mockMvc.perform(post(GENERATE_URI, 7L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("FIELD_PARTICIPANT_ALREADY_ENDED"))
                .andExpect(jsonPath("$.message")
                        .value("임장을 종료한 뒤에는 체크리스트를 생성할 수 없습니다."));
    }

    @Test
    void 조회_성공은_DETAIL_SUCCESS를_반환한다() throws Exception {
        when(checklistService.getChecklist(eq(7L), eq(1L)))
                .thenReturn(new ChecklistDetailResponse(
                        7L,
                        100L,
                        "IN_PROGRESS",
                        false,
                        null
                ));

        mockMvc.perform(get(DETAIL_URI, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CHECKLIST_DETAIL_SUCCESS"))
                .andExpect(jsonPath("$.data.checklist").isEmpty());
    }

    @Test
    void generationStatusReturnsTheLatestBackendStage() throws Exception {
        when(checklistService.getGenerationStatus(eq(7L), eq(1L), eq("attempt-1")))
                .thenReturn(new ChecklistGenerationStatusResponse(
                        "attempt-1",
                        "IN_PROGRESS",
                        55,
                        "AI_GENERATION",
                        "맞춤 체크 항목을 만들고 있어요.",
                        OffsetDateTime.of(
                                2026, 8, 4, 14, 3, 0, 0, ZoneOffset.ofHours(9)
                        )
                ));

        mockMvc.perform(get(GENERATION_STATUS_URI, 7L)
                        .header(GENERATION_ATTEMPT_HEADER, "attempt-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("CHECKLIST_GENERATION_STATUS_SUCCESS"))
                .andExpect(jsonPath("$.data.attemptId").value("attempt-1"))
                .andExpect(jsonPath("$.data.progressRate").value(55))
                .andExpect(jsonPath("$.data.progressStage").value("AI_GENERATION"));
    }

    private ChecklistGenerateResponse sampleGenerateBody(boolean fallback) {
        OffsetDateTime generatedAt = OffsetDateTime.of(
                2026, 7, 25, 14, 3, 0, 0, ZoneOffset.ofHours(9)
        );
        return new ChecklistGenerateResponse(
                7L,
                100L,
                55L,
                fallback,
                generatedAt,
                0,
                1,
                List.of(new ChecklistGenerateResponse.GenerateCategoryResponse(
                        "교통",
                        1,
                        List.of(new ChecklistGenerateResponse.GenerateItemResponse(
                                501L,
                                "지하철역 접근성",
                                "확인",
                                1,
                                false,
                                null,
                                0
                        ))
                ))
        );
    }
}
