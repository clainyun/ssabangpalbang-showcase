package com.ssafy.ssabangpalbang.review.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewItemResponse;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewListResponse;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewSummaryResponse;
import com.ssafy.ssabangpalbang.review.dto.response.ReviewTagCountView;
import com.ssafy.ssabangpalbang.review.dto.response.ReviewTagView;
import com.ssafy.ssabangpalbang.review.service.MemberReviewQueryService;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberReviewQueryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MemberReviewQueryControllerTest {

    private static final String URI = "/api/v1/members/12/reviews";

    @Autowired MockMvc mockMvc;
    @MockitoBean MemberReviewQueryService memberReviewQueryService;

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
    void 익명_평가_목록은_태그_집계와_커서를_반환하고_작성자와_스터디를_노출하지_않는다()
            throws Exception {
        when(memberReviewQueryService.getReviews(
                7L,
                12L,
                "opaque-cursor",
                20
        )).thenReturn(response());

        mockMvc.perform(get(URI)
                        .queryParam("cursor", "opaque-cursor")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_REVIEW_LIST_SUCCESS"))
                .andExpect(jsonPath("$.data.summary.topTags[0].code")
                        .value("PUNCTUAL"))
                .andExpect(jsonPath("$.data.summary.topTags[0].category")
                        .value("PERSON"))
                .andExpect(jsonPath("$.data.summary.topTags[0].count")
                        .value(8))
                .andExpect(jsonPath("$.data.summary.likeReceivedCount")
                        .value(9))
                .andExpect(jsonPath("$.data.summary.reviewCount")
                        .value(12))
                .andExpect(jsonPath("$.data.summary.averageRating")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.content[0].reviewId")
                        .value(120))
                .andExpect(jsonPath("$.data.content[0].liked")
                        .value(true))
                .andExpect(jsonPath("$.data.content[0].tags[0].code")
                        .value("PUNCTUAL"))
                .andExpect(jsonPath("$.data.content[0].rating")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.content[0].reviewerId")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.content[0].reviewerNickname")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.content[0].studyId")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.nextCursor")
                        .value("next-opaque-cursor"))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        verify(memberReviewQueryService).getReviews(
                7L,
                12L,
                "opaque-cursor",
                20
        );
    }

    @Test
    void 잘못된_회원_ID와_size는_400으로_거절한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/0/reviews"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        mockMvc.perform(get(URI).queryParam("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verify(memberReviewQueryService, never())
                .getReviews(any(), any(), any(), any(Integer.class));
    }

    @Test
    void 유효하지_않은_커서는_400을_반환한다() throws Exception {
        when(memberReviewQueryService.getReviews(
                eq(7L),
                eq(12L),
                eq("invalid"),
                eq(20)
        )).thenThrow(new BusinessException(ErrorCode.INVALID_CURSOR));

        mockMvc.perform(get(URI).queryParam("cursor", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_CURSOR"));
    }

    private MemberReviewListResponse response() {
        return new MemberReviewListResponse(
                new MemberReviewSummaryResponse(
                        List.of(
                                new ReviewTagCountView(
                                        "PUNCTUAL",
                                        "시간 약속을 잘 지켜요",
                                        "⏰",
                                        "PERSON",
                                        8
                                ),
                                new ReviewTagCountView(
                                        "SHARES_INFO",
                                        "정보 공유를 잘해요",
                                        "📣",
                                        "VISIT",
                                        5
                                )
                        ),
                        9L,
                        12L
                ),
                List.of(new MemberReviewItemResponse(
                        120L,
                        List.of(new ReviewTagView(
                                "PUNCTUAL",
                                "시간 약속을 잘 지켜요",
                                "⏰"
                        )),
                        true,
                        "좋은 팀원이었어요.",
                        OffsetDateTime.parse(
                                "2026-08-03T22:10:00+09:00"
                        )
                )),
                "next-opaque-cursor",
                true
        );
    }
}
