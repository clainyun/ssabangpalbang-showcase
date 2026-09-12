package com.ssafy.ssabangpalbang.community.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.community.dto.request.CommentUpdateRequest;
import com.ssafy.ssabangpalbang.community.dto.response.CommentAuthorResponse;
import com.ssafy.ssabangpalbang.community.dto.response.CommentResponse;
import com.ssafy.ssabangpalbang.community.service.CommentCommandService;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CommentUpdateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentCommandService commentCommandService;

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
    void updatesCommentWith200AndExpectedContract() throws Exception {
        CommentUpdateRequest request = new CommentUpdateRequest(
                "수정된 댓글"
        );
        when(commentCommandService.update(12L, 36L, request))
                .thenReturn(response());

        mockMvc.perform(patch("/api/v1/comments/36")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"수정된 댓글"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("COMMENT_UPDATE_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("댓글이 수정되었습니다."))
                .andExpect(jsonPath("$.data.commentId").value(36L))
                .andExpect(jsonPath("$.data.postId").value(154L))
                .andExpect(jsonPath("$.data.content")
                        .value("수정된 댓글"))
                .andExpect(jsonPath("$.data.author.memberId")
                        .value(12L))
                .andExpect(jsonPath("$.data.author.nickname")
                        .value("옥수탐방러"))
                .andExpect(jsonPath("$.data.author.profileImageUrl")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.author.selectedCharacterId")
                        .value("DURI"))
                .andExpect(jsonPath("$.data.isMine").value(true))
                .andExpect(jsonPath("$.data.isPostAuthor").value(false))
                .andExpect(jsonPath("$.data.canEdit").value(true))
                .andExpect(jsonPath("$.data.canDelete").value(true))
                .andExpect(jsonPath("$.data.createdAt")
                        .value("2026-07-25T17:15:00+09:00"))
                .andExpect(jsonPath("$.data.updatedAt")
                        .value("2026-07-25T17:20:00+09:00"));

        verify(commentCommandService).update(12L, 36L, request);
    }

    @Test
    void rejectsInvalidIdAndStrictBodyBeforeService()
            throws Exception {
        mockMvc.perform(patch("/api/v1/comments/0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"댓글"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        mockMvc.perform(patch("/api/v1/comments/36")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field")
                        .value("content"));

        mockMvc.perform(patch("/api/v1/comments/36")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":123}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        mockMvc.perform(patch("/api/v1/comments/36")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"댓글","postId":154}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(commentCommandService);
    }

    @ParameterizedTest
    @MethodSource("updateErrors")
    void mapsUpdateErrors(ErrorCode errorCode, int expectedStatus)
            throws Exception {
        when(commentCommandService.update(
                12L,
                36L,
                new CommentUpdateRequest("댓글")
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(patch("/api/v1/comments/36")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"댓글"}
                                """))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code")
                        .value(errorCode.getCode()));
    }

    @Test
    void usesUpdateSpecificMessageForDeletedComment()
            throws Exception {
        when(commentCommandService.update(
                12L,
                36L,
                new CommentUpdateRequest("댓글")
        )).thenThrow(new BusinessException(
                ErrorCode.COMMENT_ALREADY_DELETED,
                "삭제된 댓글은 수정할 수 없습니다."
        ));

        mockMvc.perform(patch("/api/v1/comments/36")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"댓글"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("COMMENT_ALREADY_DELETED"))
                .andExpect(jsonPath("$.message")
                        .value("삭제된 댓글은 수정할 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private static Stream<Arguments> updateErrors() {
        return Stream.of(
                Arguments.of(
                        ErrorCode.COMMENT_UPDATE_FORBIDDEN,
                        403
                ),
                Arguments.of(ErrorCode.COMMENT_NOT_FOUND, 404),
                Arguments.of(ErrorCode.POST_NOT_FOUND, 404),
                Arguments.of(
                        ErrorCode.COMMENT_ALREADY_DELETED,
                        409
                )
        );
    }

    private CommentResponse response() {
        return new CommentResponse(
                36L,
                154L,
                "수정된 댓글",
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
                OffsetDateTime.of(
                        2026,
                        7,
                        25,
                        17,
                        15,
                        0,
                        0,
                        ZoneOffset.ofHours(9)
                ),
                OffsetDateTime.of(
                        2026,
                        7,
                        25,
                        17,
                        20,
                        0,
                        0,
                        ZoneOffset.ofHours(9)
                )
        );
    }
}
