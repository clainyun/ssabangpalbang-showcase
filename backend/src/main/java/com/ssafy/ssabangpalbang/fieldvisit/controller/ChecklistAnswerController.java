package com.ssafy.ssabangpalbang.fieldvisit.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.ChecklistAnswerSaveRequest;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistAnswerSaveResponse;
import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistAnswerService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/studies/{studyId}/field-visit/checklist")
@Tag(name = "임장 체크리스트", description = "체크리스트 완료 상태 저장")
public class ChecklistAnswerController {

    private final ChecklistAnswerService checklistAnswerService;

    @PutMapping("/answers")
    @Operation(summary = "체크리스트 완료 상태 일괄 저장")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "저장 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 검증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "체크리스트·항목 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "세션·참여자 종료 또는 리포트 잠금"
            )
    })
    public ResponseEntity<ApiResponse<ChecklistAnswerSaveResponse>> saveAnswers(
            @PathVariable
            @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @Valid @RequestBody ChecklistAnswerSaveRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        ChecklistAnswerService.SaveResult result = checklistAnswerService.saveAnswers(
                studyId,
                authenticatedMember.memberId(),
                request
        );
        return ResponseEntity
                .status(result.httpStatus())
                .body(ApiResponse.success(result.responseCode(), result.body()));
    }
}
