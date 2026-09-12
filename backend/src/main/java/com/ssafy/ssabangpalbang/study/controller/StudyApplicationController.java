package com.ssafy.ssabangpalbang.study.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyApplicationApproveResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyApplicationListResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyApplicationRejectResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyResponseCode;
import com.ssafy.ssabangpalbang.study.service.StudyApplicationDecisionService;
import com.ssafy.ssabangpalbang.study.service.StudyApplicationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/studies/{studyId}/applications")
@RequiredArgsConstructor
@Tag(name = "스터디 신청 관리", description = "스터디장의 신청자 목록 조회·승인·거절")
public class StudyApplicationController {

    private final StudyApplicationQueryService studyApplicationQueryService;
    private final StudyApplicationDecisionService studyApplicationDecisionService;

    @GetMapping
    @Operation(summary = "스터디 신청자 목록 조회", description = "스터디장만 신청자 목록을 조회할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "상태 또는 페이징 값 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "조회 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "일시적인 오류")
    })
    public ApiResponse<StudyApplicationListResponse> getApplications(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Parameter(description = "신청자 목록을 조회할 스터디 ID", example = "10") @PathVariable Long studyId,
            @Parameter(description = "신청 상태(PENDING, APPROVED, REJECTED)", example = "PENDING")
            @RequestParam(required = false) String status,
            @Parameter(description = "이 값보다 작은 신청 ID 조회", example = "25")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "조회 개수(1~100)", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(StudyResponseCode.STUDY_APPLICATION_LIST_SUCCESS,
                studyApplicationQueryService.getApplications(member.memberId(), studyId, status, cursor, size));
    }

    @PatchMapping("/{applicationId}/approve")
    @Operation(summary = "스터디 신청 승인", description = "스터디장만 대기 중인 신청을 승인할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신청 승인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "신청 소속 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "승인 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디 또는 신청을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 처리됨, 정원 초과 또는 현재 상태에서 승인 불가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "일시적인 오류")
    })
    public ApiResponse<StudyApplicationApproveResponse> approve(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Parameter(description = "신청이 접수된 스터디 ID", example = "10") @PathVariable Long studyId,
            @Parameter(description = "승인할 신청 ID", example = "25") @PathVariable Long applicationId
    ) {
        return ApiResponse.success(StudyResponseCode.STUDY_APPLICATION_APPROVE_SUCCESS,
                studyApplicationDecisionService.approve(member.memberId(), studyId, applicationId));
    }

    @PatchMapping("/{applicationId}/reject")
    @Operation(summary = "스터디 신청 거절", description = "스터디장만 대기 중인 신청을 거절할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신청 거절 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "신청 소속 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "거절 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디 또는 신청을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 처리됨 또는 현재 상태에서 거절 불가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "일시적인 오류")
    })
    public ApiResponse<StudyApplicationRejectResponse> reject(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Parameter(description = "신청이 접수된 스터디 ID", example = "10") @PathVariable Long studyId,
            @Parameter(description = "거절할 신청 ID", example = "25") @PathVariable Long applicationId
    ) {
        return ApiResponse.success(StudyResponseCode.STUDY_APPLICATION_REJECT_SUCCESS,
                studyApplicationDecisionService.reject(member.memberId(), studyId, applicationId));
    }
}
