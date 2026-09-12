package com.ssafy.ssabangpalbang.community.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.community.dto.request.CommentCreateRequest;
import com.ssafy.ssabangpalbang.community.dto.request.CommentListCondition;
import com.ssafy.ssabangpalbang.community.dto.response.CommentCreateResponse;
import com.ssafy.ssabangpalbang.community.dto.response.CommentListResponse;
import com.ssafy.ssabangpalbang.community.response.CommentResponseCode;
import com.ssafy.ssabangpalbang.community.service.CommentCommandService;
import com.ssafy.ssabangpalbang.community.service.CommentQueryService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
@RequiredArgsConstructor
@Validated
@Tag(name = "커뮤니티 댓글", description = "커뮤니티 댓글 API")
public class PostCommentController {

    private final CommentCommandService commentCommandService;
    private final CommentQueryService commentQueryService;

    @GetMapping
    @Operation(
            summary = "댓글 목록 조회",
            description = """
                    활성 게시글의 삭제되지 않은 평면 댓글을
                    오래된 순서의 커서 페이지로 반환합니다.
                    """
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "댓글 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID, 커서 또는 조회 개수가 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "활성 회원 또는 게시글을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "처리되지 않은 서버 오류"
            )
    })
    public ResponseEntity<ApiResponse<CommentListResponse>> list(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable("postId")
            @Positive(message = "게시글 ID는 1 이상의 숫자여야 합니다.")
            Long postId,
            @Valid @ModelAttribute @ParameterObject
            CommentListCondition condition
    ) {
        CommentListResponse response =
                commentQueryService.getComments(
                        member.memberId(),
                        postId,
                        condition
                );
        return ResponseEntity.ok(ApiResponse.success(
                CommentResponseCode.COMMENT_LIST_SUCCESS,
                response
        ));
    }

    @PostMapping
    @Operation(
            summary = "댓글 작성",
            description = "로그인한 회원이 활성 게시글에 평면 댓글을 작성합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "댓글 작성 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID 또는 댓글 본문이 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "활성 회원 또는 게시글을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "처리되지 않은 서버 오류"
            )
    })
    public ResponseEntity<ApiResponse<CommentCreateResponse>> create(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable("postId")
            @Positive(message = "게시글 ID는 1 이상의 숫자여야 합니다.")
            Long postId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        CommentCreateResponse response = commentCommandService.create(
                member.memberId(),
                postId,
                request
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        CommentResponseCode.COMMENT_CREATE_SUCCESS,
                        response
                ));
    }
}
