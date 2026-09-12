package com.ssafy.ssabangpalbang.fieldvisit.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistDetailResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistGenerationStatusResponse;
import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistService;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/studies/{studyId}/field-visit/checklist")
@Tag(name = "임장 체크리스트", description = "개인 맞춤 AI 체크리스트 생성·조회")
public class ChecklistController {

    private static final String GENERATION_ATTEMPT_HEADER =
            "X-Checklist-Generation-Attempt-Id";

    private final ChecklistService checklistService;

    @PostMapping("/generate")
    @Operation(summary = "개인 맞춤 체크리스트 생성")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "신규 AI 또는 fallback 체크리스트 생성"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "이미 생성된 체크리스트 반환"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "생성 권한 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "스터디 없음 또는 임장 미시작"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "세션 또는 참여자 종료"
            )
    })
    public ResponseEntity<ApiResponse<ChecklistGenerateResponse>> generate(
            @PathVariable
            @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @RequestHeader(name = GENERATION_ATTEMPT_HEADER, required = false)
            @Size(max = 64, message = "생성 시도 ID는 64자 이하여야 합니다.")
            String attemptId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        ChecklistService.GenerateResult result = checklistService.generate(
                studyId,
                authenticatedMember.memberId(),
                attemptId
        );
        return ResponseEntity
                .status(result.httpStatus())
                .body(ApiResponse.success(result.responseCode(), result.body()));
    }

    @GetMapping("/generate/status")
    @Operation(summary = "개인 맞춤 체크리스트 생성 상태 조회")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "생성 상태 조회 성공(임장 미시작이면 PENDING)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "조회 권한 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "스터디 없음"
            )
    })
    public ResponseEntity<ApiResponse<ChecklistGenerationStatusResponse>> getGenerationStatus(
            @PathVariable
            @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @RequestHeader(name = GENERATION_ATTEMPT_HEADER, required = false)
            @Size(max = 64, message = "생성 시도 ID는 64자 이하여야 합니다.")
            String attemptId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        ChecklistGenerationStatusResponse body = checklistService.getGenerationStatus(
                studyId,
                authenticatedMember.memberId(),
                attemptId
        );
        return ResponseEntity.ok(ApiResponse.success(
                ChecklistResponseCode.CHECKLIST_GENERATION_STATUS_SUCCESS,
                body
        ));
    }

    @GetMapping
    @Operation(summary = "내 체크리스트 조회")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공(미생성이면 checklist=null)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "조회 권한 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "스터디 없음"
            )
    })
    public ResponseEntity<ApiResponse<ChecklistDetailResponse>> getChecklist(
            @PathVariable
            @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        ChecklistDetailResponse body = checklistService.getChecklist(
                studyId,
                authenticatedMember.memberId()
        );
        return ResponseEntity.ok(
                ApiResponse.success(ChecklistResponseCode.CHECKLIST_DETAIL_SUCCESS, body)
        );
    }
}
