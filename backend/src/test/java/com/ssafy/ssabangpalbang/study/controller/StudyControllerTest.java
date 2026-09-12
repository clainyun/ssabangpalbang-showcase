package com.ssafy.ssabangpalbang.study.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.study.dto.request.StudyCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.request.StudyApplicationCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.response.StudyApplicationCreateResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyCreateResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyDetailResponse;
import com.ssafy.ssabangpalbang.study.service.StudyService;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(StudyController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StudyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyService studyService;

    @Test
    void swaggerTagIncludesCreateApplicationAndDetailScope() {
        Tag tag = StudyController.class.getAnnotation(Tag.class);

        assertThat(tag.name()).isEqualTo("스터디");
        assertThat(tag.description()).isEqualTo("임장 스터디 생성·신청과 상세 조회");
    }

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
    void createsStudyWith201AndExpectedResponse() throws Exception {
        when(studyService.create(eq(7L), any(StudyCreateRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/v1/studies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("STUDY_CREATE_SUCCESS"))
                .andExpect(jsonPath("$.data.studyId").value(10L))
                .andExpect(jsonPath("$.data.apartment.apartmentId").value(15L))
                .andExpect(jsonPath("$.data.leader.nickname").value("집보는다람쥐"))
                .andExpect(jsonPath("$.data.leader.selectedCharacterId").value("JIPKONG"))
                .andExpect(jsonPath("$.data.createdAt").value(
                        org.hamcrest.Matchers.endsWith("+09:00")))
                .andExpect(jsonPath("$.data.schedule").doesNotExist())
                .andExpect(jsonPath("$.data.startAt").doesNotExist())
                .andExpect(jsonPath("$.data.meetingPlace").doesNotExist());
    }

    @Test
    void rejectsBlankTitleAndGoal() throws Exception {
        assertCommonInvalid(request(" ", "목표", 6, "RESIDENCE"));
        assertCommonInvalid(request("제목", " ", 6, "RESIDENCE"));
    }

    @Test
    void returnsCapacityError() throws Exception {
        when(studyService.create(eq(7L), any(StudyCreateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.STUDY_CAPACITY_INVALID));

        mockMvc.perform(post("/api/v1/studies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("제목", "목표", 1, "RESIDENCE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STUDY_CAPACITY_INVALID"));
    }

    @Test
    void returnsPurposeError() throws Exception {
        when(studyService.create(eq(7L), any(StudyCreateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.STUDY_PURPOSE_INVALID));

        mockMvc.perform(post("/api/v1/studies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("제목", "목표", 6, "UNKNOWN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STUDY_PURPOSE_INVALID"));
    }

    @Test
    void returnsApartmentNotFoundWithNullData() throws Exception {
        when(studyService.create(eq(7L), any(StudyCreateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.APARTMENT_NOT_FOUND));

        mockMvc.perform(post("/api/v1/studies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APARTMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void returnsStudyDetailWithExpectedContract() throws Exception {
        when(studyService.getDetail(7L, 10L)).thenReturn(detailResponse());

        mockMvc.perform(get("/api/v1/studies/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STUDY_DETAIL_SUCCESS"))
                .andExpect(jsonPath("$.data.apartment.apartmentId").value(15L))
                .andExpect(jsonPath("$.data.leader.nickname").value("리더"))
                .andExpect(jsonPath("$.data.nextSchedule.scheduleId").value(5L))
                .andExpect(jsonPath("$.data.nextSchedule.startAt")
                        .value(org.hamcrest.Matchers.endsWith("+09:00")))
                .andExpect(jsonPath("$.data.permissions.canUseChat").value(true))
                .andExpect(jsonPath("$.data.applicationMessageRequired").doesNotExist())
                .andExpect(jsonPath("$.data.latestNotice").doesNotExist())
                .andExpect(jsonPath("$.data.hasNewNotice").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").doesNotExist());
    }

    @Test
    void returnsStudyNotFoundWithNullData() throws Exception {
        when(studyService.getDetail(7L, 999L))
                .thenThrow(new BusinessException(ErrorCode.STUDY_NOT_FOUND));
        mockMvc.perform(get("/api/v1/studies/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDY_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void rejectsNonNumericStudyId() throws Exception {
        mockMvc.perform(get("/api/v1/studies/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsStudyApplicationWith201AndExpectedContract() throws Exception {
        when(studyService.applyToStudy(
                eq(10L), eq(7L), any(StudyApplicationCreateRequest.class)))
                .thenReturn(applicationResponse());

        mockMvc.perform(post("/api/v1/studies/10/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationRequest("  실거주를 고려 중입니다.  ", "RESIDENCE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("STUDY_APPLICATION_CREATE_SUCCESS"))
                .andExpect(jsonPath("$.data.applicationId").value(25L))
                .andExpect(jsonPath("$.data.studyId").value(10L))
                .andExpect(jsonPath("$.data.applicant.memberId").value(7L))
                .andExpect(jsonPath("$.data.applicant.nickname").value("신청자"))
                .andExpect(jsonPath("$.data.applicant.selectedCharacterId").value("PALBANG"))
                .andExpect(jsonPath("$.data.intro").value("실거주를 고려 중입니다."))
                .andExpect(jsonPath("$.data.purpose").value("RESIDENCE"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.createdAt").value(
                        org.hamcrest.Matchers.endsWith("+09:00")))
                .andExpect(jsonPath("$.data.decidedAt").doesNotExist())
                .andExpect(jsonPath("$.data.applicant.email").doesNotExist())
                .andExpect(jsonPath("$.data.applicant.profileImageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.applicant.ageGroup").doesNotExist());

        verify(studyService).applyToStudy(
                eq(10L),
                eq(7L),
                argThat(request -> request.intro().equals("실거주를 고려 중입니다."))
        );
    }

    @Test
    void rejectsInvalidApplicationBody() throws Exception {
        assertApplicationCommonInvalid(applicationRequest(" ", "RESIDENCE"));
        assertApplicationCommonInvalid(applicationRequest("소개", " "));
        assertApplicationCommonInvalid(applicationRequest("가".repeat(201), "STUDY"));
    }

    @Test
    void acceptsApplicationIntroWithExactly200CharactersAfterTrim() throws Exception {
        when(studyService.applyToStudy(
                eq(10L), eq(7L), any(StudyApplicationCreateRequest.class)))
                .thenReturn(applicationResponse());

        mockMvc.perform(post("/api/v1/studies/10/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationRequest(
                                "  " + "가".repeat(200) + "  ",
                                "STUDY")))
                .andExpect(status().isCreated());

        verify(studyService).applyToStudy(
                eq(10L),
                eq(7L),
                argThat(request -> request.intro().length() == 200)
        );
    }

    @Test
    void rejectsApplicationBodyWithMissingFields() throws Exception {
        assertApplicationCommonInvalid("""
                {
                  "purpose": "RESIDENCE"
                }
                """);
        assertApplicationCommonInvalid("""
                {
                  "intro": "소개"
                }
                """);
    }

    @Test
    void mapsApplicationPurposeError() throws Exception {
        when(studyService.applyToStudy(
                eq(10L), eq(7L), any(StudyApplicationCreateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.STUDY_APPLICATION_PURPOSE_INVALID));

        mockMvc.perform(post("/api/v1/studies/10/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationRequest("소개", "UNKNOWN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("STUDY_APPLICATION_PURPOSE_INVALID"));
    }

    @Test
    void mapsApplicationConflictErrors() throws Exception {
        ErrorCode[] errors = {
                ErrorCode.STUDY_APPLICATION_ALREADY_EXISTS,
                ErrorCode.STUDY_ALREADY_MEMBER,
                ErrorCode.STUDY_APPLICATION_NOT_ALLOWED,
                ErrorCode.STUDY_CAPACITY_FULL
        };
        for (ErrorCode error : errors) {
            when(studyService.applyToStudy(
                    eq(10L), eq(7L), any(StudyApplicationCreateRequest.class)))
                    .thenThrow(new BusinessException(error));

            mockMvc.perform(post("/api/v1/studies/10/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(applicationRequest("소개", "STUDY")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(error.getCode()));
        }
    }

    @Test
    void mapsApplicationNotFoundErrors() throws Exception {
        ErrorCode[] errors = {
                ErrorCode.MEMBER_NOT_FOUND,
                ErrorCode.STUDY_NOT_FOUND
        };
        for (ErrorCode error : errors) {
            when(studyService.applyToStudy(
                    eq(10L), eq(7L), any(StudyApplicationCreateRequest.class)))
                    .thenThrow(new BusinessException(error));

            mockMvc.perform(post("/api/v1/studies/10/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(applicationRequest("소개", "STUDY")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(error.getCode()));
        }
    }

    private void assertCommonInvalid(String content) throws Exception {
        mockMvc.perform(post("/api/v1/studies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    private void assertApplicationCommonInvalid(String content) throws Exception {
        mockMvc.perform(post("/api/v1/studies/10/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    private String applicationRequest(String intro, String purpose) {
        return """
                {
                  "intro": "%s",
                  "purpose": "%s"
                }
                """.formatted(intro, purpose);
    }

    private String validRequest() {
        return request("옥수동 주말 임장", "목표", 6, "RESIDENCE");
    }

    private String request(String title, String goal, int capacity, String purpose) {
        return """
                {
                  "apartmentId": 15,
                  "title": "%s",
                  "intro": "소개",
                  "goal": "%s",
                  "capacity": %d,
                  "purpose": "%s"
                }
                """.formatted(title, goal, capacity, purpose);
    }

    private StudyCreateResponse response() {
        return new StudyCreateResponse(
                10L,
                "옥수동 주말 임장",
                "소개",
                "목표",
                "RESIDENCE",
                "RECRUITING",
                6,
                1,
                new StudyCreateResponse.ApartmentSummary(
                        15L, "래미안 옥수 리버젠", "서울특별시 성동구 매봉길 15"),
                new StudyCreateResponse.LeaderSummary(
                        7L, "집보는다람쥐", "JIPKONG"),
                OffsetDateTime.of(2026, 7, 24, 18, 0, 0, 0, ZoneOffset.ofHours(9))
        );
    }

    private StudyDetailResponse detailResponse() {
        return new StudyDetailResponse(
                10L, "스터디", "소개", "목표", "RESIDENCE", "CLOSED",
                new StudyDetailResponse.ApartmentSummary(15L, "아파트", "주소"),
                new StudyDetailResponse.LeaderSummary(7L, "리더", "JIPKONG"),
                1, 6,
                new StudyDetailResponse.ScheduleSummary(
                        5L,
                        OffsetDateTime.of(2026, 7, 27, 15, 0, 0, 0, ZoneOffset.ofHours(9)),
                        null, "옥수역"),
                List.of(new StudyDetailResponse.MemberSummary(7L, "리더", "JIPKONG", "LEADER")),
                "APPROVED", true, true, false, 3L, "NOT_STARTED", null, null,
                true, false,
                new StudyDetailResponse.Permissions(true, true, true, true, true, true)
        );
    }

    private StudyApplicationCreateResponse applicationResponse() {
        return new StudyApplicationCreateResponse(
                25L,
                10L,
                new StudyApplicationCreateResponse.ApplicantSummary(
                        7L, "신청자", "PALBANG"),
                "실거주를 고려 중입니다.",
                "RESIDENCE",
                "PENDING",
                OffsetDateTime.of(2026, 7, 24, 18, 10, 0, 0, ZoneOffset.ofHours(9))
        );
    }
}
