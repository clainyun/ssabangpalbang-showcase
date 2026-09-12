package com.ssafy.ssabangpalbang.study.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.study.dto.request.StudyUpdateRequest;
import com.ssafy.ssabangpalbang.study.dto.response.StudyMemberKickResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyMemberLeaveResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyRecruitmentCloseResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyUpdateResponse;
import com.ssafy.ssabangpalbang.study.service.StudyMemberCommandService;
import com.ssafy.ssabangpalbang.study.service.StudyRecruitmentService;
import com.ssafy.ssabangpalbang.study.service.StudyService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudyController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StudyManagementContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyService studyService;

    @MockitoBean
    private StudyRecruitmentService studyRecruitmentService;

    @MockitoBean
    private StudyMemberCommandService studyMemberCommandService;

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
    void 모집_마감_응답은_정본_필드만_반환한다() throws Exception {
        when(studyRecruitmentService.closeRecruitment(7L, 10L)).thenReturn(
                new StudyRecruitmentCloseResponse(
                        10L,
                        "CLOSED",
                        4L,
                        6,
                        2L,
                        "2026-07-30T10:00:00+09:00"
                )
        );

        mockMvc.perform(patch("/api/v1/studies/10/recruitment/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_RECRUITMENT_CLOSE_SUCCESS"))
                .andExpect(jsonPath("$.data.studyId").value(10))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.currentMemberCount").value(4))
                .andExpect(jsonPath("$.data.capacity").value(6))
                .andExpect(jsonPath("$.data.pendingApplicationCount").value(2))
                .andExpect(jsonPath("$.data.recruitmentClosedAt")
                        .value("2026-07-30T10:00:00+09:00"))
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist());

        verify(studyRecruitmentService).closeRecruitment(7L, 10L);
    }

    @Test
    void 모집_재개_응답은_RECRUITING과_null_마감시각을_반환한다() throws Exception {
        when(studyRecruitmentService.reopenRecruitment(7L, 10L)).thenReturn(
                new StudyRecruitmentCloseResponse(
                        10L,
                        "RECRUITING",
                        4L,
                        6,
                        2L,
                        null
                )
        );

        mockMvc.perform(patch("/api/v1/studies/10/recruitment/open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_RECRUITMENT_OPEN_SUCCESS"))
                .andExpect(jsonPath("$.data.studyId").value(10))
                .andExpect(jsonPath("$.data.status").value("RECRUITING"))
                .andExpect(jsonPath("$.data.currentMemberCount").value(4))
                .andExpect(jsonPath("$.data.capacity").value(6))
                .andExpect(jsonPath("$.data.pendingApplicationCount").value(2))
                .andExpect(jsonPath("$.data.recruitmentClosedAt").value(org.hamcrest.Matchers.nullValue()));

        verify(studyRecruitmentService).reopenRecruitment(7L, 10L);
    }

    @Test
    void 이미_모집_중인_스터디는_정본_409_코드를_반환한다() throws Exception {
        when(studyRecruitmentService.reopenRecruitment(7L, 10L))
                .thenThrow(new BusinessException(ErrorCode.STUDY_RECRUITMENT_ALREADY_OPEN));

        mockMvc.perform(patch("/api/v1/studies/10/recruitment/open"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDY_RECRUITMENT_ALREADY_OPEN"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void 스터디장이_아니면_모집_재개는_정본_403_코드를_반환한다() throws Exception {
        when(studyRecruitmentService.reopenRecruitment(7L, 10L))
                .thenThrow(new BusinessException(ErrorCode.STUDY_RECRUITMENT_OPEN_FORBIDDEN));

        mockMvc.perform(patch("/api/v1/studies/10/recruitment/open"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDY_RECRUITMENT_OPEN_FORBIDDEN"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void 멤버_강퇴_응답은_강퇴_후_인원수를_그대로_반환한다() throws Exception {
        when(studyMemberCommandService.kick(7L, 10L, 8L)).thenReturn(
                new StudyMemberKickResponse(
                        10L,
                        8L,
                        3L,
                        6,
                        "RECRUITING",
                        "2026-07-30T10:05:00+09:00"
                )
        );

        mockMvc.perform(delete("/api/v1/studies/10/members/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_MEMBER_KICK_SUCCESS"))
                .andExpect(jsonPath("$.data.studyId").value(10))
                .andExpect(jsonPath("$.data.memberId").value(8))
                .andExpect(jsonPath("$.data.currentMemberCount").value(3))
                .andExpect(jsonPath("$.data.capacity").value(6))
                .andExpect(jsonPath("$.data.studyStatus").value("RECRUITING"))
                .andExpect(jsonPath("$.data.kickedAt")
                        .value("2026-07-30T10:05:00+09:00"))
                .andExpect(jsonPath("$.data.status").doesNotExist());

        verify(studyMemberCommandService).kick(7L, 10L, 8L);
    }

    @Test
    void 일반_멤버_나가기_응답은_활성_인원과_시각을_반환한다() throws Exception {
        when(studyMemberCommandService.leave(7L, 10L)).thenReturn(
                new StudyMemberLeaveResponse(
                        10L,
                        7L,
                        3L,
                        6,
                        "RECRUITING",
                        "2026-08-06T10:05:00+09:00"
                )
        );

        mockMvc.perform(delete("/api/v1/studies/10/members/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_MEMBER_LEAVE_SUCCESS"))
                .andExpect(jsonPath("$.data.studyId").value(10))
                .andExpect(jsonPath("$.data.memberId").value(7))
                .andExpect(jsonPath("$.data.currentMemberCount").value(3))
                .andExpect(jsonPath("$.data.capacity").value(6))
                .andExpect(jsonPath("$.data.studyStatus").value("RECRUITING"))
                .andExpect(jsonPath("$.data.leftAt")
                        .value("2026-08-06T10:05:00+09:00"));

        verify(studyMemberCommandService).leave(7L, 10L);
    }

    @Test
    void 스터디장_나가기_시도는_409를_반환한다() throws Exception {
        when(studyMemberCommandService.leave(7L, 10L))
                .thenThrow(new BusinessException(ErrorCode.STUDY_LEADER_CANNOT_LEAVE));

        mockMvc.perform(delete("/api/v1/studies/10/members/me"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDY_LEADER_CANNOT_LEAVE"));
    }

    @Test
    void 스터디장은_제목과_목표와_소개를_수정할_수_있다() throws Exception {
        when(studyService.updateDetails(
                eq(7L), eq(10L), any(StudyUpdateRequest.class)))
                .thenReturn(new StudyUpdateResponse(10L, "새 제목", "새 소개", "새 목표"));

        mockMvc.perform(patch("/api/v1/studies/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "새 제목",
                                  "goal": "새 목표",
                                  "intro": "새 소개"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_UPDATE_SUCCESS"))
                .andExpect(jsonPath("$.data.studyId").value(10))
                .andExpect(jsonPath("$.data.title").value("새 제목"))
                .andExpect(jsonPath("$.data.goal").value("새 목표"))
                .andExpect(jsonPath("$.data.intro").value("새 소개"));

        verify(studyService).updateDetails(
                7L, 10L, new StudyUpdateRequest("새 제목", "새 목표", "새 소개"));
    }

    @Test
    void 스터디장은_제목만_수정할_수_있다() throws Exception {
        when(studyService.updateDetails(
                eq(7L), eq(10L), any(StudyUpdateRequest.class)))
                .thenReturn(new StudyUpdateResponse(10L, "새 제목", "기존 소개", "기존 목표"));

        mockMvc.perform(patch("/api/v1/studies/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "새 제목"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_UPDATE_SUCCESS"))
                .andExpect(jsonPath("$.data.title").value("새 제목"));

        verify(studyService).updateDetails(
                7L, 10L, new StudyUpdateRequest("새 제목", null, null));
    }

    @Test
    void 공백_제목은_서비스_호출_전에_400으로_거부한다() throws Exception {
        mockMvc.perform(patch("/api/v1/studies/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));

        verify(studyService, never()).updateDetails(
                eq(7L), eq(10L), any(StudyUpdateRequest.class));
    }

    @Test
    void 길이_제한을_넘은_제목은_서비스_호출_전에_400으로_거부한다() throws Exception {
        mockMvc.perform(patch("/api/v1/studies/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + "가".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));

        verify(studyService, never()).updateDetails(
                eq(7L), eq(10L), any(StudyUpdateRequest.class));
    }

    @Test
    void 일반_멤버의_목표와_소개_수정은_403을_반환한다() throws Exception {
        when(studyService.updateDetails(
                eq(7L), eq(10L), any(StudyUpdateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.STUDY_UPDATE_FORBIDDEN));

        mockMvc.perform(patch("/api/v1/studies/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goal": "새 목표"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDY_UPDATE_FORBIDDEN"));
    }

    @Test
    void 공백_목표는_서비스_호출_전에_400으로_거부한다() throws Exception {
        mockMvc.perform(patch("/api/v1/studies/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goal": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));

        verify(studyService, never()).updateDetails(
                eq(7L), eq(10L), any(StudyUpdateRequest.class));
    }

    @Test
    void 스터디장_강퇴_시도는_정본_400_코드를_반환한다() throws Exception {
        when(studyMemberCommandService.kick(7L, 10L, 7L))
                .thenThrow(new BusinessException(ErrorCode.STUDY_LEADER_CANNOT_BE_KICKED));

        mockMvc.perform(delete("/api/v1/studies/10/members/7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STUDY_LEADER_CANNOT_BE_KICKED"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void 이미_마감된_스터디는_정본_409_코드를_반환한다() throws Exception {
        when(studyRecruitmentService.closeRecruitment(7L, 10L))
                .thenThrow(new BusinessException(ErrorCode.STUDY_RECRUITMENT_ALREADY_CLOSED));

        mockMvc.perform(patch("/api/v1/studies/10/recruitment/close"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDY_RECRUITMENT_ALREADY_CLOSED"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }
}
