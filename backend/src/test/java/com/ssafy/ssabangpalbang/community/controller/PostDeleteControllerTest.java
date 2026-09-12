package com.ssafy.ssabangpalbang.community.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.community.dto.response.PostDeleteResponse;
import com.ssafy.ssabangpalbang.community.service.PostCommandService;
import com.ssafy.ssabangpalbang.community.service.PostQueryService;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

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
class PostDeleteControllerTest {

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
    void deletesPostWith200AndMinimalResponse() throws Exception {
        OffsetDateTime deletedAt = OffsetDateTime.of(
                2026,
                7,
                29,
                17,
                0,
                0,
                0,
                ZoneOffset.ofHours(9)
        );
        when(postCommandService.delete(7L, 154L))
                .thenReturn(new PostDeleteResponse(154L, deletedAt));

        mockMvc.perform(delete("/api/v1/posts/154"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("POST_DELETE_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("게시글이 삭제되었습니다."))
                .andExpect(jsonPath("$.data", aMapWithSize(2)))
                .andExpect(jsonPath("$.data.postId").value(154L))
                .andExpect(jsonPath("$.data.deletedAt")
                        .value("2026-07-29T17:00:00+09:00"))
                .andExpect(jsonPath("$.data.title").doesNotExist())
                .andExpect(jsonPath("$.data.attachments").doesNotExist());

        verify(postCommandService).delete(7L, 154L);
    }

    @Test
    void rejectsInvalidPostIdBeforeCallingService() throws Exception {
        mockMvc.perform(delete("/api/v1/posts/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("postId"));

        mockMvc.perform(delete("/api/v1/posts/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("postId"));

        verifyNoInteractions(postCommandService);
    }

    @ParameterizedTest
    @MethodSource("deleteErrors")
    void mapsDeleteErrors(ErrorCode errorCode, int expectedStatus)
            throws Exception {
        when(postCommandService.delete(7L, 154L))
                .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(delete("/api/v1/posts/154"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code")
                        .value(errorCode.getCode()));
    }

    private static Stream<Arguments> deleteErrors() {
        return Stream.of(
                Arguments.of(ErrorCode.POST_DELETE_FORBIDDEN, 403),
                Arguments.of(
                        ErrorCode.POST_AUTO_REPORT_DELETE_FORBIDDEN,
                        403
                ),
                Arguments.of(ErrorCode.MEMBER_NOT_FOUND, 404),
                Arguments.of(ErrorCode.POST_NOT_FOUND, 404),
                Arguments.of(ErrorCode.POST_STATUS_CONFLICT, 409)
        );
    }
}
