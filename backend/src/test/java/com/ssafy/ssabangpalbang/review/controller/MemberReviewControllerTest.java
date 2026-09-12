package com.ssafy.ssabangpalbang.review.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.review.dto.request.MemberReviewCreateRequest;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewCreateResponse;
import com.ssafy.ssabangpalbang.review.dto.response.ReviewTagView;
import com.ssafy.ssabangpalbang.review.service.MemberReviewService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MemberReviewControllerTest {

    private static final String URI = "/api/v1/studies/10/members/9/reviews";

    @Autowired MockMvc mockMvc;
    @MockitoBean MemberReviewService memberReviewService;

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
    void 평가_등록은_201과_작성자_정보가_없는_태그_좋아요_응답을_반환한다() throws Exception {
        when(memberReviewService.createReview(
                eq(7L),
                eq(10L),
                eq(9L),
                any(MemberReviewCreateRequest.class)
        )).thenReturn(response());

        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[\"PUNCTUAL\",\"GOOD_RECORDS\"],"
                                + "\"liked\":true,"
                                + "\"content\":\"좋은 팀원이었어요.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_REVIEW_CREATE_SUCCESS"))
                .andExpect(jsonPath("$.data.reviewId").value(21))
                .andExpect(jsonPath("$.data.studyId").value(10))
                .andExpect(jsonPath("$.data.reviewedMemberId").value(9))
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.tags[0].code").value("PUNCTUAL"))
                .andExpect(jsonPath("$.data.tags[0].label")
                        .value("시간 약속을 잘 지켜요"))
                .andExpect(jsonPath("$.data.tags[0].emoji").value("⏰"))
                .andExpect(jsonPath("$.data.createdAt")
                        .value("2026-08-03T15:00:00+09:00"))
                .andExpect(jsonPath("$.data.rating").doesNotExist())
                .andExpect(jsonPath("$.data.reviewerId").doesNotExist())
                .andExpect(jsonPath("$.data.reviewerNickname").doesNotExist());
    }

    @Test
    void 좋아요만_남긴_요청도_등록한다() throws Exception {
        when(memberReviewService.createReview(
                eq(7L),
                eq(10L),
                eq(9L),
                any(MemberReviewCreateRequest.class)
        )).thenReturn(likeOnlyResponse());

        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liked\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.tags").isEmpty());
    }

    @Test
    void 태그가_15개를_초과하면_400을_반환한다() throws Exception {
        StringBuilder tags = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            tags.append("\"PUNCTUAL\"");
            if (i < 15) {
                tags.append(",");
            }
        }

        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[" + tags + "],\"liked\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("tags"));
    }

    @Test
    void 신호가_전혀_없으면_400을_반환한다() throws Exception {
        when(memberReviewService.createReview(
                eq(7L),
                eq(10L),
                eq(9L),
                any(MemberReviewCreateRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.MEMBER_REVIEW_SIGNAL_REQUIRED
        ));

        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liked\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_REVIEW_SIGNAL_REQUIRED"));
    }

    @Test
    void 유효하지_않은_태그는_400을_반환한다() throws Exception {
        when(memberReviewService.createReview(
                eq(7L),
                eq(10L),
                eq(9L),
                any(MemberReviewCreateRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.MEMBER_REVIEW_TAG_INVALID
        ));

        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[\"UNKNOWN_TAG\"],\"liked\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_REVIEW_TAG_INVALID"));
    }

    @Test
    void 리뷰는_500자를_허용하고_501자를_거절한다() throws Exception {
        when(memberReviewService.createReview(
                eq(7L),
                eq(10L),
                eq(9L),
                any(MemberReviewCreateRequest.class)
        )).thenReturn(response());

        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithContent(500)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithContent(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("content"));
    }

    @Test
    void 중복_평가는_409를_반환한다() throws Exception {
        when(memberReviewService.createReview(
                eq(7L),
                eq(10L),
                eq(9L),
                any(MemberReviewCreateRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.MEMBER_REVIEW_ALREADY_EXISTS
        ));

        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liked\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_REVIEW_ALREADY_EXISTS"));
    }

    @Test
    void 양수가_아닌_경로_ID는_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/studies/0/members/9/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liked\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    private MemberReviewCreateResponse response() {
        return new MemberReviewCreateResponse(
                21L,
                10L,
                9L,
                List.of(
                        new ReviewTagView(
                                "PUNCTUAL",
                                "시간 약속을 잘 지켜요",
                                "⏰"
                        ),
                        new ReviewTagView(
                                "GOOD_RECORDS",
                                "사진·기록을 잘 남겨요",
                                "📸"
                        )
                ),
                true,
                "좋은 팀원이었어요.",
                OffsetDateTime.parse("2026-08-03T15:00:00+09:00")
        );
    }

    private MemberReviewCreateResponse likeOnlyResponse() {
        return new MemberReviewCreateResponse(
                22L,
                10L,
                9L,
                List.of(),
                true,
                null,
                OffsetDateTime.parse("2026-08-03T15:00:00+09:00")
        );
    }

    private String jsonWithContent(int length) {
        return "{\"liked\":true,\"content\":\""
                + "가".repeat(length) + "\"}";
    }
}
