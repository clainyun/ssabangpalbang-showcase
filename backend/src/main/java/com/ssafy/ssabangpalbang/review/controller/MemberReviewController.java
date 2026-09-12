package com.ssafy.ssabangpalbang.review.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.review.dto.request.MemberReviewCreateRequest;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewCreateResponse;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewResponseCode;
import com.ssafy.ssabangpalbang.review.service.MemberReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/studies/{studyId}/members/{memberId}/reviews")
@RequiredArgsConstructor
@Validated
@Tag(name = "스터디 멤버 평가", description = "같은 스터디 멤버 익명 태그·좋아요 평가")
public class MemberReviewController {

    private final MemberReviewService memberReviewService;

    @PostMapping
    @Operation(
            summary = "스터디 멤버 익명 평가 등록",
            description = "같은 스터디의 활성 멤버에게 태그와 좋아요, 선택 리뷰를 한 번 등록합니다. "
                    + "태그를 하나 이상 선택하거나 좋아요를 남겨야 합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "평가 등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류(신호 없음·태그 오류) 또는 본인 평가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "평가 자격 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "스터디 또는 평가 대상 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "중복 평가 또는 취소된 스터디")
    })
    public ResponseEntity<ApiResponse<MemberReviewCreateResponse>> create(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable @Positive Long studyId,
            @PathVariable @Positive Long memberId,
            @Valid @RequestBody MemberReviewCreateRequest request
    ) {
        MemberReviewCreateResponse response =
                memberReviewService.createReview(
                        member.memberId(),
                        studyId,
                        memberId,
                        request
                );
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        MemberReviewResponseCode.MEMBER_REVIEW_CREATE_SUCCESS,
                        response
                )
        );
    }
}
