package com.ssafy.ssabangpalbang.community.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.community.dto.request.PostUpdateRequest;
import com.ssafy.ssabangpalbang.community.dto.response.PostAuthorResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostUpdateResponse;
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
import org.mockito.ArgumentCaptor;
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
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PostUpdateControllerTest {

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
    void updatesPostWith200AndExpectedContract() throws Exception {
        when(postCommandService.update(
                eq(7L),
                eq(154L),
                any(PostUpdateRequest.class)
        )).thenReturn(response());

        mockMvc.perform(patch("/api/v1/posts/154")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "boardType": "FREE",
                                  "title": "수정 제목",
                                  "apartmentId": null,
                                  "fileIds": [401, 405]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("POST_UPDATE_SUCCESS"))
                .andExpect(jsonPath("$.data.postId").value(154L))
                .andExpect(jsonPath("$.data.boardType").value("FREE"))
                .andExpect(jsonPath("$.data.author.memberId").value(7L))
                .andExpect(jsonPath("$.data.isAutoReport").value(false))
                .andExpect(jsonPath("$.data.attachments[0].fileId")
                        .value(401L))
                .andExpect(jsonPath("$.data.attachments[0].displayOrder")
                        .value(1))
                .andExpect(jsonPath("$.data.attachments[0].expiresAt")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.viewCount").value(12L))
                .andExpect(jsonPath("$.data.likeCount").value(2L))
                .andExpect(jsonPath("$.data.commentCount").value(1L))
                .andExpect(jsonPath("$.data.isMine").value(true))
                .andExpect(jsonPath("$.data.createdAt")
                        .value(org.hamcrest.Matchers.endsWith("+09:00")))
                .andExpect(jsonPath("$.data.updatedAt")
                        .value(org.hamcrest.Matchers.endsWith("+09:00")));
    }

    @Test
    void preservesPresenceOfExplicitNullAndEmptyArray() throws Exception {
        when(postCommandService.update(
                eq(7L),
                eq(154L),
                any(PostUpdateRequest.class)
        )).thenReturn(response());

        mockMvc.perform(patch("/api/v1/posts/154")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "apartmentId": null,
                                  "fileIds": []
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<PostUpdateRequest> captor =
                ArgumentCaptor.forClass(PostUpdateRequest.class);
        verify(postCommandService).update(
                eq(7L),
                eq(154L),
                captor.capture()
        );
        assertThat(captor.getValue().isApartmentIdPresent()).isTrue();
        assertThat(captor.getValue().apartmentId()).isNull();
        assertThat(captor.getValue().isFileIdsPresent()).isTrue();
        assertThat(captor.getValue().fileIds()).isEmpty();
        assertThat(captor.getValue().isTitlePresent()).isFalse();
    }

    @Test
    void rejectsInvalidPathUnknownFieldsAndScalarCoercion()
            throws Exception {
        assertCommonInvalid(
                "/api/v1/posts/0",
                "{\"title\":\"제목\"}"
        );
        assertCommonInvalid(
                "/api/v1/posts/154",
                "{\"authorId\":7}"
        );
        assertCommonInvalid(
                "/api/v1/posts/154",
                "{\"fileIds\":[\"401\"]}"
        );

        verifyNoInteractions(postCommandService);
    }

    @ParameterizedTest
    @MethodSource("updateErrors")
    void mapsUpdateErrors(ErrorCode errorCode, int expectedStatus)
            throws Exception {
        when(postCommandService.update(
                eq(7L),
                eq(154L),
                any(PostUpdateRequest.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(patch("/api/v1/posts/154")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\"}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code")
                        .value(errorCode.getCode()));
    }

    private static Stream<Arguments> updateErrors() {
        return Stream.of(
                Arguments.of(ErrorCode.POST_UPDATE_EMPTY, 400),
                Arguments.of(ErrorCode.POST_BOARD_TYPE_INVALID, 400),
                Arguments.of(ErrorCode.POST_ATTACHMENT_LIMIT_EXCEEDED, 400),
                Arguments.of(ErrorCode.POST_UPDATE_FORBIDDEN, 403),
                Arguments.of(
                        ErrorCode.POST_AUTO_REPORT_UPDATE_FORBIDDEN,
                        403
                ),
                Arguments.of(ErrorCode.MEDIA_ACCESS_DENIED, 403),
                Arguments.of(ErrorCode.MEMBER_NOT_FOUND, 404),
                Arguments.of(ErrorCode.POST_NOT_FOUND, 404),
                Arguments.of(ErrorCode.APARTMENT_NOT_FOUND, 404),
                Arguments.of(ErrorCode.MEDIA_FILE_NOT_FOUND, 404),
                Arguments.of(ErrorCode.POST_STATUS_CONFLICT, 409),
                Arguments.of(ErrorCode.POST_ATTACHMENT_ALREADY_USED, 409)
        );
    }

    private void assertCommonInvalid(String path, String body)
            throws Exception {
        mockMvc.perform(patch(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
    }

    private PostUpdateResponse response() {
        OffsetDateTime createdAt = OffsetDateTime.of(
                2026, 7, 25, 16, 30, 0, 0,
                ZoneOffset.ofHours(9)
        );
        return new PostUpdateResponse(
                154L,
                "FREE",
                "수정 제목",
                "기존 본문",
                "ACTIVE",
                new PostAuthorResponse(
                        7L,
                        "집보는다람쥐",
                        null,
                        "JIPKONG",
                        "MEMBER"
                ),
                false,
                null,
                null,
                List.of(new PostUpdateResponse.Attachment(
                        401L,
                        "station-route.jpg",
                        "image/jpeg",
                        "https://example.com/401",
                        1
                )),
                12L,
                2L,
                1L,
                false,
                true,
                true,
                createdAt,
                createdAt.plusMinutes(20)
        );
    }
}
