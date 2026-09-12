package com.ssafy.ssabangpalbang.community.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.community.dto.response.PostUnlikeResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostUnlikeResult;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PostUnlikeControllerTest {

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
    void returnsRemovedLikeWithCurrentMetrics() throws Exception {
        when(postCommandService.unlike(7L, 154L))
                .thenReturn(result(PostResponseCode.POST_UNLIKE_SUCCESS));

        mockMvc.perform(delete("/api/v1/posts/154/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("POST_UNLIKE_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("게시글 좋아요를 해제했습니다."))
                .andExpect(jsonPath("$.data", aMapWithSize(9)))
                .andExpect(jsonPath("$.data.postId").value(154L))
                .andExpect(jsonPath("$.data.likedByMe").value(false))
                .andExpect(jsonPath("$.data.likeCount").value(0L))
                .andExpect(jsonPath("$.data.commentCount").value(0L))
                .andExpect(jsonPath("$.data.viewCount").value(1L))
                .andExpect(jsonPath("$.data.isHot").value(false))
                .andExpect(jsonPath("$.data.hotScore").value(17.7))
                .andExpect(jsonPath("$.data.hotRank").isEmpty())
                .andExpect(jsonPath("$.data.unlikedAt")
                        .value("2026-07-25T17:35:00+09:00"));

        verify(postCommandService).unlike(7L, 154L);
    }

    @Test
    void returnsAlreadyUnlikedAsSuccessfulResponse() throws Exception {
        when(postCommandService.unlike(7L, 154L))
                .thenReturn(result(
                        PostResponseCode.POST_ALREADY_UNLIKED
                ));

        mockMvc.perform(delete("/api/v1/posts/154/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("POST_ALREADY_UNLIKED"))
                .andExpect(jsonPath("$.message")
                        .value("이미 좋아요가 해제된 게시글입니다."))
                .andExpect(jsonPath("$.data.likedByMe").value(false));
    }

    @Test
    void rejectsInvalidPostIdBeforeCallingService() throws Exception {
        mockMvc.perform(delete("/api/v1/posts/0/like"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("postId"));

        mockMvc.perform(delete("/api/v1/posts/not-a-number/like"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("postId"));

        verifyNoInteractions(postCommandService);
    }

    private PostUnlikeResult result(PostResponseCode responseCode) {
        return new PostUnlikeResult(
                responseCode,
                new PostUnlikeResponse(
                        154L,
                        false,
                        0L,
                        0L,
                        1L,
                        false,
                        new BigDecimal("17.70"),
                        null,
                        OffsetDateTime.parse(
                                "2026-07-25T17:35:00+09:00"
                        )
                )
        );
    }
}
