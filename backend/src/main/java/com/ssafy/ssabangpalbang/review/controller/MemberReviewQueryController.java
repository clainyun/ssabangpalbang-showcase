package com.ssafy.ssabangpalbang.review.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewListResponse;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewResponseCode;
import com.ssafy.ssabangpalbang.review.service.MemberReviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/{memberId}/reviews")
@RequiredArgsConstructor
@Validated
@Tag(name = "사용자 평가", description = "사용자가 받은 익명 태그·좋아요 평가 조회")
public class MemberReviewQueryController {

    private final MemberReviewQueryService memberReviewQueryService;

    @GetMapping
    @Operation(
            summary = "사용자 익명 평가 목록 조회",
            description = "활성 사용자가 받은 태그 집계(topTags), 받은 좋아요 수, 평가 수와 "
                    + "익명 리뷰를 최근 작성 순서로 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "평가 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "회원 ID, cursor 또는 size 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "탈퇴한 로그인 회원"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "조회 대상 회원 없음")
    })
    public ApiResponse<MemberReviewListResponse> getReviews(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Parameter(
                    description = "평가를 받은 회원 ID",
                    example = "12",
                    schema = @Schema(
                            type = "integer",
                            format = "int64",
                            minimum = "1"
                    )
            )
            @PathVariable @Positive Long memberId,
            @Parameter(
                    description = "이전 응답에서 받은 불투명 커서",
                    example = "eyJ2IjoxfQ.signature"
            )
            @RequestParam(required = false) String cursor,
            @Parameter(
                    description = "조회 개수 (1~100)",
                    example = "20",
                    schema = @Schema(
                            type = "integer",
                            minimum = "1",
                            maximum = "100",
                            defaultValue = "20"
                    )
            )
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(
                MemberReviewResponseCode.MEMBER_REVIEW_LIST_SUCCESS,
                memberReviewQueryService.getReviews(
                        member.memberId(),
                        memberId,
                        cursor,
                        size
                )
        );
    }
}
