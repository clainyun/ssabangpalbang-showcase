package com.ssafy.ssabangpalbang.community.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.community.dto.request.CommentUpdateRequest;
import com.ssafy.ssabangpalbang.community.dto.response.CommentDeleteResponse;
import com.ssafy.ssabangpalbang.community.dto.response.CommentResponse;
import com.ssafy.ssabangpalbang.community.response.CommentResponseCode;
import com.ssafy.ssabangpalbang.community.service.CommentCommandService;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
@Validated
@Tag(name = "커뮤니티 댓글", description = "커뮤니티 댓글 API")
public class CommentController {

    private final CommentCommandService commentCommandService;

    @PatchMapping("/{commentId}")
    @Operation(
            summary = "댓글 수정",
            description = "로그인한 회원이 자신이 작성한 활성 댓글을 수정합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "댓글 수정 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "댓글 ID 또는 본문이 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "댓글 작성자가 아님"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원, 댓글 또는 활성 게시글을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 삭제된 댓글"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "처리되지 않은 서버 오류"
            )
    })
    public ResponseEntity<ApiResponse<CommentResponse>> update(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable("commentId")
            @Positive(message = "댓글 ID는 1 이상의 숫자여야 합니다.")
            Long commentId,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        CommentResponse response = commentCommandService.update(
                member.memberId(),
                commentId,
                request
        );
        return ResponseEntity.ok(ApiResponse.success(
                CommentResponseCode.COMMENT_UPDATE_SUCCESS,
                response
        ));
    }

    @DeleteMapping("/{commentId}")
    @Operation(
            summary = "댓글 삭제",
            description = "로그인한 회원이 자신이 작성한 댓글을 삭제합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "댓글 삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "댓글 ID 형식이 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "댓글 작성자가 아님"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "댓글을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 삭제된 댓글"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "처리되지 않은 서버 오류"
            )
    })
    public ResponseEntity<ApiResponse<CommentDeleteResponse>> delete(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable("commentId")
            @Positive(message = "댓글 ID는 1 이상의 숫자여야 합니다.")
            Long commentId
    ) {
        CommentDeleteResponse response = commentCommandService.delete(
                member.memberId(),
                commentId
        );
        return ResponseEntity.ok(ApiResponse.success(
                CommentResponseCode.COMMENT_DELETE_SUCCESS,
                response
        ));
    }
}
