package com.ssafy.ssabangpalbang.study.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.study.dto.response.StudyScheduleCreateResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyScheduleDeleteResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyScheduleDetailResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyScheduleUpdateResponse;
import com.ssafy.ssabangpalbang.study.service.StudyScheduleService;
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

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudyScheduleController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StudyScheduleContractTest {

    private static final String URI = "/api/v1/studies/10/schedule";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private StudyScheduleService service;

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
    void C1_POST_성공은_201과_등록_성공_코드를_반환한다() throws Exception {
        when(service.createSchedule(eq(7L), eq(10L), any()))
                .thenReturn(createResponse());

        mvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code")
                        .value("STUDY_SCHEDULE_CREATE_SUCCESS"));
    }

    @Test
    void C2_GET_성공은_상세_성공_코드를_반환한다() throws Exception {
        when(service.getSchedule(7L, 10L)).thenReturn(detailResponse());

        mvc.perform(get(URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("STUDY_SCHEDULE_DETAIL_SUCCESS"));
    }

    @Test
    void C3_PATCH_성공은_수정_성공_코드를_반환한다() throws Exception {
        when(service.updateSchedule(eq(7L), eq(10L), any()))
                .thenReturn(updateResponse());

        mvc.perform(patch(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meetingPlace\":\"옥수역 4번 출구\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("STUDY_SCHEDULE_UPDATE_SUCCESS"));
    }

    @Test
    void C4_DELETE_성공은_삭제_성공_코드를_반환한다() throws Exception {
        when(service.deleteSchedule(7L, 10L)).thenReturn(deleteResponse());

        mvc.perform(delete(URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("STUDY_SCHEDULE_DELETE_SUCCESS"));
    }

    @Test
    void C5_에러_봉투는_최상위_code를_사용한다() throws Exception {
        when(service.getSchedule(7L, 10L))
                .thenThrow(new BusinessException(
                        ErrorCode.STUDY_SCHEDULE_ACCESS_DENIED
                ));

        mvc.perform(get(URI))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("STUDY_SCHEDULE_ACCESS_DENIED"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void C6_GET_응답의_계약_필드명이_정확하다() throws Exception {
        when(service.getSchedule(7L, 10L)).thenReturn(detailResponse());

        mvc.perform(get(URI))
                .andExpect(jsonPath("$.data.isLeader").value(true))
                .andExpect(jsonPath("$.data.canManageSchedule").value(true))
                .andExpect(jsonPath("$.data.schedule.calendarEvent.location")
                        .value("옥수역 3번 출구"));
    }

    @Test
    void C7_시각은_KST_오프셋으로_끝난다() throws Exception {
        when(service.createSchedule(eq(7L), eq(10L), any()))
                .thenReturn(createResponse());

        mvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(jsonPath("$.data.startAt", endsWith("+09:00")));
    }

    @Test
    void C8_studyId가_0이면_공통_입력_에러다() throws Exception {
        mvc.perform(get("/api/v1/studies/0/schedule"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void C9_유령_필드는_응답에_없다() throws Exception {
        when(service.getSchedule(7L, 10L)).thenReturn(detailResponse());

        mvc.perform(get(URI))
                .andExpect(jsonPath("$.data.schedule.latitude").doesNotExist())
                .andExpect(jsonPath("$.data.schedule.longitude").doesNotExist())
                .andExpect(jsonPath(
                        "$.data.schedule.meetingPlaceDetail"
                ).doesNotExist());
    }

    private String createBody() {
        return """
                {
                  "startAt": "2099-08-15T15:00:00+09:00",
                  "endAt": "2099-08-15T18:00:00+09:00",
                  "meetingPlace": "옥수역 3번 출구"
                }
                """;
    }

    private StudyScheduleCreateResponse createResponse() {
        return new StudyScheduleCreateResponse(
                1L,
                10L,
                "SCHEDULED",
                "2099-08-15T15:00:00+09:00",
                "2099-08-15T18:00:00+09:00",
                "옥수역 3번 출구",
                "2026-07-30T09:00:00+09:00",
                "2026-07-30T09:00:00+09:00"
        );
    }

    private StudyScheduleDetailResponse detailResponse() {
        return new StudyScheduleDetailResponse(
                10L,
                "검증 스터디",
                "RECRUITING",
                true,
                true,
                new StudyScheduleDetailResponse.ScheduleItem(
                        1L,
                        "SCHEDULED",
                        "2099-08-15T15:00:00+09:00",
                        "2099-08-15T18:00:00+09:00",
                        "옥수역 3번 출구",
                        new StudyScheduleDetailResponse.CalendarEvent(
                                "검증 스터디",
                                "2099-08-15T15:00:00+09:00",
                                "2099-08-15T18:00:00+09:00",
                                "옥수역 3번 출구"
                        ),
                        "2026-07-30T09:00:00+09:00",
                        "2026-07-30T09:00:00+09:00"
                )
        );
    }

    private StudyScheduleUpdateResponse updateResponse() {
        return new StudyScheduleUpdateResponse(
                1L,
                10L,
                "SCHEDULED",
                "2099-08-15T15:00:00+09:00",
                "2099-08-15T18:00:00+09:00",
                "옥수역 4번 출구",
                "2026-07-30T09:01:00+09:00"
        );
    }

    private StudyScheduleDeleteResponse deleteResponse() {
        return new StudyScheduleDeleteResponse(
                10L,
                1L,
                "CANCELED",
                "2026-07-30T09:02:00+09:00"
        );
    }
}
