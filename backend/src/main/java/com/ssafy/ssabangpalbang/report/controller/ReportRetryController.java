package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportRetryResponse;
import com.ssafy.ssabangpalbang.report.service.ReportRetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "리포트", description = "AI 임장 리포트 조회 및 관리")
public class ReportRetryController {

    private final ReportRetryService reportRetryService;

    @PostMapping("/{reportId}/retry")
    @Operation(
            summary = "실패한 리포트 재생성 요청",
            description = "스터디장이 재시도 가능한 실패 리포트의 비동기 재생성을 요청합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "리포트 재생성 요청 수락",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "리포트 재생성이 이미 진행 중",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "리포트 ID가 올바르지 않음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "리포트 재생성 권한 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "리포트 또는 활성 회원을 찾을 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "재생성 불가 상태 또는 원본 데이터 부족",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
    public ResponseEntity<ApiResponse<ReportRetryResponse>> retry(
            @AuthenticationPrincipal
            AuthenticatedMember authenticatedMember,
            @Parameter(
                    description = "재생성할 리포트 ID",
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
        ReportRetryService.RetryResult result = reportRetryService.retry(
                authenticatedMember.memberId(),
                parsedReportId
        );
        return ResponseEntity.status(result.httpStatus())
                .body(ApiResponse.success(
                        result.responseCode(),
                        result.response()
                ));
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
