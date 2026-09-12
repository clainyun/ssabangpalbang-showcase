package com.ssafy.ssabangpalbang.member.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.member.dto.response.MemberVisitCalendarResponse;
import com.ssafy.ssabangpalbang.member.service.MemberFollowService;
import com.ssafy.ssabangpalbang.member.service.MemberMessageService;
import com.ssafy.ssabangpalbang.member.service.MemberService;
import com.ssafy.ssabangpalbang.member.service.MemberStudyService;
import com.ssafy.ssabangpalbang.member.service.MemberVisitCalendarService;
import com.ssafy.ssabangpalbang.member.service.MemberWithdrawalService;
import com.ssafy.ssabangpalbang.report.service.ReportService;
import com.ssafy.ssabangpalbang.study.domain.ScheduleStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
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
class MemberVisitCalendarControllerTest {

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
    void 월별_임장_달력을_조회하면_200과_dates_계약을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);
        when(memberVisitCalendarService.getVisitCalendar(1L, 2026, 7))
                .thenReturn(calendarResponse());

        mockMvc.perform(get("/api/v1/members/me/visit-calendar")
                        .header("Authorization", "Bearer access-token")
                        .param("year", "2026")
                        .param("month", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_VISIT_CALENDAR_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("월별 임장 달력 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.year").value(2026))
                .andExpect(jsonPath("$.data.month").value(7))
                .andExpect(jsonPath("$.data.monthlyVisitCount").value(2))
                .andExpect(jsonPath("$.data.dates[0].date")
                        .value("2026-07-27"))
                .andExpect(jsonPath("$.data.dates[0].visits[0].scheduleId")
                        .value(7L))
                .andExpect(jsonPath("$.data.dates[0].visits[0].studyId")
                        .value(10L))
                .andExpect(jsonPath("$.data.dates[0].visits[0].studyTitle")
                        .value("옥수동 주말 임장"))
                .andExpect(jsonPath("$.data.dates[0].visits[0].apartmentName")
                        .value("래미안 옥수 리버젠"))
                .andExpect(jsonPath("$.data.dates[0].visits[0].startAt")
                        .value("2026-07-27T15:00:00+09:00"))
                .andExpect(jsonPath("$.data.dates[0].visits[0].scheduleStatus")
                        .value("SCHEDULED"))
                .andExpect(jsonPath("$.data.days").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(memberVisitCalendarService)
                .getVisitCalendar(1L, 2026, 7);
    }

    @Test
    void 연도_파라미터가_없으면_400을_반환한다() throws Exception {
        authenticate("access-token", 1L);

        mockMvc.perform(get("/api/v1/members/me/visit-calendar")
                        .header("Authorization", "Bearer access-token")
                        .param("month", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("year"))
                .andExpect(jsonPath("$.data.reason")
                        .value("조회할 연도는 필수 값입니다."));

        verify(memberVisitCalendarService, never())
                .getVisitCalendar(anyLong(), anyInt(), anyInt());
    }

    @Test
    void 잘못된_연도면_명세_오류를_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberVisitCalendarService.getVisitCalendar(1L, 1999, 7))
                .thenThrow(new BusinessException(
                        ErrorCode.MEMBER_VISIT_CALENDAR_YEAR_INVALID,
                        Map.of(
                                "field", "year",
                                "reason", "연도는 2000 이상 2100 이하이어야 합니다."
                        )
                ));

        mockMvc.perform(get("/api/v1/members/me/visit-calendar")
                        .header("Authorization", "Bearer access-token")
                        .param("year", "1999")
                        .param("month", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_VISIT_CALENDAR_YEAR_INVALID"))
                .andExpect(jsonPath("$.data.field").value("year"));
    }

    @Test
    void Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/visit-calendar")
                        .param("year", "2026")
                        .param("month", "7"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(memberVisitCalendarService, never())
                .getVisitCalendar(anyLong(), anyInt(), anyInt());
    }

    private void authenticate(String accessToken, Long memberId) {
        when(jwtTokenProvider.parseAccessToken(accessToken))
                .thenReturn(memberId);
    }

    private MemberVisitCalendarResponse calendarResponse() {
        return new MemberVisitCalendarResponse(
                2026,
                7,
                2,
                List.of(new MemberVisitCalendarResponse.VisitDateResponse(
                        LocalDate.of(2026, 7, 27),
                        List.of(
                                new MemberVisitCalendarResponse.VisitResponse(
                                        7L,
                                        10L,
                                        "옥수동 주말 임장",
                                        "래미안 옥수 리버젠",
                                        OffsetDateTime.parse(
                                                "2026-07-27T15:00:00+09:00"
                                        ),
                                        ScheduleStatus.SCHEDULED
                                ),
                                new MemberVisitCalendarResponse.VisitResponse(
                                        8L,
                                        11L,
                                        "성수동 저녁 임장",
                                        "성수 트리마제",
                                        OffsetDateTime.parse(
                                                "2026-07-27T19:00:00+09:00"
                                        ),
                                        ScheduleStatus.COMPLETED
                                )
                        )
                ))
        );
    }
}
