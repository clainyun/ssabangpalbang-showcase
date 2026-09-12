package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportResponseCode;
import com.ssafy.ssabangpalbang.report.dto.response.ReportStatusResponse;
import com.ssafy.ssabangpalbang.report.service.ReportStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "리포트", description = "AI 임장 리포트 조회 및 관리")
public class ReportStatusController {

    private final ReportStatusService reportStatusService;

    @GetMapping("/{reportId}/status")
    @Operation(
            summary = "리포트 생성 상태 조회",
            description = "리포트 생성 상태와 현재 단계, 진행률 및 실패 정보를 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "리포트 생성 상태 조회 성공"
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
                    description = "진행 중이거나 실패한 리포트의 조회 권한 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "리포트 또는 활성 회원을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류"
            )
    })
    public ApiResponse<ReportStatusResponse> getStatus(
            @AuthenticationPrincipal
            AuthenticatedMember authenticatedMember,
            @Parameter(
                    description = "상태를 조회할 리포트 ID",
                    required = true,
                    example = "48",
                    schema = @Schema(
                            type = "integer",
                            format = "int64",
                            minimum = "1"
                    )
            )
            @PathVariable String reportId
    ) {
        Long parsedReportId = parseReportId(reportId);
        return ApiResponse.success(
                ReportResponseCode.REPORT_STATUS_SUCCESS,
                reportStatusService.getStatus(
                        authenticatedMember.memberId(),
                        parsedReportId
                )
        );
    }

    private Long parseReportId(String reportId) {
        try {
            long parsedReportId = Long.parseLong(reportId);
            if (parsedReportId >= 1) {
                return parsedReportId;
            }
        } catch (NumberFormatException ignored) {
            // The common contract below handles every invalid path value.
        }

        throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                Map.of(
                        "field", "reportId",
                        "reason", "리포트 ID는 1 이상의 숫자여야 합니다."
                )
        );
    }
}
