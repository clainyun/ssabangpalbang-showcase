package com.ssafy.ssabangpalbang.study.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.study.dto.request.StudyApplicationCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.request.StudyCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.response.StudyApplicationCreateResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyCreateResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyDetailResponse;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudyController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StudyContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyService studyService;

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
    void createContractKeepsElevenFieldsAndCreatedStatus() throws Exception {
        when(studyService.create(eq(7L), any(StudyCreateRequest.class)))
                .thenReturn(createResponse());

        mockMvc.perform(post("/api/v1/studies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "apartmentId": 15,
                                  "title": "옥수동 주말 임장",
                                  "intro": "소개",
                                  "goal": "목표",
                                  "capacity": 6,
                                  "purpose": "RESIDENCE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("STUDY_CREATE_SUCCESS"))
                .andExpect(jsonPath("$.data.studyId").exists())
                .andExpect(jsonPath("$.data.title").exists())
                .andExpect(jsonPath("$.data.intro").exists())
                .andExpect(jsonPath("$.data.goal").exists())
                .andExpect(jsonPath("$.data.purpose").exists())
                .andExpect(jsonPath("$.data.status").exists())
                .andExpect(jsonPath("$.data.capacity").exists())
                .andExpect(jsonPath("$.data.currentMemberCount").exists())
                .andExpect(jsonPath("$.data.apartment.apartmentId").exists())
                .andExpect(jsonPath("$.data.apartment.name").exists())
                .andExpect(jsonPath("$.data.apartment.address").exists())
                .andExpect(jsonPath("$.data.leader.memberId").exists())
                .andExpect(jsonPath("$.data.leader.nickname").exists())
                .andExpect(jsonPath("$.data.leader.selectedCharacterId").exists())
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.applicationMessageRequired").doesNotExist());
    }

    @Test
    void applicationContractKeepsSevenFieldsAndCreatedStatus() throws Exception {
        when(studyService.applyToStudy(
                eq(10L), eq(7L), any(StudyApplicationCreateRequest.class)))
                .thenReturn(applicationResponse());

        mockMvc.perform(post("/api/v1/studies/10/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intro": "실거주를 고려 중입니다.",
                                  "purpose": "RESIDENCE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("STUDY_APPLICATION_CREATE_SUCCESS"))
                .andExpect(jsonPath("$.data.applicationId").exists())
                .andExpect(jsonPath("$.data.studyId").exists())
                .andExpect(jsonPath("$.data.applicant.memberId").exists())
                .andExpect(jsonPath("$.data.applicant.nickname").exists())
                .andExpect(jsonPath("$.data.applicant.selectedCharacterId").exists())
                .andExpect(jsonPath("$.data.intro").exists())
                .andExpect(jsonPath("$.data.purpose").exists())
                .andExpect(jsonPath("$.data.status").exists())
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.applicationMessageRequired").doesNotExist());
    }

    @Test
    void memberDetailContractKeepsNestedScheduleMembersAndPermissions() throws Exception {
        when(studyService.getDetail(7L, 10L)).thenReturn(memberDetailResponse());

        mockMvc.perform(get("/api/v1/studies/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_DETAIL_SUCCESS"))
                .andExpect(jsonPath("$.data.studyId").exists())
                .andExpect(jsonPath("$.data.title").exists())
                .andExpect(jsonPath("$.data.intro").exists())
                .andExpect(jsonPath("$.data.goal").exists())
                .andExpect(jsonPath("$.data.purpose").exists())
                .andExpect(jsonPath("$.data.status").exists())
                .andExpect(jsonPath("$.data.apartment").exists())
                .andExpect(jsonPath("$.data.leader").exists())
                .andExpect(jsonPath("$.data.currentMemberCount").exists())
                .andExpect(jsonPath("$.data.capacity").exists())
                .andExpect(jsonPath("$.data.nextSchedule").exists())
                .andExpect(jsonPath("$.data.memberSummary").exists())
                .andExpect(jsonPath("$.data.myParticipationStatus").exists())
                .andExpect(jsonPath("$.data.isLeader").exists())
                .andExpect(jsonPath("$.data.isMember").exists())
                .andExpect(jsonPath("$.data.canApply").exists())
                .andExpect(jsonPath("$.data.unreadChatCount").exists())
                .andExpect(jsonPath("$.data.fieldVisitStatus").exists())
                .andExpect(jsonPath("$.data.fieldSessionId").value(100))
                .andExpect(jsonPath("$.data.report.reportId").value(48))
                .andExpect(jsonPath("$.data.report.status").value("PENDING"))
                .andExpect(jsonPath("$.data.canStartFieldVisit").exists())
                .andExpect(jsonPath("$.data.readOnly").exists())
                .andExpect(jsonPath("$.data.permissions").exists())
                .andExpect(jsonPath("$.data.nextSchedule.scheduleId").exists())
                .andExpect(jsonPath("$.data.nextSchedule.startAt").exists())
                .andExpect(jsonPath("$.data.nextSchedule.endAt").exists())
                .andExpect(jsonPath("$.data.nextSchedule.meetingPlace").exists())
                .andExpect(jsonPath("$.data.memberSummary[0].memberId").exists())
                .andExpect(jsonPath("$.data.memberSummary[0].nickname").exists())
                .andExpect(jsonPath("$.data.memberSummary[0].selectedCharacterId").exists())
                .andExpect(jsonPath("$.data.memberSummary[0].role").exists())
                .andExpect(jsonPath("$.data.permissions.canManageApplications").exists())
                .andExpect(jsonPath("$.data.permissions.canManageMembers").exists())
                .andExpect(jsonPath("$.data.permissions.canManageNotices").exists())
                .andExpect(jsonPath("$.data.permissions.canManageSchedule").exists())
                .andExpect(jsonPath("$.data.permissions.canUseChat").exists())
                .andExpect(jsonPath("$.data.permissions.canUseFieldVisit").exists())
                .andExpect(jsonPath("$.data.applicationMessageRequired").doesNotExist());
    }

    @Test
    void nonMemberDetailKeepsPrivateFieldsNull() throws Exception {
        when(studyService.getDetail(7L, 10L)).thenReturn(nonMemberDetailResponse());

        mockMvc.perform(get("/api/v1/studies/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goal").doesNotExist())
                .andExpect(jsonPath("$.data.memberSummary").doesNotExist())
                .andExpect(jsonPath("$.data.unreadChatCount").doesNotExist())
                .andExpect(jsonPath("$.data.fieldVisitStatus").doesNotExist())
                .andExpect(jsonPath("$.data.fieldSessionId").doesNotExist())
                .andExpect(jsonPath("$.data.report").doesNotExist());
    }

    private StudyCreateResponse createResponse() {
        return new StudyCreateResponse(
                10L, "옥수동 주말 임장", "소개", "목표", "RESIDENCE", "RECRUITING", 6, 1,
                new StudyCreateResponse.ApartmentSummary(15L, "아파트", "주소"),
                new StudyCreateResponse.LeaderSummary(7L, "리더", "JIPKONG"),
                time());
    }

    private StudyApplicationCreateResponse applicationResponse() {
        return new StudyApplicationCreateResponse(
                25L, 10L,
                new StudyApplicationCreateResponse.ApplicantSummary(7L, "신청자", "PALBANG"),
                "실거주를 고려 중입니다.", "RESIDENCE", "PENDING", time());
    }

    private StudyDetailResponse memberDetailResponse() {
        return detailResponse(
                "목표",
                List.of(new StudyDetailResponse.MemberSummary(
                        7L, "리더", "JIPKONG", "LEADER")),
                0L,
                "ENDED",
                true,
                true,
                false,
                new StudyDetailResponse.Permissions(true, true, true, true, true, true));
    }

    private StudyDetailResponse nonMemberDetailResponse() {
        return detailResponse(
                null,
                null,
                null,
                null,
                false,
                false,
                true,
                new StudyDetailResponse.Permissions(false, false, false, false, false, false));
    }

    private StudyDetailResponse detailResponse(
            String goal,
            List<StudyDetailResponse.MemberSummary> members,
            Long unreadChatCount,
            String fieldVisitStatus,
            boolean isLeader,
            boolean isMember,
            boolean canApply,
            StudyDetailResponse.Permissions permissions
    ) {
        return new StudyDetailResponse(
                10L, "스터디", "소개", goal, "RESIDENCE", "CLOSED",
                new StudyDetailResponse.ApartmentSummary(15L, "아파트", "주소"),
                new StudyDetailResponse.LeaderSummary(7L, "리더", "JIPKONG"),
                1, 6,
                new StudyDetailResponse.ScheduleSummary(5L, time(), time().plusHours(2), "옥수역"),
                members, isMember ? "APPROVED" : "NONE", isLeader, isMember, canApply,
                unreadChatCount, fieldVisitStatus,
                "ENDED".equals(fieldVisitStatus) ? 100L : null,
                "ENDED".equals(fieldVisitStatus)
                        ? new StudyDetailResponse.ReportSummary(48L, "PENDING")
                        : null,
                isMember, false, permissions);
    }

    private OffsetDateTime time() {
        return OffsetDateTime.of(2026, 7, 29, 15, 0, 0, 0, ZoneOffset.ofHours(9));
    }
}
