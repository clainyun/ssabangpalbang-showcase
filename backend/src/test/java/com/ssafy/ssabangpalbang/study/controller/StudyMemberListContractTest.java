package com.ssafy.ssabangpalbang.study.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.study.dto.response.StudyMemberListResponse;
import com.ssafy.ssabangpalbang.study.service.StudyMemberQueryService;
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

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudyController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StudyMemberListContractTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean StudyService studyService;
    @MockitoBean StudyMemberQueryService studyMemberQueryService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthenticatedMember(7L), null));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void responseKeepsOnlyDocumentedFields() throws Exception {
        when(studyMemberQueryService.getMembers(7L, 10L)).thenReturn(
                new StudyMemberListResponse(10L, true, 1, 6, List.of(
                        new StudyMemberListResponse.MemberItem(
                                7L, "리더", null, "PALBANG", "LEADER", false, true,
                                "2026-07-22T15:00:00+09:00"))));

        mockMvc.perform(get("/api/v1/studies/10/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_MEMBER_LIST_SUCCESS"))
                .andExpect(jsonPath("$.data.studyId").value(10))
                .andExpect(jsonPath("$.data.isLeader").value(true))
                .andExpect(jsonPath("$.data.currentMemberCount").value(1))
                .andExpect(jsonPath("$.data.capacity").value(6))
                .andExpect(jsonPath("$.data.members[0].memberId").value(7))
                .andExpect(jsonPath("$.data.members[0].nickname").value("리더"))
                .andExpect(jsonPath("$.data.members[0].profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.data.members[0].selectedCharacterId").value("PALBANG"))
                .andExpect(jsonPath("$.data.members[0].role").value("LEADER"))
                .andExpect(jsonPath("$.data.members[0].canKick").value(false))
                .andExpect(jsonPath("$.data.members[0].reviewedByMe").value(true))
                .andExpect(jsonPath("$.data.members[0].joinedAt").exists())
                .andExpect(jsonPath("$.data.members[0].ageGroup").doesNotExist())
                .andExpect(jsonPath("$.data.members[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.members[0].status").doesNotExist())
                .andExpect(jsonPath("$.data.members[0].leftAt").doesNotExist());
    }

    @Test
    void forbiddenUsesFlatEnvelope() throws Exception {
        when(studyMemberQueryService.getMembers(7L, 10L))
                .thenThrow(new BusinessException(ErrorCode.STUDY_MEMBER_LIST_FORBIDDEN));

        mockMvc.perform(get("/api/v1/studies/10/members"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDY_MEMBER_LIST_FORBIDDEN"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }
}
