package com.ssafy.ssabangpalbang.member.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.dto.request.MemberStudyStatus;
import com.ssafy.ssabangpalbang.member.dto.response.MemberStudyResponse;
import com.ssafy.ssabangpalbang.member.service.MemberFollowService;
import com.ssafy.ssabangpalbang.member.service.MemberMessageService;
import com.ssafy.ssabangpalbang.member.service.MemberService;
import com.ssafy.ssabangpalbang.member.service.MemberStudyService;
import com.ssafy.ssabangpalbang.member.service.MemberVisitCalendarService;
import com.ssafy.ssabangpalbang.member.service.MemberWithdrawalService;
import com.ssafy.ssabangpalbang.report.service.ReportService;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberRole;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@Import({
        GlobalExceptionHandler.class,
        AuthSecurityConfiguration.class
})
class MemberStudyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private MemberFollowService memberFollowService;

    @MockitoBean
    private MemberMessageService memberMessageService;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private MemberVisitCalendarService memberVisitCalendarService;

    @MockitoBean
    private MemberStudyService memberStudyService;

    @MockitoBean
    private MemberWithdrawalService memberWithdrawalService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 내_스터디를_조회하면_200과_페이지_계약을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);
        when(memberStudyService.getMyStudies(
                1L,
                MemberStudyStatus.ACTIVE,
                0,
                20
        )).thenReturn(studyPage());

        mockMvc.perform(get("/api/v1/members/me/studies")
                        .header("Authorization", "Bearer access-token")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_STUDY_LIST_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("내 스터디 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.content[0].studyId")
                        .value(10L))
                .andExpect(jsonPath("$.data.content[0].status")
                        .value("CLOSED"))
                .andExpect(jsonPath("$.data.content[0].role")
                        .value("MEMBER"))
                .andExpect(jsonPath(
                        "$.data.content[0].apartment.apartmentId"
                ).value(15L))
                .andExpect(jsonPath(
                        "$.data.content[0].nextSchedule.startAt"
                ).value("2026-07-27T15:00:00+09:00"))
                .andExpect(jsonPath("$.data.content[0].unreadChatCount")
                        .value(3))
                .andExpect(jsonPath("$.data.content[0].pendingReviewCount")
                        .value(2))
                .andExpect(jsonPath("$.data.content[0].readOnly")
                        .value(false))
                .andExpect(jsonPath(
                        "$.data.content[0].hasReturnableFieldVisit")
                        .value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1L))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(memberStudyService).getMyStudies(
                1L,
                MemberStudyStatus.ACTIVE,
                0,
                20
        );
    }

    @Test
    void 상태를_생략하면_ALL로_조회한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberStudyService.getMyStudies(
                1L,
                MemberStudyStatus.ALL,
                0,
                20
        )).thenReturn(new PageResponse<>(List.of(), 0, 0, 20, 0));

        mockMvc.perform(get("/api/v1/members/me/studies")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());

        verify(memberStudyService).getMyStudies(
                1L,
                MemberStudyStatus.ALL,
                0,
                20
        );
    }

    @Test
    void 진행중_상태를_정확한_필터로_전달한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberStudyService.getMyStudies(
                1L,
                MemberStudyStatus.IN_PROGRESS,
                0,
                20
        )).thenReturn(new PageResponse<>(List.of(), 0, 0, 20, 0));

        mockMvc.perform(get("/api/v1/members/me/studies")
                        .header("Authorization", "Bearer access-token")
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());

        verify(memberStudyService).getMyStudies(
                1L,
                MemberStudyStatus.IN_PROGRESS,
                0,
                20
        );
    }

    @Test
    void 잘못된_상태면_명세_오류를_반환한다() throws Exception {
        authenticate("access-token", 1L);

        mockMvc.perform(get("/api/v1/members/me/studies")
                        .header("Authorization", "Bearer access-token")
                        .param("status", "READY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_STUDY_STATUS_INVALID"))
                .andExpect(jsonPath("$.data.field").value("status"))
                .andExpect(jsonPath("$.data.allowedValues[0]")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.data.allowedValues[1]")
                        .value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.allowedValues[2]")
                        .value("COMPLETED"))
                .andExpect(jsonPath("$.data.allowedValues[3]")
                        .value("ALL"));

        verify(memberStudyService, never()).getMyStudies(
                anyLong(),
                any(MemberStudyStatus.class),
                anyInt(),
                anyInt()
        );
    }

    @Test
    void 잘못된_페이지_크기면_400을_반환한다() throws Exception {
        authenticate("access-token", 1L);

        mockMvc.perform(get("/api/v1/members/me/studies")
                        .header("Authorization", "Bearer access-token")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("size"));

        verify(memberStudyService, never()).getMyStudies(
                anyLong(),
                any(MemberStudyStatus.class),
                anyInt(),
                anyInt()
        );
    }

    @Test
    void Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/studies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(memberStudyService, never()).getMyStudies(
                anyLong(),
                any(MemberStudyStatus.class),
                anyInt(),
                anyInt()
        );
    }

    private void authenticate(String accessToken, Long memberId) {
        when(jwtTokenProvider.parseAccessToken(accessToken))
                .thenReturn(memberId);
    }

    private PageResponse<MemberStudyResponse> studyPage() {
        MemberStudyResponse response = new MemberStudyResponse(
                10L,
                "옥수동 주말 임장",
                "교통과 단지 환경을 확인합니다.",
                "역 접근성·경사·주변 소음을 함께 확인합니다.",
                StudyStatus.CLOSED,
                StudyMemberRole.MEMBER,
                new MemberStudyResponse.ApartmentSummary(
                        15L,
                        "래미안 옥수 리버젠"
                ),
                new MemberStudyResponse.ScheduleSummary(
                        7L,
                        OffsetDateTime.parse(
                                "2026-07-27T15:00:00+09:00"
                        ),
                        "옥수역 3번 출구"
                ),
                3,
                2,
                false,
                true
        );
        return new PageResponse<>(List.of(response), 1, 0, 20, 1);
    }
}
