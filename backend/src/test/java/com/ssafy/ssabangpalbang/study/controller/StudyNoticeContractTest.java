package com.ssafy.ssabangpalbang.study.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.study.dto.request.StudyNoticeCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.request.StudyNoticeUpdateRequest;
import com.ssafy.ssabangpalbang.study.dto.response.StudyNoticeDeleteResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyNoticeListResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyNoticeResponse;
import com.ssafy.ssabangpalbang.study.service.StudyNoticeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudyNoticeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StudyNoticeContractTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean StudyNoticeService service;

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
    void createAndUpdateKeepFiveFields() throws Exception {
        when(service.createNotice(eq(7L), eq(10L), any(StudyNoticeCreateRequest.class)))
                .thenReturn(notice());
        when(service.updateNotice(eq(7L), eq(10L), eq(18L), any(StudyNoticeUpdateRequest.class)))
                .thenReturn(notice());

        mockMvc.perform(post("/api/v1/studies/10/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"공지\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("STUDY_NOTICE_CREATE_SUCCESS"))
                .andExpect(jsonPath("$.data.noticeId").exists())
                .andExpect(jsonPath("$.data.studyId").exists())
                .andExpect(jsonPath("$.data.content").exists())
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists());

        mockMvc.perform(patch("/api/v1/studies/10/notices/18")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_NOTICE_UPDATE_SUCCESS"));
    }

    @Test
    void listUsesFlatCursorContractWithoutLegacyFields() throws Exception {
        when(service.getNotices(7L, 10L, null, 20)).thenReturn(
                new StudyNoticeListResponse(10L, true, false, List.of(
                        new StudyNoticeListResponse.NoticeItem(
                                18L, "공지", true, true, time(), time())),
                        null, false));

        mockMvc.perform(get("/api/v1/studies/10/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_NOTICE_LIST_SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].noticeId").exists())
                .andExpect(jsonPath("$.data.content[0].canEdit").exists())
                .andExpect(jsonPath("$.data.notices").doesNotExist())
                .andExpect(jsonPath("$.data.totalElements").doesNotExist())
                .andExpect(jsonPath("$.data.totalPages").doesNotExist())
                .andExpect(jsonPath("$.data.page").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].title").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].author").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].isPinned").doesNotExist());
    }

    @Test
    void deleteReturns200AndDeletedAt() throws Exception {
        when(service.deleteNotice(7L, 10L, 18L))
                .thenReturn(new StudyNoticeDeleteResponse(10L, 18L, time()));

        mockMvc.perform(delete("/api/v1/studies/10/notices/18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_NOTICE_DELETE_SUCCESS"))
                .andExpect(jsonPath("$.data.studyId").value(10))
                .andExpect(jsonPath("$.data.noticeId").value(18))
                .andExpect(jsonPath("$.data.deletedAt").exists());
    }

    @Test
    void emptyUpdateAndInvalidSizeUseFlat400Envelope() throws Exception {
        when(service.updateNotice(eq(7L), eq(10L), eq(18L), any()))
                .thenThrow(new BusinessException(ErrorCode.STUDY_NOTICE_UPDATE_EMPTY));
        mockMvc.perform(patch("/api/v1/studies/10/notices/18")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STUDY_NOTICE_UPDATE_EMPTY"));

        mockMvc.perform(get("/api/v1/studies/10/notices?size=999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void zeroAndNegativeCursorUseFlat400Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/studies/10/notices?cursor=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/studies/10/notices?cursor=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void sizeAllowsOneHundredAndRejectsOneHundredOne() throws Exception {
        when(service.getNotices(7L, 10L, null, 100)).thenReturn(
                new StudyNoticeListResponse(10L, true, false, List.of(), null, false));

        mockMvc.perform(get("/api/v1/studies/10/notices?size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_NOTICE_LIST_SUCCESS"));
        mockMvc.perform(get("/api/v1/studies/10/notices?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void multilineContentIsAllowedAndTwoThousandOneCharactersAreRejected() throws Exception {
        when(service.createNotice(eq(7L), eq(10L), any(StudyNoticeCreateRequest.class)))
                .thenReturn(notice());
        when(service.updateNotice(eq(7L), eq(10L), eq(18L), any(StudyNoticeUpdateRequest.class)))
                .thenReturn(notice());

        mockMvc.perform(post("/api/v1/studies/10/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"첫 줄\\n둘째 줄\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/v1/studies/10/notices/18")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정 첫 줄\\n수정 둘째 줄\"}"))
                .andExpect(status().isOk());

        String tooLongBody = "{\"content\":\"" + "가".repeat(2001) + "\"}";
        mockMvc.perform(post("/api/v1/studies/10/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tooLongBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
        mockMvc.perform(patch("/api/v1/studies/10/notices/18")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tooLongBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    private StudyNoticeResponse notice() {
        return new StudyNoticeResponse(18L, 10L, "공지", time(), time());
    }

    private String time() {
        return "2026-07-24T16:00:00+09:00";
    }
}
