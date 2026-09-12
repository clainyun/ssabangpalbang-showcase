package com.ssafy.ssabangpalbang.fieldvisit.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistAnswerResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldRecordResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldRecordUpdateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistAnswerSaveResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldRecordCreateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldRecordListResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldRecordMutationResponse;
import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistAnswerService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldRecordService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ChecklistAnswerController.class, FieldRecordController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FieldVisitBe015ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChecklistAnswerService checklistAnswerService;

    @MockitoBean
    private FieldRecordService fieldRecordService;

    @BeforeEach
    void setUp() {
        AuthenticatedMember member = new AuthenticatedMember(42L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member, null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void answers_저장은_200을_반환한다() throws Exception {
        when(checklistAnswerService.saveAnswers(eq(7L), eq(42L), any()))
                .thenReturn(new ChecklistAnswerService.SaveResult(
                        HttpStatus.OK,
                        ChecklistAnswerResponseCode.CHECKLIST_COMPLETION_SAVE_SUCCESS,
                        new ChecklistAnswerSaveResponse(7L, 55L, 1, 1, 2, List.of(), List.of())
                ));

        mockMvc.perform(put("/api/v1/studies/7/field-visit/checklist/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"checklistItemId\":501,\"isCompleted\":true}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CHECKLIST_COMPLETION_SAVE_SUCCESS"));
    }

    @Test
    void records_생성은_201을_반환한다() throws Exception {
        when(fieldRecordService.create(eq(7L), eq(42L), any()))
                .thenReturn(new FieldRecordService.CreateResult(
                        HttpStatus.CREATED,
                        FieldRecordResponseCode.FIELD_RECORD_CREATE_SUCCESS,
                        new FieldRecordCreateResponse(
                                7L,
                                100L,
                                new FieldRecordCreateResponse.RecordBody(
                                        1L, 501L, "TEXT", "hi", null, null,
                                        new FieldRecordListResponse.Author(42L, "me", "PALBANG"),
                                        true, true, true,
                                        "11111111-1111-1111-1111-111111111111",
                                        null, null
                                ),
                                1
                        )
                ));

        mockMvc.perform(post("/api/v1/studies/7/field-visit/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "checklistItemId":501,
                                  "sourceType":"TEXT",
                                  "textContent":"hi",
                                  "clientRequestId":"11111111-1111-1111-1111-111111111111"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("FIELD_RECORD_CREATE_SUCCESS"))
                .andExpect(jsonPath("$.data.record.sourceId").value(1))
                .andExpect(jsonPath("$.data.record.canEdit").value(true));
    }

    @Test
    void records_목록은_200을_반환한다() throws Exception {
        when(fieldRecordService.list(eq(7L), eq(42L), any(), any(), any(), any(), any()))
                .thenReturn(new FieldRecordListResponse(7L, 100L, false, null, List.of(), null, false));

        mockMvc.perform(get("/api/v1/studies/7/field-visit/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FIELD_RECORD_LIST_SUCCESS"));
    }

    @Test
    void records_수정은_200을_반환한다() throws Exception {
        when(fieldRecordService.update(eq(7L), eq(42L), eq(9L), any(FieldRecordUpdateRequest.class)))
                .thenReturn(new FieldRecordMutationResponse(
                        7L, 100L, 9L, 501L,
                        new FieldRecordListResponse.Author(42L, "me", "PALBANG"),
                        "TEXT", "updated", null, null,
                        "11111111-1111-1111-1111-111111111111",
                        false, null, 1, null, null
                ));

        mockMvc.perform(patch("/api/v1/studies/7/field-visit/records/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"textContent\":\"updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FIELD_RECORD_UPDATE_SUCCESS"))
                .andExpect(jsonPath("$.data.sourceId").value(9));
    }

    @Test
    void records_삭제는_200을_반환한다() throws Exception {
        when(fieldRecordService.delete(eq(7L), eq(42L), eq(9L)))
                .thenReturn(new FieldRecordService.MutationResult(
                        HttpStatus.OK,
                        FieldRecordResponseCode.FIELD_RECORD_DELETE_SUCCESS,
                        new FieldRecordMutationResponse(
                                7L, 100L, 9L, 501L,
                                new FieldRecordListResponse.Author(42L, "me", "PALBANG"),
                                "TEXT", null, null, null,
                                "11111111-1111-1111-1111-111111111111",
                                true, null, 0, null, null
                        )
                ));

        mockMvc.perform(delete("/api/v1/studies/7/field-visit/records/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FIELD_RECORD_DELETE_SUCCESS"));
    }

    @Test
    void answers_빈_배열은_400을_반환한다() throws Exception {
        mockMvc.perform(put("/api/v1/studies/7/field-visit/checklist/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void TEXT_공백_PATCH는_서비스에서_거부되도록_요청을_전달한다() throws Exception {
        when(fieldRecordService.update(eq(7L), eq(42L), eq(9L), any()))
                .thenThrow(new com.ssafy.ssabangpalbang.global.error.BusinessException(
                        com.ssafy.ssabangpalbang.global.error.ErrorCode.FIELD_RECORD_PAYLOAD_INVALID
                ));

        mockMvc.perform(patch("/api/v1/studies/7/field-visit/records/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"textContent\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
