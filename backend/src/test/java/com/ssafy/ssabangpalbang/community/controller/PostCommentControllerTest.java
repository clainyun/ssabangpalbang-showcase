package com.ssafy.ssabangpalbang.community.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.community.dto.request.CommentCreateRequest;
import com.ssafy.ssabangpalbang.community.dto.response.CommentAuthorResponse;
import com.ssafy.ssabangpalbang.community.dto.response.CommentCreateResponse;
import com.ssafy.ssabangpalbang.community.dto.response.CommentPostMetricsResponse;
import com.ssafy.ssabangpalbang.community.dto.response.CommentResponse;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostCommentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PostCommentControllerTest {

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
    void createsCommentWith201AndExpectedContract() throws Exception {
        when(commentCommandService.create(
                eq(12L),
                eq(154L),
                any(CommentCreateRequest.class)
        )).thenReturn(response());

        mockMvc.perform(post("/api/v1/posts/154/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "댓글 본문"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("COMMENT_CREATE_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("댓글이 작성되었습니다."))
                .andExpect(jsonPath("$.data.comment.commentId")
                        .value(36L))
                .andExpect(jsonPath("$.data.comment.postId")
                        .value(154L))
                .andExpect(jsonPath("$.data.comment.content")
                        .value("댓글 본문"))
                .andExpect(jsonPath("$.data.comment.author.memberId")
                        .value(12L))
                .andExpect(jsonPath("$.data.comment.author.nickname")
                        .value("옥수탐방러"))
                .andExpect(jsonPath(
                        "$.data.comment.author.profileImageUrl"
                ).hasJsonPath())
                .andExpect(jsonPath(
                        "$.data.comment.author.profileImageUrl"
                ).value(nullValue()))
                .andExpect(jsonPath(
                        "$.data.comment.author.selectedCharacterId"
                ).value("DURI"))
                .andExpect(jsonPath("$.data.comment.isMine")
                        .value(true))
                .andExpect(jsonPath("$.data.comment.isPostAuthor")
                        .value(false))
                .andExpect(jsonPath("$.data.comment.canEdit")
                        .value(true))
                .andExpect(jsonPath("$.data.comment.canDelete")
                        .value(true))
                .andExpect(jsonPath("$.data.comment.createdAt")
                        .value("2026-07-25T17:15:00+09:00"))
                .andExpect(jsonPath("$.data.comment.updatedAt")
                        .value("2026-07-25T17:15:00+09:00"))
                .andExpect(jsonPath(
                        "$.data.postMetrics.commentCount"
                ).value(1L))
                .andExpect(jsonPath("$.data.postMetrics.likeCount")
                        .value(0L))
                .andExpect(jsonPath("$.data.postMetrics.viewCount")
                        .value(1L))
                .andExpect(jsonPath("$.data.postMetrics.isHot")
                        .value(true))
                .andExpect(jsonPath("$.data.postMetrics.hotScore")
                        .value(20.7));
    }

    @Test
    void rejectsMissingContentWithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/posts/154/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field")
                        .value("content"))
                .andExpect(jsonPath("$.data.reason")
                        .value(CommentCreateRequest
                                .CONTENT_INVALID_REASON));

        verifyNoInteractions(commentCommandService);
    }

    @Test
    void rejectsNonPositivePostId() throws Exception {
        mockMvc.perform(post("/api/v1/posts/0/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field")
                        .value("postId"));

        verifyNoInteractions(commentCommandService);
    }

    @Test
    void rejectsNonStringAndServerOwnedFields() throws Exception {
        mockMvc.perform(post("/api/v1/posts/154/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":123}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field")
                        .value("content"))
                .andExpect(jsonPath("$.data.reason")
                        .value(CommentCreateRequest
                                .CONTENT_INVALID_REASON));
        assertBadRequest("""
                {
                  "content": "댓글",
                  "authorId": 99
                }
                """);
        assertBadRequest("""
                {
                  "content": "댓글",
                  "parentCommentId": 1
                }
                """);

        verifyNoInteractions(commentCommandService);
    }

    @Test
    void rejectsDuplicateContentField() throws Exception {
        assertBadRequest("""
                {
                  "content": "첫 번째",
                  "content": "두 번째"
                }
                """);

        verifyNoInteractions(commentCommandService);
    }

    @Test
    void mapsPostNotFoundFromService() throws Exception {
        when(commentCommandService.create(
                eq(12L),
                eq(154L),
                any(CommentCreateRequest.class)
        )).thenThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

        mockMvc.perform(post("/api/v1/posts/154/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    private void assertBadRequest(String content) throws Exception {
        mockMvc.perform(post("/api/v1/posts/154/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
    }

    private CommentCreateResponse response() {
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
        return new CommentCreateResponse(
                new CommentResponse(
                        36L,
                        154L,
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
                ),
                new CommentPostMetricsResponse(
                        1L,
                        0L,
                        1L,
                        true,
                        new BigDecimal("20.7")
                )
        );
    }
}
