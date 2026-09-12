package com.ssafy.ssabangpalbang.community.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.community.dto.response.PostLikeResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostLikeResult;
import com.ssafy.ssabangpalbang.community.response.PostResponseCode;
import com.ssafy.ssabangpalbang.community.service.PostCommandService;
import com.ssafy.ssabangpalbang.community.service.PostQueryService;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PostLikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostCommandService postCommandService;

    @MockitoBean
    private PostQueryService postQueryService;

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
    void returnsCreatedLikeWithCurrentMetrics() throws Exception {
        when(postCommandService.like(7L, 154L))
                .thenReturn(result(PostResponseCode.POST_LIKE_SUCCESS));

        mockMvc.perform(put("/api/v1/posts/154/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("POST_LIKE_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("게시글에 좋아요를 등록했습니다."))
                .andExpect(jsonPath("$.data", aMapWithSize(9)))
                .andExpect(jsonPath("$.data.postId").value(154L))
                .andExpect(jsonPath("$.data.likedByMe").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1L))
                .andExpect(jsonPath("$.data.hotScore").value(22.7))
                .andExpect(jsonPath("$.data.hotRank").value(8L))
                .andExpect(jsonPath("$.data.likedAt")
                        .value("2026-07-25T17:30:00+09:00"));

        verify(postCommandService).like(7L, 154L);
    }

    @Test
    void returnsAlreadyExistsAsSuccessfulResponse() throws Exception {
        when(postCommandService.like(7L, 154L))
                .thenReturn(result(
                        PostResponseCode.POST_LIKE_ALREADY_EXISTS
                ));

        mockMvc.perform(put("/api/v1/posts/154/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("POST_LIKE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.data.likedByMe").value(true));
    }

    @Test
    void rejectsInvalidPostIdBeforeCallingService() throws Exception {
        mockMvc.perform(put("/api/v1/posts/0/like"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("postId"));

        mockMvc.perform(put("/api/v1/posts/not-a-number/like"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("postId"));

        verifyNoInteractions(postCommandService);
    }

    private PostLikeResult result(PostResponseCode responseCode) {
        return new PostLikeResult(
                responseCode,
                new PostLikeResponse(
                        154L,
                        true,
                        1L,
                        0L,
                        1L,
                        true,
                        new BigDecimal("22.70"),
                        8L,
                        OffsetDateTime.parse(
                                "2026-07-25T17:30:00+09:00"
                        )
                )
        );
    }
}
