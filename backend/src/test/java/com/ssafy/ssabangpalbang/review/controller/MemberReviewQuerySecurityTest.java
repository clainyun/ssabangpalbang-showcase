package com.ssafy.ssabangpalbang.review.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewListResponse;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewSummaryResponse;
import com.ssafy.ssabangpalbang.review.service.MemberReviewQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberReviewQueryController.class)
@Import({AuthSecurityConfiguration.class, GlobalExceptionHandler.class})
class MemberReviewQuerySecurityTest {

    private static final String URI = "/api/v1/members/12/reviews";

    @Autowired MockMvc mockMvc;
    @MockitoBean MemberReviewQueryService memberReviewQueryService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    @Test
    void Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get(URI))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(memberReviewQueryService, jwtTokenProvider);
    }

    @Test
    void 유효한_Access_Token의_회원_ID로_조회한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(7L);
        when(memberReviewQueryService.getReviews(
                7L,
                12L,
                null,
                20
        )).thenReturn(new MemberReviewListResponse(
                new MemberReviewSummaryResponse(List.of(), 0, 0),
                List.of(),
                null,
                false
        ));

        mockMvc.perform(get(URI)
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_REVIEW_LIST_SUCCESS"));

        verify(jwtTokenProvider).parseAccessToken("access-token");
        verify(memberReviewQueryService).getReviews(
                7L,
                12L,
                null,
                20
        );
    }
}
