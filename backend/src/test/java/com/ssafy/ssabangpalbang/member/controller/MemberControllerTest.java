package com.ssafy.ssabangpalbang.member.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.dto.request.MemberProfileUpdateRequest;
import com.ssafy.ssabangpalbang.member.dto.request.MemberWithdrawalRequest;
import com.ssafy.ssabangpalbang.member.dto.request.MemberMessageSendRequest;
import com.ssafy.ssabangpalbang.member.dto.request.MemberPublicProfileSection;
import com.ssafy.ssabangpalbang.member.dto.request.MemberPublicStudyStatus;
import com.ssafy.ssabangpalbang.member.dto.request.OnboardingRequest;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFollowResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFollowResult;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFollowingListResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFollowingResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberMessageSendResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberMessageSendResult;
import com.ssafy.ssabangpalbang.member.dto.response.MemberProfileResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberProfileUpdateResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberPublicProfileResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberUnfollowResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberUnfollowResult;
import com.ssafy.ssabangpalbang.member.dto.response.MemberWithdrawalResponse;
import com.ssafy.ssabangpalbang.member.dto.response.OnboardingResponse;
import com.ssafy.ssabangpalbang.member.dto.response.PublicProfileStudyResponse;
import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;
import com.ssafy.ssabangpalbang.member.service.MemberFollowService;
import com.ssafy.ssabangpalbang.member.service.MemberMessageService;
import com.ssafy.ssabangpalbang.member.service.MemberService;
import com.ssafy.ssabangpalbang.member.service.MemberStudyService;
import com.ssafy.ssabangpalbang.member.service.MemberVisitCalendarService;
import com.ssafy.ssabangpalbang.member.service.MemberWithdrawalService;
import com.ssafy.ssabangpalbang.report.dto.response.MemberReportResponse;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewSummaryResponse;
import com.ssafy.ssabangpalbang.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@Import({
        GlobalExceptionHandler.class,
        AuthSecurityConfiguration.class
})
class MemberControllerTest {

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
    void 회원을_탈퇴하면_200과_명세_응답을_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberWithdrawalService.withdraw(
                eq(1L),
                any(MemberWithdrawalRequest.class)
        )).thenReturn(new MemberWithdrawalResponse(
                1L,
                OffsetDateTime.parse("2026-07-30T15:45:00+09:00")
        ));

        mockMvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token",
                                  "confirmationText": "회원탈퇴"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_WITHDRAW_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("회원 탈퇴가 완료되었습니다."))
                .andExpect(jsonPath("$.data.memberId").value(1L))
                .andExpect(jsonPath("$.data.withdrawnAt")
                        .value("2026-07-30T15:45:00+09:00"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 탈퇴_확인_문구가_다르면_명세의_400을_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberWithdrawalService.withdraw(
                eq(1L),
                any(MemberWithdrawalRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.MEMBER_WITHDRAW_CONFIRMATION_INVALID,
                Map.of(
                        "field", "confirmationText",
                        "expectedValue", "회원탈퇴"
                )
        ));

        mockMvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token",
                                  "confirmationText": "탈퇴"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_WITHDRAW_CONFIRMATION_INVALID"))
                .andExpect(jsonPath("$.data.field")
                        .value("confirmationText"))
                .andExpect(jsonPath("$.data.expectedValue")
                        .value("회원탈퇴"));
    }

    @Test
    void Access_Token_없이_회원_탈퇴를_요청하면_401이다() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token",
                                  "confirmationText": "회원탈퇴"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verify(memberWithdrawalService, never()).withdraw(any(), any());
    }

    @Test
    void 내_프로필을_조회하면_200과_명세_응답을_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberService.getMyProfile(1L)).thenReturn(profileResponse());

        mockMvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_PROFILE_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("내 프로필 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.memberId").value(1L))
                .andExpect(jsonPath("$.data.email")
                        .value("dain@example.com"))
                .andExpect(jsonPath("$.data.selectedCharacterId")
                        .value("PALBANG_RABBIT"))
                .andExpect(jsonPath("$.data.preference.maritalStatus")
                        .value("MARRIED"))
                .andExpect(jsonPath("$.data.preference.hasVehicle")
                        .value(true))
                .andExpect(jsonPath("$.data.preference.hasChildren")
                        .value(false))
                .andExpect(jsonPath("$.data.preference.householdType")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.preference.budget")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.preference.interestRegion")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.data.preference.interestRegionPublicAgreed"
                ).doesNotExist())
                .andExpect(jsonPath("$.data.preference.priorities[2]")
                        .value("PARKING"))
                .andExpect(jsonPath("$.data.onboardingCompleted")
                        .value(true))
                .andExpect(jsonPath("$.data.joinedDays").value(32L))
                .andExpect(jsonPath("$.data.summary.studyCount").value(3L))
                .andExpect(jsonPath("$.data.summary.reportCount").value(2L))
                .andExpect(jsonPath("$.data.summary.followingCount").value(5L))
                .andExpect(jsonPath("$.data.fieldVisitCompletedCount")
                        .value(7L))
                .andExpect(jsonPath("$.data.reviewSummary.topTags[0].code")
                        .value("PUNCTUAL"))
                .andExpect(jsonPath("$.data.reviewSummary.likeReceivedCount")
                        .value(9L))
                .andExpect(jsonPath("$.data.reviewSummary.reviewCount")
                        .value(12L))
                .andExpect(jsonPath("$.data.reviewSummary.averageRating")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.createdAt")
                        .value("2026-06-26T00:00:00+09:00"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(memberService).getMyProfile(1L);
    }

    @Test
    void 내_프로필_조회에_Access_Token이_없으면_401을_반환한다()
            throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(memberService, never()).getMyProfile(any());
    }

    @Test
    void 탈퇴_회원의_내_프로필_조회는_403을_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberService.getMyProfile(1L)).thenThrow(
                new BusinessException(ErrorCode.AUTH_MEMBER_WITHDRAWN)
        );

        mockMvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_MEMBER_WITHDRAWN"));
    }

    @Test
    void 존재하지_않는_회원의_내_프로필_조회는_404를_반환한다()
            throws Exception {
        authenticate("access-token", 99L);
        when(memberService.getMyProfile(99L)).thenThrow(
                new BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        );

        mockMvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 다른_사용자_공개_프로필을_조회하면_200과_명세_응답을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);
        when(memberService.getPublicProfile(
                1L,
                12L,
                MemberPublicProfileSection.STUDIES,
                MemberPublicStudyStatus.ACTIVE,
                0,
                20
        ))
                .thenReturn(publicProfileResponse());

        mockMvc.perform(get("/api/v1/members/12")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_PUBLIC_PROFILE_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("사용자 프로필 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.memberId").value(12L))
                .andExpect(jsonPath("$.data.nickname").value("옥수탐방러"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.ageGroup").value("TWENTIES"))
                .andExpect(jsonPath("$.data.interestRegion")
                        .value("서울특별시 성동구"))
                .andExpect(jsonPath("$.data.participatingStudyCount")
                        .value(4L))
                .andExpect(jsonPath("$.data.reportCount").value(3L))
                .andExpect(jsonPath("$.data.followingCount").value(5L))
                .andExpect(jsonPath("$.data.fieldVisitCompletedCount")
                        .value(6L))
                .andExpect(jsonPath("$.data.reviewSummary.topTags[0].code")
                        .value("PUNCTUAL"))
                .andExpect(jsonPath("$.data.reviewSummary.likeReceivedCount")
                        .value(9L))
                .andExpect(jsonPath("$.data.reviewSummary.reviewCount")
                        .value(12L))
                .andExpect(jsonPath("$.data.isMe").value(false))
                .andExpect(jsonPath("$.data.isFollowing").value(true))
                .andExpect(jsonPath("$.data.canFollow").value(false))
                .andExpect(jsonPath("$.data.canSendMessage").value(true))
                .andExpect(jsonPath("$.data.section").value("STUDIES"))
                .andExpect(jsonPath("$.data.studies.content[0].studyId")
                        .value(10L))
                .andExpect(jsonPath("$.data.studies.content[0].apartment.name")
                        .value("래미안 옥수 리버젠"))
                .andExpect(jsonPath("$.data.reports").value(nullValue()))
                .andExpect(jsonPath("$.data.followings").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(memberService).getPublicProfile(
                1L,
                12L,
                MemberPublicProfileSection.STUDIES,
                MemberPublicStudyStatus.ACTIVE,
                0,
                20
        );
    }

    @Test
    void 공개_프로필_회원_ID가_올바르지_않으면_400을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);

        mockMvc.perform(get("/api/v1/members/not-a-number")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("memberId"))
                .andExpect(jsonPath("$.data.reason")
                        .value("회원 ID는 1 이상의 숫자여야 합니다."));

        mockMvc.perform(get("/api/v1/members/0")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.field").value("memberId"))
                .andExpect(jsonPath("$.data.reason")
                        .value("회원 ID는 1 이상의 숫자여야 합니다."));

        verify(memberService, never()).getPublicProfile(
                any(), any(), any(), any(), anyInt(), anyInt()
        );
    }

    @Test
    void 공개_프로필_조회에_Access_Token이_없으면_401을_반환한다()
            throws Exception {
        mockMvc.perform(get("/api/v1/members/12"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verify(memberService, never()).getPublicProfile(
                any(), any(), any(), any(), anyInt(), anyInt()
        );
    }

    @Test
    void 탈퇴했거나_없는_조회_대상은_404를_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberService.getPublicProfile(
                1L,
                12L,
                MemberPublicProfileSection.STUDIES,
                MemberPublicStudyStatus.ACTIVE,
                0,
                20
        )).thenThrow(
                new BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        );

        mockMvc.perform(get("/api/v1/members/12")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 팔로잉_사용자에게_쪽지를_보내면_201과_명세_응답을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);
        when(memberMessageService.send(
                eq(1L),
                eq(12L),
                any(MemberMessageSendRequest.class)
        )).thenReturn(new MemberMessageSendResult(
                MemberResponseCode.MESSAGE_SENT,
                messageSendResponse(),
                true
        ));

        mockMvc.perform(post("/api/v1/members/12/messages")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageSendRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_MESSAGE_SEND_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("쪽지를 보냈습니다."))
                .andExpect(jsonPath("$.data.recipientId").value(12L))
                .andExpect(jsonPath("$.data.recipientNickname")
                        .value("옥수탐방러"))
                .andExpect(jsonPath("$.data.notificationId").value(81L))
                .andExpect(jsonPath("$.data.clientMessageId")
                        .value("8e70e108-7c81-477a-bb55-a9f334fb5e67"))
                .andExpect(jsonPath("$.data.sentAt")
                        .value("2026-07-25T11:00:00+09:00"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(memberMessageService).send(
                eq(1L),
                eq(12L),
                any(MemberMessageSendRequest.class)
        );
    }

    @Test
    void 같은_쪽지_요청이면_200과_기존_전송_결과를_반환한다()
            throws Exception {
        authenticate("access-token", 1L);
        when(memberMessageService.send(
                eq(1L),
                eq(12L),
                any(MemberMessageSendRequest.class)
        )).thenReturn(new MemberMessageSendResult(
                MemberResponseCode.MESSAGE_ALREADY_SENT,
                messageSendResponse(),
                false
        ));

        mockMvc.perform(post("/api/v1/members/12/messages")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageSendRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_MESSAGE_ALREADY_SENT"))
                .andExpect(jsonPath("$.message")
                        .value("이미 전송된 쪽지입니다."))
                .andExpect(jsonPath("$.data.notificationId").value(81L));
    }

    @Test
    void 쪽지_수신_회원_ID가_올바르지_않으면_400을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);

        mockMvc.perform(post("/api/v1/members/not-a-number/messages")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageSendRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("memberId"))
                .andExpect(jsonPath("$.data.reason")
                        .value("회원 ID는 1 이상의 숫자여야 합니다."));

        verify(memberMessageService, never()).send(any(), any(), any());
    }

    @Test
    void 쪽지_전송에_Access_Token이_없으면_401을_반환한다()
            throws Exception {
        mockMvc.perform(post("/api/v1/members/12/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageSendRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verify(memberMessageService, never()).send(any(), any(), any());
    }

    @Test
    void 쪽지_내용이_비어_있으면_명세의_400을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);
        when(memberMessageService.send(
                eq(1L),
                eq(12L),
                any(MemberMessageSendRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.MEMBER_MESSAGE_CONTENT_REQUIRED,
                Map.of("field", "content")
        ));

        mockMvc.perform(post("/api/v1/members/12/messages")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageSendRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_MESSAGE_CONTENT_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("쪽지 내용을 입력해 주세요."))
                .andExpect(jsonPath("$.data.field").value("content"));
    }

    @Test
    void 팔로잉_관계가_없으면_명세의_403을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);
        when(memberMessageService.send(
                eq(1L),
                eq(12L),
                any(MemberMessageSendRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.MEMBER_MESSAGE_FOLLOW_REQUIRED
        ));

        mockMvc.perform(post("/api/v1/members/12/messages")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageSendRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_MESSAGE_FOLLOW_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("팔로잉 중인 사용자에게만 쪽지를 보낼 수 있습니다."));
    }

    @Test
    void 사용자를_팔로우하면_200과_명세_응답을_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberFollowService.follow(1L, 15L)).thenReturn(
                new MemberFollowResult(
                        MemberResponseCode.FOLLOWED,
                        followResponse()
                )
        );

        mockMvc.perform(put("/api/v1/members/15/follow")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("MEMBER_FOLLOWED"))
                .andExpect(jsonPath("$.message")
                        .value("사용자를 팔로우했습니다."))
                .andExpect(jsonPath("$.data.memberId").value(15L))
                .andExpect(jsonPath("$.data.nickname").value("임장초보"))
                .andExpect(jsonPath("$.data.selectedCharacterId")
                        .value("PALBANG_DOG"))
                .andExpect(jsonPath("$.data.isFollowing").value(true))
                .andExpect(jsonPath("$.data.canSendMessage").value(true))
                .andExpect(jsonPath("$.data.followingCount").value(6L))
                .andExpect(jsonPath("$.data.followedAt")
                        .value("2026-07-25T10:30:00+09:00"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(memberFollowService).follow(1L, 15L);
    }

    @Test
    void 이미_팔로우_중이면_200과_현재_상태를_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberFollowService.follow(1L, 15L)).thenReturn(
                new MemberFollowResult(
                        MemberResponseCode.ALREADY_FOLLOWING,
                        followResponse()
                )
        );

        mockMvc.perform(put("/api/v1/members/15/follow")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_ALREADY_FOLLOWING"))
                .andExpect(jsonPath("$.message")
                        .value("이미 팔로우 중인 사용자입니다."))
                .andExpect(jsonPath("$.data.isFollowing").value(true));
    }

    @Test
    void 팔로우_회원_ID가_올바르지_않으면_400을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);

        mockMvc.perform(put("/api/v1/members/not-a-number/follow")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("memberId"))
                .andExpect(jsonPath("$.data.reason")
                        .value("회원 ID는 1 이상의 숫자여야 합니다."));

        verify(memberFollowService, never()).follow(any(), any());
    }

    @Test
    void 팔로우에_Access_Token이_없으면_401을_반환한다()
            throws Exception {
        mockMvc.perform(put("/api/v1/members/15/follow"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verify(memberFollowService, never()).follow(any(), any());
    }

    @Test
    void 사용자_팔로우를_해제하면_200과_명세_응답을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);
        when(memberFollowService.unfollow(1L, 15L)).thenReturn(
                new MemberUnfollowResult(
                        MemberResponseCode.UNFOLLOWED,
                        unfollowResponse()
                )
        );

        mockMvc.perform(delete("/api/v1/members/15/follow")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("MEMBER_UNFOLLOWED"))
                .andExpect(jsonPath("$.message")
                        .value("사용자 팔로우를 해제했습니다."))
                .andExpect(jsonPath("$.data.memberId").value(15L))
                .andExpect(jsonPath("$.data.nickname").value("임장초보"))
                .andExpect(jsonPath("$.data.selectedCharacterId")
                        .value("PALBANG_DOG"))
                .andExpect(jsonPath("$.data.isFollowing").value(false))
                .andExpect(jsonPath("$.data.canSendMessage").value(false))
                .andExpect(jsonPath("$.data.followingCount").value(5L))
                .andExpect(jsonPath("$.data.unfollowedAt")
                        .value("2026-07-25T10:30:00+09:00"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(memberFollowService).unfollow(1L, 15L);
    }

    @Test
    void 이미_팔로우하지_않으면_200과_현재_상태를_반환한다()
            throws Exception {
        authenticate("access-token", 1L);
        when(memberFollowService.unfollow(1L, 15L)).thenReturn(
                new MemberUnfollowResult(
                        MemberResponseCode.ALREADY_UNFOLLOWED,
                        unfollowResponse()
                )
        );

        mockMvc.perform(delete("/api/v1/members/15/follow")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_ALREADY_UNFOLLOWED"))
                .andExpect(jsonPath("$.message")
                        .value("이미 팔로우하지 않은 사용자입니다."))
                .andExpect(jsonPath("$.data.isFollowing").value(false));
    }

    @Test
    void 팔로우_해제_회원_ID가_올바르지_않으면_400을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);

        mockMvc.perform(delete("/api/v1/members/not-a-number/follow")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("memberId"))
                .andExpect(jsonPath("$.data.reason")
                        .value("회원 ID는 1 이상의 숫자여야 합니다."));

        verify(memberFollowService, never()).unfollow(any(), any());
    }

    @Test
    void 팔로우_해제에_Access_Token이_없으면_401을_반환한다()
            throws Exception {
        mockMvc.perform(delete("/api/v1/members/15/follow"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verify(memberFollowService, never()).unfollow(any(), any());
    }

    @Test
    void 본인을_팔로우_해제_대상으로_지정하면_400을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);
        when(memberFollowService.unfollow(1L, 1L)).thenThrow(
                new BusinessException(
                        ErrorCode.MEMBER_SELF_UNFOLLOW_NOT_ALLOWED
                )
        );

        mockMvc.perform(delete("/api/v1/members/1/follow")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_SELF_UNFOLLOW_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message")
                        .value("본인을 팔로우 해제 대상으로 지정할 수 없습니다."));
    }

    @Test
    void 없는_회원의_팔로우_해제는_404를_반환한다()
            throws Exception {
        authenticate("access-token", 1L);
        when(memberFollowService.unfollow(1L, 99L)).thenThrow(
                new BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        );

        mockMvc.perform(delete("/api/v1/members/99/follow")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 내_팔로잉_목록을_커서로_조회하면_200과_명세_응답을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);
        when(memberFollowService.getFollowings(1L, 30L, 20))
                .thenReturn(followingListResponse());

        mockMvc.perform(get("/api/v1/members/me/followings")
                        .header("Authorization", "Bearer access-token")
                        .param("cursor", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_FOLLOWING_LIST_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("팔로잉 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.content[0].memberId")
                        .value(15L))
                .andExpect(jsonPath("$.data.content[0].nickname")
                        .value("임장초보"))
                .andExpect(jsonPath("$.data.content[0].profileImageUrl")
                        .value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].selectedCharacterId")
                        .value("PALBANG_DOG"))
                .andExpect(jsonPath("$.data.content[0].ageGroup")
                        .value("THIRTIES"))
                .andExpect(jsonPath("$.data.content[0].interestRegion")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.content[0].participatingStudyCount")
                        .value(4L))
                .andExpect(jsonPath("$.data.content[0].isFollowing")
                        .value(true))
                .andExpect(jsonPath("$.data.content[0].canSendMessage")
                        .value(true))
                .andExpect(jsonPath("$.data.content[0].followedAt")
                        .value("2026-07-25T10:30:00+09:00"))
                .andExpect(jsonPath("$.data.totalCount").value(5L))
                .andExpect(jsonPath("$.data.nextCursor").value(30L))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(memberFollowService).getFollowings(1L, 30L, 20);
    }

    @Test
    void 팔로잉_목록_커서가_숫자가_아니면_전용_400을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);

        mockMvc.perform(get("/api/v1/members/me/followings")
                        .header("Authorization", "Bearer access-token")
                        .param("cursor", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_FOLLOWING_CURSOR_INVALID"))
                .andExpect(jsonPath("$.data.field").value("cursor"))
                .andExpect(jsonPath("$.data.reason")
                        .value("커서는 1 이상의 숫자여야 합니다."));

        verify(memberFollowService, never()).getFollowings(
                any(),
                any(),
                anyInt()
        );
    }

    @Test
    void 팔로잉_목록_조회에_Access_Token이_없으면_401을_반환한다()
            throws Exception {
        mockMvc.perform(get("/api/v1/members/me/followings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verify(memberFollowService, never()).getFollowings(
                any(),
                any(),
                anyInt()
        );
    }

    @Test
    void 공개_프로필의_리포트_탭을_선택해_조회한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberService.getPublicProfile(
                1L,
                12L,
                MemberPublicProfileSection.REPORTS,
                MemberPublicStudyStatus.ALL,
                1,
                10
        )).thenReturn(publicProfileResponse());

        mockMvc.perform(get("/api/v1/members/12")
                        .header("Authorization", "Bearer access-token")
                        .param("section", "REPORTS")
                        .param("studyStatus", "ALL")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(memberService).getPublicProfile(
                1L,
                12L,
                MemberPublicProfileSection.REPORTS,
                MemberPublicStudyStatus.ALL,
                1,
                10
        );
    }

    @Test
    void 공개_프로필_탭이_올바르지_않으면_400을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);

        mockMvc.perform(get("/api/v1/members/12")
                        .header("Authorization", "Bearer access-token")
                        .param("section", "CALENDAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("section"))
                .andExpect(jsonPath("$.data.allowedValues[0]")
                        .value("STUDIES"))
                .andExpect(jsonPath("$.data.allowedValues[2]")
                        .value("FOLLOWINGS"));

        verify(memberService, never()).getPublicProfile(
                any(), any(), any(), any(), anyInt(), anyInt()
        );
    }

    @Test
    void 내_리포트_목록을_조회하면_200과_확장된_카드_정보를_반환한다()
            throws Exception {
        authenticate("access-token", 1L);
        when(reportService.getMyReports(1L, 0, 20))
                .thenReturn(reportPageResponse());

        mockMvc.perform(get("/api/v1/members/me/reports")
                        .header("Authorization", "Bearer access-token")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_REPORT_LIST_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("내 리포트 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.content[0].reportId")
                        .value(48L))
                .andExpect(jsonPath("$.data.content[0].analysisTags[0]")
                        .value("교통 우수"))
                .andExpect(jsonPath("$.data.content[0].apartment.name")
                        .value("래미안 옥수 리버젠"))
                .andExpect(jsonPath("$.data.content[0].study.studyId")
                        .value(10L))
                .andExpect(jsonPath("$.data.content[0].study.participantCount")
                        .value(4L))
                .andExpect(jsonPath("$.data.content[0].canViewEvidence")
                        .value(false))
                .andExpect(jsonPath("$.data.content[0].completedAt")
                        .value("2026-07-22T18:07:00+09:00"))
                .andExpect(jsonPath("$.data.totalElements").value(1L))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalPages").value(1));

        verify(reportService).getMyReports(1L, 0, 20);
    }

    @Test
    void 내_리포트_목록의_페이지가_음수면_400을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);

        mockMvc.perform(get("/api/v1/members/me/reports")
                        .header("Authorization", "Bearer access-token")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("page"))
                .andExpect(jsonPath("$.data.reason")
                        .value("페이지 번호는 0 이상이어야 합니다."));

        verify(reportService, never()).getMyReports(any(), anyInt(), anyInt());
    }

    @Test
    void 내_리포트_목록의_크기가_범위를_벗어나면_400을_반환한다()
            throws Exception {
        authenticate("access-token", 1L);

        mockMvc.perform(get("/api/v1/members/me/reports")
                        .header("Authorization", "Bearer access-token")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("size"))
                .andExpect(jsonPath("$.data.reason")
                        .value("페이지 크기는 1 이상 100 이하이어야 합니다."));

        verify(reportService, never()).getMyReports(any(), anyInt(), anyInt());
    }

    @Test
    void 내_리포트_목록에_Access_Token이_없으면_401을_반환한다()
            throws Exception {
        mockMvc.perform(get("/api/v1/members/me/reports"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verify(reportService, never()).getMyReports(any(), anyInt(), anyInt());
    }

    @Test
    void 내_프로필을_수정하면_200과_최신_정보를_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberService.updateMyProfile(
                eq(1L),
                any(MemberProfileUpdateRequest.class)
        )).thenReturn(profileUpdateResponse());

        mockMvc.perform(patch("/api/v1/members/me")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "성동구탐방러",
                                  "ageGroup": "FIFTIES",
                                  "ageGroupPublicAgreed": true,
                                  "purpose": "INVESTMENT",
                                  "priorities": [
                                    "PARKING",
                                    "TRANSPORT",
                                    "SAFETY"
                                  ],
                                  "selectedCharacterId": "PALBANG_DOG",
                                  "maritalStatus": "MARRIED",
                                  "hasVehicle": true,
                                  "hasChildren": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_PROFILE_UPDATE_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("프로필이 수정되었습니다."))
                .andExpect(jsonPath("$.data.memberId").value(1L))
                .andExpect(jsonPath("$.data.nickname")
                        .value("성동구탐방러"))
                .andExpect(jsonPath("$.data.ageGroup")
                        .value("FIFTIES"))
                .andExpect(jsonPath("$.data.ageGroupPublicAgreed")
                        .value(true))
                .andExpect(jsonPath("$.data.purpose")
                        .value("INVESTMENT"))
                .andExpect(jsonPath("$.data.householdType").doesNotExist())
                .andExpect(jsonPath("$.data.budget").doesNotExist())
                .andExpect(jsonPath("$.data.interestRegion").doesNotExist())
                .andExpect(jsonPath("$.data.interestRegionPublicAgreed")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.priorities[0]")
                        .value("PARKING"))
                .andExpect(jsonPath("$.data.priorities[1]")
                        .value("TRANSPORT"))
                .andExpect(jsonPath("$.data.priorities[2]")
                        .value("SAFETY"))
                .andExpect(jsonPath("$.data.selectedCharacterId")
                        .value("PALBANG_DOG"))
                .andExpect(jsonPath("$.data.maritalStatus")
                        .value("MARRIED"))
                .andExpect(jsonPath("$.data.hasVehicle").value(true))
                .andExpect(jsonPath("$.data.hasChildren").value(false))
                .andExpect(jsonPath("$.data.updatedAt")
                        .value("2026-07-28T15:00:00+09:00"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void PATCH_요청은_미전송_null_false를_구분한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberService.updateMyProfile(
                eq(1L),
                any(MemberProfileUpdateRequest.class)
        )).thenReturn(profileUpdateResponse());

        mockMvc.perform(patch("/api/v1/members/me")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": null,
                                  "hasVehicle": false
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<MemberProfileUpdateRequest> captor =
                ArgumentCaptor.forClass(MemberProfileUpdateRequest.class);
        verify(memberService).updateMyProfile(eq(1L), captor.capture());
        MemberProfileUpdateRequest request = captor.getValue();
        assertThat(request.hasNickname()).isTrue();
        assertThat(request.getNickname()).isNull();
        assertThat(request.hasVehicle()).isTrue();
        assertThat(request.getHasVehicle()).isFalse();
        assertThat(request.hasAgeGroup()).isFalse();
    }

    @Test
    void 내_프로필_수정에_Access_Token이_없으면_401을_반환한다()
            throws Exception {
        mockMvc.perform(patch("/api/v1/members/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "성동구탐방러"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verify(memberService, never()).updateMyProfile(any(), any());
    }

    @Test
    void 수정_항목이_없으면_명세의_400을_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberService.updateMyProfile(
                eq(1L),
                any(MemberProfileUpdateRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.MEMBER_PROFILE_UPDATE_EMPTY
        ));

        mockMvc.perform(patch("/api/v1/members/me")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_PROFILE_UPDATE_EMPTY"))
                .andExpect(jsonPath("$.message")
                        .value("수정할 내 정보를 입력해 주세요."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 온보딩_정보를_저장하면_200과_최신_정보를_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberService.saveOnboarding(
                eq(1L),
                any(OnboardingRequest.class)
        )).thenReturn(completedResponse());

        mockMvc.perform(put("/api/v1/members/me/onboarding")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_ONBOARDING_SAVED"))
                .andExpect(jsonPath("$.message")
                        .value("온보딩 정보가 저장되었습니다."))
                .andExpect(jsonPath("$.data.memberId").value(1L))
                .andExpect(jsonPath("$.data.skipped").doesNotExist())
                .andExpect(jsonPath("$.data.purpose")
                        .value("RESIDENCE"))
                .andExpect(jsonPath("$.data.maritalStatus")
                        .value("MARRIED"))
                .andExpect(jsonPath("$.data.hasVehicle").value(true))
                .andExpect(jsonPath("$.data.hasChildren").value(true))
                .andExpect(jsonPath("$.data.priorities[0]")
                        .value("TRANSPORT"))
                .andExpect(jsonPath("$.data.priorities[3]")
                        .value("GREEN_SPACE"))
                .andExpect(jsonPath("$.data.ageGroup")
                        .value("FIFTIES"))
                .andExpect(jsonPath("$.data.ageGroupPublicAgreed")
                        .value(true))
                .andExpect(jsonPath("$.data.selectedCharacterId")
                        .value("PALBANG_RABBIT"))
                .andExpect(jsonPath("$.data.householdType").doesNotExist())
                .andExpect(jsonPath("$.data.budget").doesNotExist())
                .andExpect(jsonPath("$.data.interestRegion").doesNotExist())
                .andExpect(jsonPath("$.data.interestRegionPublicAgreed")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.onboardingCompleted")
                        .value(true))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(memberService).saveOnboarding(
                eq(1L),
                any(OnboardingRequest.class)
        );
    }

    @Test
    void Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(put("/api/v1/members/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"))
                .andExpect(jsonPath("$.message")
                        .value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(memberService, never()).saveOnboarding(any(), any());
    }

    @Test
    void 유효하지_않은_Access_Token이면_401을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("invalid-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(put("/api/v1/members/me/onboarding")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verify(memberService, never()).saveOnboarding(any(), any());
    }

    @Test
    void 필수_항목이_누락되면_명세의_400을_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberService.saveOnboarding(
                eq(1L),
                any(OnboardingRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.MEMBER_ONBOARDING_REQUIRED_FIELD_MISSING,
                Map.of(
                        "field", "purpose",
                        "reason", "필수 값입니다."
                )
        ));

        mockMvc.perform(put("/api/v1/members/me/onboarding")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_ONBOARDING_REQUIRED_FIELD_MISSING"))
                .andExpect(jsonPath("$.message")
                        .value("온보딩 필수 항목을 모두 선택해 주세요."))
                .andExpect(jsonPath("$.data.field").value("purpose"))
                .andExpect(jsonPath("$.data.reason")
                        .value("필수 값입니다."));
    }

    @Test
    void 허용되지_않은_캐릭터면_명세의_400을_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberService.saveOnboarding(
                eq(1L),
                any(OnboardingRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.MEMBER_CHARACTER_INVALID,
                Map.of(
                        "allowedValues",
                        List.of(
                                "PALBANG",
                                "PALBANG_RABBIT",
                                "PALBANG_DOG"
                        )
                )
        ));

        mockMvc.perform(put("/api/v1/members/me/onboarding")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completedRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_CHARACTER_INVALID"))
                .andExpect(jsonPath("$.message")
                        .value("선택할 수 없는 캐릭터입니다."))
                .andExpect(jsonPath("$.data.allowedValues[0]")
                        .value("PALBANG"))
                .andExpect(jsonPath("$.data.allowedValues[2]")
                        .value("PALBANG_DOG"));
    }

    @Test
    void 우선순위가_중복되면_명세의_400을_반환한다() throws Exception {
        authenticate("access-token", 1L);
        when(memberService.saveOnboarding(
                eq(1L),
                any(OnboardingRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.MEMBER_PRIORITY_DUPLICATED
        ));

        mockMvc.perform(put("/api/v1/members/me/onboarding")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completedRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_PRIORITY_DUPLICATED"))
                .andExpect(jsonPath("$.message")
                        .value("같은 우선순위를 중복해서 선택할 수 없습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private void authenticate(String accessToken, Long memberId) {
        when(jwtTokenProvider.parseAccessToken(accessToken))
                .thenReturn(memberId);
    }

    private String completedRequest() {
        return """
                {
                  "purpose": "RESIDENCE",
                  "maritalStatus": "MARRIED",
                  "hasVehicle": true,
                  "hasChildren": true,
                  "priorities": [
                    "TRANSPORT",
                    "WALKABILITY",
                    "PARKING",
                    "GREEN_SPACE"
                  ],
                  "ageGroup": "FIFTIES",
                  "ageGroupPublicAgreed": true,
                  "selectedCharacterId": "PALBANG_RABBIT"
                }
                """;
    }

    private OnboardingResponse completedResponse() {
        return new OnboardingResponse(
                1L,
                "RESIDENCE",
                "MARRIED",
                true,
                true,
                List.of(
                        "TRANSPORT",
                        "WALKABILITY",
                        "PARKING",
                        "GREEN_SPACE"
                ),
                "FIFTIES",
                true,
                "PALBANG_RABBIT",
                true
        );
    }

    private MemberProfileResponse profileResponse() {
        return new MemberProfileResponse(
                1L,
                "dain@example.com",
                "옥수탐방러",
                null,
                "PALBANG_RABBIT",
                "TWENTIES",
                false,
                true,
                false,
                new MemberProfileResponse.Preference(
                        "RESIDENCE",
                        "MARRIED",
                        true,
                        false,
                        List.of("TRANSPORT", "SAFETY", "PARKING")
                ),
                true,
                32L,
                7L,
                new MemberProfileResponse.Summary(3L, 2L, 5L),
                reviewSummaryFixture(),
                OffsetDateTime.parse("2026-06-26T00:00:00+09:00"),
                OffsetDateTime.parse("2026-07-28T15:00:00+09:00")
        );
    }

    private MemberReviewSummaryResponse reviewSummaryFixture() {
        return new MemberReviewSummaryResponse(
                List.of(new com.ssafy.ssabangpalbang.review.dto.response
                        .ReviewTagCountView(
                        "PUNCTUAL",
                        "시간 약속을 잘 지켜요",
                        "⏰",
                        "PERSON",
                        8L
                )),
                9L,
                12L
        );
    }

    private String messageSendRequest() {
        return """
                {
                  "content": "다음 임장도 같이 참여해요!",
                  "clientMessageId": "8e70e108-7c81-477a-bb55-a9f334fb5e67"
                }
                """;
    }

    private MemberMessageSendResponse messageSendResponse() {
        return new MemberMessageSendResponse(
                12L,
                "옥수탐방러",
                81L,
                "8e70e108-7c81-477a-bb55-a9f334fb5e67",
                OffsetDateTime.parse("2026-07-25T11:00:00+09:00")
        );
    }

    private MemberFollowResponse followResponse() {
        return new MemberFollowResponse(
                15L,
                "임장초보",
                "PALBANG_DOG",
                true,
                true,
                6L,
                OffsetDateTime.parse("2026-07-25T10:30:00+09:00")
        );
    }

    private MemberUnfollowResponse unfollowResponse() {
        return new MemberUnfollowResponse(
                15L,
                "임장초보",
                "PALBANG_DOG",
                false,
                false,
                5L,
                OffsetDateTime.parse("2026-07-25T10:30:00+09:00")
        );
    }

    private MemberFollowingListResponse followingListResponse() {
        return new MemberFollowingListResponse(
                List.of(new MemberFollowingResponse(
                        15L,
                        "임장초보",
                        null,
                        "PALBANG_DOG",
                        "THIRTIES",
                        4L,
                        true,
                        true,
                        OffsetDateTime.parse("2026-07-25T10:30:00+09:00")
                )),
                5L,
                30L,
                true
        );
    }

    private MemberPublicProfileResponse publicProfileResponse() {
        PublicProfileStudyResponse study = new PublicProfileStudyResponse(
                10L,
                "옥수동 주말 임장",
                "IN_PROGRESS",
                "MEMBER",
                new PublicProfileStudyResponse.ApartmentSummary(
                        15L,
                        "래미안 옥수 리버젠"
                )
        );
        return new MemberPublicProfileResponse(
                12L,
                "옥수탐방러",
                null,
                "PALBANG_RABBIT",
                "TWENTIES",
                "서울특별시 성동구",
                4L,
                3L,
                5L,
                6L,
                reviewSummaryFixture(),
                false,
                true,
                false,
                true,
                MemberPublicProfileSection.STUDIES,
                new PageResponse<>(List.of(study), 1L, 0, 20, 1),
                null,
                null
        );
    }

    private PageResponse<MemberReportResponse> reportPageResponse() {
        MemberReportResponse report = new MemberReportResponse(
                48L,
                "래미안 옥수 리버젠 임장 리포트",
                "교통은 좋고 경사는 주의가 필요합니다.",
                List.of("교통 우수", "단지 경사"),
                new MemberReportResponse.ApartmentSummary(
                        15L,
                        "래미안 옥수 리버젠"
                ),
                new MemberReportResponse.StudySummary(
                        10L,
                        "옥수동 주말 임장",
                        OffsetDateTime.parse("2026-07-20T14:00:00+09:00"),
                        4L
                ),
                "DONE",
                true,
                false,
                OffsetDateTime.parse("2026-07-22T18:07:00+09:00")
        );
        return new PageResponse<>(List.of(report), 1L, 0, 20, 1);
    }

    private MemberProfileUpdateResponse profileUpdateResponse() {
        return new MemberProfileUpdateResponse(
                1L,
                "성동구탐방러",
                "FIFTIES",
                true,
                "INVESTMENT",
                List.of("PARKING", "TRANSPORT", "SAFETY"),
                "PALBANG_DOG",
                "MARRIED",
                true,
                false,
                OffsetDateTime.parse("2026-07-28T15:00:00+09:00")
        );
    }
}
