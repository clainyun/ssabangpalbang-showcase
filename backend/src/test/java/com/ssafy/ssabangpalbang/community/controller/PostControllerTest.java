package com.ssafy.ssabangpalbang.community.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.domain.PostStatus;
import com.ssafy.ssabangpalbang.community.dto.request.PostCreateRequest;
import com.ssafy.ssabangpalbang.community.dto.request.PostListCondition;
import com.ssafy.ssabangpalbang.community.dto.response.CursorPageInfoResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostApartmentDetailResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostAttachmentResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostAuthorResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostCreateResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostDetailResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostListResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostPermissionsResponse;
import com.ssafy.ssabangpalbang.community.service.PostCommandService;
import com.ssafy.ssabangpalbang.community.service.PostQueryService;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PostControllerTest {

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
    void createsPostWith201AndExpectedInitialState() throws Exception {
        when(postCommandService.create(
                eq(7L),
                any(PostCreateRequest.class)
        )).thenReturn(response());

        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("POST_CREATE_SUCCESS"))
                .andExpect(jsonPath("$.data.postId").value(154L))
                .andExpect(jsonPath("$.data.boardType").value("INFORMATION"))
                .andExpect(jsonPath("$.data.author.memberId").value(7L))
                .andExpect(jsonPath("$.data.author.authorType").value("MEMBER"))
                .andExpect(jsonPath("$.data.isAutoReport").value(false))
                .andExpect(jsonPath("$.data.report").doesNotExist())
                .andExpect(jsonPath("$.data.attachments[0].fileId").value(401L))
                .andExpect(jsonPath("$.data.attachments[0].displayOrder").value(1))
                .andExpect(jsonPath("$.data.viewCount").value(0))
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.commentCount").value(0))
                .andExpect(jsonPath("$.data.likedByMe").value(false))
                .andExpect(jsonPath("$.data.isMine").value(true))
                .andExpect(jsonPath("$.data.isHot").value(false))
                .andExpect(jsonPath("$.data.createdAt")
                        .value(org.hamcrest.Matchers.endsWith("+09:00")))
                .andExpect(jsonPath("$.data.updatedAt")
                        .value(org.hamcrest.Matchers.endsWith("+09:00")));
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        assertCommonInvalid("""
                {
                  "boardType": "INFORMATION"
                }
                """);
        verifyNoInteractions(postCommandService);
    }

    @Test
    void rejectsNonPositiveIds() throws Exception {
        assertCommonInvalid("""
                {
                  "boardType": "INFORMATION",
                  "title": "제목",
                  "content": "본문",
                  "apartmentId": 0,
                  "fileIds": [1, -2]
                }
                """);
        verifyNoInteractions(postCommandService);
    }

    @Test
    void rejectsUnknownAndServerOwnedFields() throws Exception {
        assertCommonInvalid("""
                {
                  "boardType": "INFORMATION",
                  "title": "제목",
                  "content": "본문",
                  "authorId": 999,
                  "isAutoReport": true
                }
                """);
        verifyNoInteractions(postCommandService);
    }

    @Test
    void rejectsUnknownFieldsEvenWhenTheyUseFormerGuardNames()
            throws Exception {
        assertCommonInvalid("""
                {
                  "boardType": "INFORMATION",
                  "title": "제목",
                  "content": "본문",
                  "unknownFields": [],
                  "allowedFieldsOnly": true
                }
                """);
        verifyNoInteractions(postCommandService);
    }

    @Test
    void rejectsJacksonScalarCoercion() throws Exception {
        assertCommonInvalid("""
                {
                  "boardType": "INFORMATION",
                  "title": 123,
                  "content": "본문"
                }
                """);
        assertCommonInvalid("""
                {
                  "boardType": "INFORMATION",
                  "title": "제목",
                  "content": "본문",
                  "apartmentId": "15"
                }
                """);
        assertCommonInvalid("""
                {
                  "boardType": "INFORMATION",
                  "title": "제목",
                  "content": "본문",
                  "fileIds": ["401"]
                }
                """);
        assertCommonInvalid("""
                {
                  "boardType": "INFORMATION",
                  "title": "제목",
                  "content": "본문",
                  "apartmentId": 15.5
                }
                """);
        verifyNoInteractions(postCommandService);
    }

    @Test
    void mapsAttachmentLimitToDedicatedError() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "boardType": "FREE",
                                  "title": "제목",
                                  "content": "본문",
                                  "fileIds": [1,2,3,4,5,6,7,8,9,10,11]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("POST_ATTACHMENT_LIMIT_EXCEEDED"));

        verifyNoInteractions(postCommandService);
    }

    @Test
    void mapsInvalidBoardTypeFromService() throws Exception {
        when(postCommandService.create(
                eq(7L),
                any(PostCreateRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.POST_BOARD_TYPE_INVALID
        ));

        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "boardType": "INFO",
                                  "title": "제목",
                                  "content": "본문"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("POST_BOARD_TYPE_INVALID"));
    }

    @Test
    void getsPostListWithNormalizedContractFields() throws Exception {
        when(postQueryService.getPosts(
                eq(7L),
                any(PostListCondition.class)
        )).thenReturn(new PostListResponse(
                "INFORMATION",
                "LATEST",
                null,
                List.of(),
                new CursorPageInfoResponse(
                        20,
                        null,
                        false
                )
        ));

        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("boardType", "INFORMATION")
                        .queryParam("sort", "LATEST")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("POST_LIST_SUCCESS"))
                .andExpect(jsonPath("$.data.boardType")
                        .value("INFORMATION"))
                .andExpect(jsonPath("$.data.sort").value("LATEST"))
                .andExpect(jsonPath("$.data.keyword").doesNotExist())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.pageInfo.size").value(20))
                .andExpect(jsonPath("$.data.pageInfo.nextCursor")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.pageInfo.hasNext")
                        .value(false));
    }

    @Test
    void rejectsOutOfRangeListSizeBeforeService() throws Exception {
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("size"));

        verifyNoInteractions(postQueryService);
    }

    @Test
    void mapsInvalidListSortToDedicatedCode() throws Exception {
        when(postQueryService.getPosts(
                eq(7L),
                any(PostListCondition.class)
        )).thenThrow(new BusinessException(
                ErrorCode.POST_SORT_INVALID
        ));

        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("sort", "latest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("POST_SORT_INVALID"));
    }

    @Test
    void getsPostDetailWithIncrementedViewCount() throws Exception {
        when(postQueryService.getDetail(7L, 154L))
                .thenReturn(detailResponse());

        mockMvc.perform(get("/api/v1/posts/154"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("POST_DETAIL_SUCCESS"))
                .andExpect(jsonPath("$.data.postId").value(154L))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.originalAvailable").value(true))
                .andExpect(jsonPath("$.data.viewCount").value(25L))
                .andExpect(jsonPath("$.data.author.authorType").value("MEMBER"))
                .andExpect(jsonPath("$.data.attachments[0].available")
                        .value(true))
                .andExpect(jsonPath("$.data.permissions.canEdit").value(true))
                .andExpect(jsonPath("$.data.hotRank").doesNotExist());
    }

    @Test
    void rejectsNonPositivePostId() throws Exception {
        mockMvc.perform(get("/api/v1/posts/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("postId"));

        verifyNoInteractions(postQueryService);
    }

    @Test
    void mapsInvisiblePostToNotFound() throws Exception {
        when(postQueryService.getDetail(7L, 154L))
                .thenThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

        mockMvc.perform(get("/api/v1/posts/154"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private void assertCommonInvalid(String body) throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
    }

    private String validRequest() {
        return """
                {
                  "boardType": "INFORMATION",
                  "title": "성동구 임장 시 확인할 항목",
                  "content": "교통과 상권을 함께 확인해 보세요.",
                  "apartmentId": 15,
                  "fileIds": [401]
                }
                """;
    }

    private PostCreateResponse response() {
        OffsetDateTime createdAt = OffsetDateTime.of(
                2026, 7, 29, 16, 30, 0, 0,
                ZoneOffset.ofHours(9)
        );
        return new PostCreateResponse(
                154L,
                "INFORMATION",
                "성동구 임장 시 확인할 항목",
                "교통과 상권을 함께 확인해 보세요.",
                "ACTIVE",
                new PostCreateResponse.Author(
                        7L,
                        "집보는다람쥐",
                        null,
                        "JIPKONG",
                        "MEMBER"
                ),
                false,
                new PostCreateResponse.Apartment(
                        15L,
                        "래미안 옥수 리버젠"
                ),
                null,
                List.of(new PostCreateResponse.Attachment(
                        401L,
                        "station-route.jpg",
                        "image/jpeg",
                        "https://s3.example.com/presigned/post-401",
                        1,
                        createdAt.plusMinutes(10)
                )),
                0L,
                0L,
                0L,
                false,
                true,
                false,
                createdAt,
                createdAt
        );
    }

    private PostDetailResponse detailResponse() {
        OffsetDateTime createdAt = OffsetDateTime.of(
                2026, 7, 29, 16, 30, 0, 0,
                ZoneOffset.ofHours(9)
        );
        return new PostDetailResponse(
                154L,
                BoardType.INFORMATION,
                "성동구 임장 시 확인할 항목",
                "교통과 상권을 함께 확인해 보세요.",
                PostStatus.ACTIVE,
                true,
                new PostAuthorResponse(
                        7L,
                        "집보는다람쥐",
                        null,
                        "JIPKONG",
                        "MEMBER"
                ),
                false,
                new PostApartmentDetailResponse(
                        15L,
                        "래미안 옥수 리버젠",
                        "서울특별시 성동구 매봉길 15"
                ),
                null,
                List.of(new PostAttachmentResponse(
                        401L,
                        "station-route.jpg",
                        "image/jpeg",
                        "https://s3.example.com/presigned/post-401",
                        1,
                        true,
                        createdAt.plusMinutes(10)
                )),
                25L,
                2L,
                1L,
                false,
                true,
                false,
                new BigDecimal("17.70"),
                null,
                new PostPermissionsResponse(true, true, true, true),
                createdAt,
                createdAt
        );
    }
}
