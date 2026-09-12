package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportFavoriteResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportFavoriteResult;
import com.ssafy.ssabangpalbang.report.dto.response.ReportUnfavoriteResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportUnfavoriteResult;
import com.ssafy.ssabangpalbang.report.service.ReportFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "리포트", description = "AI 임장 리포트 조회 및 관리")
public class ReportFavoriteController {

    private final ReportFavoriteService reportFavoriteService;

    @PutMapping("/{reportId}/favorite")
    @Operation(
            summary = "리포트 찜",
            description = "로그인 회원이 완료된 리포트를 멱등하게 찜합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "리포트 찜 성공 또는 이미 찜한 상태"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "리포트 ID가 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "리포트 접근 권한 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "리포트 또는 회원을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "완료되지 않은 리포트"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류"
            )
    })
    public ApiResponse<ReportFavoriteResponse> addFavorite(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(description = "찜할 리포트 ID", example = "48")
            @PathVariable Long reportId
    ) {
        validateReportId(reportId);
        ReportFavoriteResult result = reportFavoriteService.addFavorite(
                authenticatedMember.memberId(),
                reportId
        );
        return ApiResponse.success(
                result.responseCode(),
                result.response()
        );
    }

    @DeleteMapping("/{reportId}/favorite")
    @Operation(
            summary = "리포트 찜 해제",
            description = "로그인 회원의 리포트 찜을 멱등하게 해제합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "리포트 찜 해제 성공 또는 이미 해제된 상태"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "리포트 ID가 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "리포트 또는 회원을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류"
            )
    })
    public ApiResponse<ReportUnfavoriteResponse> removeFavorite(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(description = "찜을 해제할 리포트 ID", example = "48")
            @PathVariable Long reportId
    ) {
        validateReportId(reportId);
        ReportUnfavoriteResult result = reportFavoriteService.removeFavorite(
                authenticatedMember.memberId(),
                reportId
        );
        return ApiResponse.success(
                result.responseCode(),
                result.response()
        );
    }

    private void validateReportId(Long reportId) {
        if (reportId == null || reportId <= 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of(
                            "field", "reportId",
                            "reason", "리포트 ID는 1 이상의 숫자여야 합니다."
                    )
            );
        }
    }
}
