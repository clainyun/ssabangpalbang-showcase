package com.ssafy.ssabangpalbang.community.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.community.dto.request.PostCreateRequest;
import com.ssafy.ssabangpalbang.community.dto.request.PostListCondition;
import com.ssafy.ssabangpalbang.community.dto.request.PostUpdateRequest;
import com.ssafy.ssabangpalbang.community.dto.response.PostCreateResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostDeleteResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostDetailResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostListResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostLikeResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostLikeResult;
import com.ssafy.ssabangpalbang.community.dto.response.PostUnlikeResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostUnlikeResult;
import com.ssafy.ssabangpalbang.community.dto.response.PostUpdateResponse;
import com.ssafy.ssabangpalbang.community.response.PostResponseCode;
import com.ssafy.ssabangpalbang.community.service.PostCommandService;
import com.ssafy.ssabangpalbang.community.service.PostQueryService;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@Validated
@Tag(name = "커뮤니티 게시글", description = "커뮤니티 게시글 API")
public class PostController {

    private final PostCommandService postCommandService;
    private final PostQueryService postQueryService;

    @GetMapping
    @Operation(
            summary = "게시글 목록·검색",
            description = """
                    공개 게시글을 게시판과 키워드로 조회하고
                    LATEST 또는 HOT 커서 순서로 반환합니다.
                    """
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "게시글 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 조건 또는 커서가 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "활성 회원을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "처리되지 않은 서버 오류"
            )
    })
    public ResponseEntity<ApiResponse<PostListResponse>> getPosts(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @ModelAttribute @ParameterObject
            PostListCondition condition
    ) {
        PostListResponse response = postQueryService.getPosts(
                member.memberId(),
                condition
        );
        return ResponseEntity.ok(ApiResponse.success(
                PostResponseCode.POST_LIST_SUCCESS,
                response
        ));
    }

    @PostMapping
    @Operation(
            summary = "게시글 작성",
            description = "로그인한 회원이 정보 또는 자유 게시글을 작성합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "게시글 작성 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "게시판 유형·제목·본문 오류 또는 첨부 파일 10개 초과"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "본인 소유가 아니거나 사용할 수 없는 첨부 파일"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원, 아파트 또는 첨부 파일을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 다른 게시글에 사용된 첨부 파일"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "처리되지 않은 서버 오류"
            )
    })
    public ResponseEntity<ApiResponse<PostCreateResponse>> createPost(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody PostCreateRequest request
    ) {
        PostCreateResponse response = postCommandService.create(
                member.memberId(),
                request
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        PostResponseCode.POST_CREATE_SUCCESS,
                        response
                ));
    }

    @GetMapping("/{postId}")
    @Operation(
            summary = "게시글 상세 조회",
            description = "게시글의 전체 본문과 작성자, 첨부, 반응 및 현재 회원의 권한을 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "게시글 상세 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID 형식이 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 또는 게시글을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "처리되지 않은 서버 오류"
            )
    })
    public ResponseEntity<ApiResponse<PostDetailResponse>> getPostDetail(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable("postId")
            @Positive(message = "게시글 ID는 1 이상의 숫자여야 합니다.")
            Long postId
    ) {
        PostDetailResponse response = postQueryService.getDetail(
                member.memberId(),
                postId
        );
        return ResponseEntity.ok(ApiResponse.success(
                PostResponseCode.POST_DETAIL_SUCCESS,
                response
        ));
    }

    @PatchMapping("/{postId}")
    @Operation(
            summary = "게시글 수정",
            description = """
                    작성자가 일반 게시글의 전달된 필드만 수정합니다.
                    apartmentId의 명시적 null은 연결 해제이며,
                    fileIds는 수정 후 유지할 전체 첨부 목록입니다.
                    """
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "게시글 수정 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "수정 필드 또는 입력값이 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "작성자 권한이 없거나 자동 리포트 게시글"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원, 게시글, 아파트 또는 파일을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "숨김 상태 또는 첨부 파일 연결 충돌"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "처리되지 않은 서버 오류"
            )
    })
    public ResponseEntity<ApiResponse<PostUpdateResponse>> updatePost(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable("postId")
            @Positive(message = "게시글 ID는 1 이상의 숫자여야 합니다.")
            Long postId,
            @Valid @RequestBody PostUpdateRequest request
    ) {
        PostUpdateResponse response = postCommandService.update(
                member.memberId(),
                postId,
                request
        );
        return ResponseEntity.ok(ApiResponse.success(
                PostResponseCode.POST_UPDATE_SUCCESS,
                response
        ));
    }

    @DeleteMapping("/{postId}")
    @Operation(
            summary = "게시글 삭제",
            description = "작성자가 자신의 일반 게시글을 삭제합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "게시글 삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID 형식이 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "작성자 권한이 없거나 자동 리포트 게시글"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 또는 게시글을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "운영 숨김 상태와 충돌"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "처리되지 않은 서버 오류"
            )
    })
    public ResponseEntity<ApiResponse<PostDeleteResponse>> deletePost(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable("postId")
            @Positive(message = "게시글 ID는 1 이상의 숫자여야 합니다.")
            Long postId
    ) {
        PostDeleteResponse response = postCommandService.delete(
                member.memberId(),
                postId
        );
        return ResponseEntity.ok(ApiResponse.success(
                PostResponseCode.POST_DELETE_SUCCESS,
                response
        ));
    }

    @PutMapping("/{postId}/like")
    @Operation(
            summary = "게시글 좋아요",
            description = "로그인한 회원이 활성 게시글에 좋아요를 멱등하게 등록합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "좋아요 등록 성공 또는 이미 등록됨"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID 형식이 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 또는 활성 게시글을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "처리되지 않은 서버 오류"
            )
    })
    public ResponseEntity<ApiResponse<PostLikeResponse>> likePost(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable("postId")
            @Positive(message = "게시글 ID는 1 이상의 숫자여야 합니다.")
            Long postId
    ) {
        PostLikeResult result = postCommandService.like(
                member.memberId(),
                postId
        );
        return ResponseEntity.ok(ApiResponse.success(
                result.responseCode(),
                result.response()
        ));
    }

    @DeleteMapping("/{postId}/like")
    @Operation(
            summary = "게시글 좋아요 해제",
            description = "로그인한 회원이 활성 게시글의 좋아요를 멱등하게 해제합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "좋아요 해제 성공 또는 이미 해제됨"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID 형식이 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 또는 활성 게시글을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "처리되지 않은 서버 오류"
            )
    })
    public ResponseEntity<ApiResponse<PostUnlikeResponse>> unlikePost(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable("postId")
            @Positive(message = "게시글 ID는 1 이상의 숫자여야 합니다.")
            Long postId
    ) {
        PostUnlikeResult result = postCommandService.unlike(
                member.memberId(),
                postId
        );
        return ResponseEntity.ok(ApiResponse.success(
                result.responseCode(),
                result.response()
        ));
    }
}
