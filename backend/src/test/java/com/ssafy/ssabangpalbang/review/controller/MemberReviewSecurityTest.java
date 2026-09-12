package com.ssafy.ssabangpalbang.review.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewCreateResponse;
import com.ssafy.ssabangpalbang.review.service.MemberReviewService;
import org.junit.jupiter.api.Test;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberReviewController.class)
@Import({AuthSecurityConfiguration.class, GlobalExceptionHandler.class})
class MemberReviewSecurityTest {

    private static final String URI = "/api/v1/studies/10/members/9/reviews";

    @Autowired MockMvc mockMvc;
    @MockitoBean MemberReviewService memberReviewService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    @Test
    void Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liked\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(memberReviewService, jwtTokenProvider);
    }

    @Test
    void 유효한_Access_Token의_회원_ID로_평가를_등록한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(7L);
        when(memberReviewService.createReview(eq(7L), eq(10L), eq(9L), any()))
                .thenReturn(response());

        mockMvc.perform(post(URI)
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liked\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_REVIEW_CREATE_SUCCESS"));

        verify(jwtTokenProvider).parseAccessToken("access-token");
        verify(memberReviewService).createReview(eq(7L), eq(10L), eq(9L), any());
    }

    @Test
    void 위변조된_Access_Token은_401을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("tampered-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(post(URI)
                        .header("Authorization", "Bearer tampered-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liked\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(memberReviewService);
    }

    private MemberReviewCreateResponse response() {
        return new MemberReviewCreateResponse(
                21L,
                10L,
                9L,
                List.of(),
                true,
                null,
                OffsetDateTime.parse("2026-08-03T15:00:00+09:00")
        );
    }
}
