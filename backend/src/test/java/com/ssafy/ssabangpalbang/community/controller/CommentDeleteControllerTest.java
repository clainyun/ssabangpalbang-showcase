package com.ssafy.ssabangpalbang.community.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.community.dto.response.CommentDeleteResponse;
import com.ssafy.ssabangpalbang.community.dto.response.CommentPostMetricsResponse;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CommentDeleteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentCommandService commentCommandService;

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
    void deletesCommentWith200AndExpectedContract() throws Exception {
        when(commentCommandService.delete(7L, 36L))
                .thenReturn(response());

        mockMvc.perform(delete("/api/v1/comments/36"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("COMMENT_DELETE_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("댓글이 삭제되었습니다."))
                .andExpect(jsonPath("$.data.commentId").value(36L))
                .andExpect(jsonPath("$.data.postId").value(154L))
                .andExpect(jsonPath("$.data.deletedAt")
                        .value("2026-07-25T17:25:00+09:00"))
                .andExpect(jsonPath("$.data.postAvailable").value(true))
                .andExpect(jsonPath("$.data.postMetrics.commentCount")
                        .value(0L))
                .andExpect(jsonPath("$.data.postMetrics.likeCount")
                        .value(2L))
                .andExpect(jsonPath("$.data.postMetrics.viewCount")
                        .value(11L))
                .andExpect(jsonPath("$.data.postMetrics.isHot")
                        .value(false))
                .andExpect(jsonPath("$.data.postMetrics.hotScore")
                        .value(17.7));

        verify(commentCommandService).delete(7L, 36L);
    }

    @Test
    void rejectsInvalidCommentIdBeforeCallingService()
            throws Exception {
        mockMvc.perform(delete("/api/v1/comments/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field")
                        .value("commentId"));

        mockMvc.perform(delete("/api/v1/comments/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field")
                        .value("commentId"));

        verifyNoInteractions(commentCommandService);
    }

    @ParameterizedTest
    @MethodSource("deleteErrors")
    void mapsDeleteErrors(ErrorCode errorCode, int expectedStatus)
            throws Exception {
        when(commentCommandService.delete(7L, 36L))
                .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(delete("/api/v1/comments/36"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code")
                        .value(errorCode.getCode()));
    }

    @Test
    void returnsCommentIdForAlreadyDeletedComment()
            throws Exception {
        when(commentCommandService.delete(7L, 36L))
                .thenThrow(new BusinessException(
                        ErrorCode.COMMENT_ALREADY_DELETED,
                        Map.of("commentId", 36L)
                ));

        mockMvc.perform(delete("/api/v1/comments/36"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("COMMENT_ALREADY_DELETED"))
                .andExpect(jsonPath("$.data.commentId").value(36L));
    }

    private static Stream<Arguments> deleteErrors() {
        return Stream.of(
                Arguments.of(
                        ErrorCode.COMMENT_DELETE_FORBIDDEN,
                        403
                ),
                Arguments.of(ErrorCode.COMMENT_NOT_FOUND, 404),
                Arguments.of(
                        ErrorCode.COMMENT_ALREADY_DELETED,
                        409
                )
        );
    }

    private CommentDeleteResponse response() {
        OffsetDateTime deletedAt = OffsetDateTime.of(
                2026,
                7,
                25,
                17,
                25,
                0,
                0,
                ZoneOffset.ofHours(9)
        );
        return new CommentDeleteResponse(
                36L,
                154L,
                deletedAt,
                true,
                new CommentPostMetricsResponse(
                        0L,
                        2L,
                        11L,
                        false,
                        new BigDecimal("17.70")
                )
        );
    }
}
