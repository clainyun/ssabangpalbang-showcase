package com.ssafy.ssabangpalbang.community.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.community.dto.request.CommentListCondition;
import com.ssafy.ssabangpalbang.community.dto.response.CommentAuthorResponse;
import com.ssafy.ssabangpalbang.community.dto.response.CommentListItemResponse;
import com.ssafy.ssabangpalbang.community.dto.response.CommentListResponse;
import com.ssafy.ssabangpalbang.community.service.CommentCommandService;
import com.ssafy.ssabangpalbang.community.service.CommentQueryService;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostCommentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PostCommentListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentCommandService commentCommandService;

    @MockitoBean
    private CommentQueryService commentQueryService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedMember(12L),
                        null
                )
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsCommentsWithExpectedContract() throws Exception {
        when(commentQueryService.getComments(
                eq(12L),
                eq(154L),
                any(CommentListCondition.class)
        )).thenReturn(response());

        mockMvc.perform(get("/api/v1/posts/154/comments")
                        .queryParam("cursor", "36")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("COMMENT_LIST_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("댓글 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.postId").value(154L))
                .andExpect(jsonPath("$.data.content[0].commentId")
                        .value(37L))
                .andExpect(jsonPath("$.data.content[0].author.memberId")
                        .value(12L))
                .andExpect(jsonPath(
                        "$.data.content[0].author.profileImageUrl"
                ).value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].isMine")
                        .value(true))
                .andExpect(jsonPath("$.data.content[0].canEdit")
                        .value(true))
                .andExpect(jsonPath("$.data.totalCount").value(21L))
                .andExpect(jsonPath("$.data.nextCursor").value(56L))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void rejectsInvalidCursorAndSizeBeforeService() throws Exception {
        mockMvc.perform(get("/api/v1/posts/154/comments")
                        .queryParam("cursor", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("cursor"));
        mockMvc.perform(get("/api/v1/posts/154/comments")
                        .queryParam("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("size"));

        verifyNoInteractions(commentQueryService);
    }

    @Test
    void mapsInvisiblePostToNotFound() throws Exception {
        when(commentQueryService.getComments(
                eq(12L),
                eq(154L),
                any(CommentListCondition.class)
        )).thenThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

        mockMvc.perform(get("/api/v1/posts/154/comments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    private CommentListResponse response() {
        OffsetDateTime timestamp = OffsetDateTime.of(
                2026,
                7,
                25,
                17,
                15,
                0,
                0,
                ZoneOffset.ofHours(9)
        );
        return new CommentListResponse(
                154L,
                List.of(new CommentListItemResponse(
                        37L,
                        "댓글 본문",
                        new CommentAuthorResponse(
                                12L,
                                "옥수탐방러",
                                null,
                                "DURI"
                        ),
                        true,
                        false,
                        true,
                        true,
                        timestamp,
                        timestamp
                )),
                21L,
                56L,
                true
        );
    }
}
