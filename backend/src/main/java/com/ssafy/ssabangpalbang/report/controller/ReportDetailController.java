package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportDetailResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportResponseCode;
import com.ssafy.ssabangpalbang.report.service.ReportDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "리포트", description = "AI 임장 리포트 조회 및 관리")
public class ReportDetailController {

    private final ReportDetailService reportDetailService;

    @GetMapping("/{reportId}")
    @Operation(
            summary = "리포트 상세 조회",
            description = "생성이 완료된 스토리형 AI 임장 리포트의 상세 결과를 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
             @io.swagger.v3.oas.annotations.responses.ApiResponse(
                     responseCode = "200",
                     description = "리포트 상세 조회 성공",
                     useReturnTypeSchema = true,
                     content = @Content(
                             mediaType = MediaType.APPLICATION_JSON_VALUE,
                             schema = @Schema(
                                     ref = "#/components/schemas/"
                                             + "ApiResponseReportDetailResponse"
                             )
                     )
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
                     description = "삭제·취소된 스터디 또는 미완료 리포트 상태에 접근할 수 없음",
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
                     description = "리포트 생성 미완료 또는 생성 실패",
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
    public ApiResponse<ReportDetailResponse> getDetail(
            @AuthenticationPrincipal
            AuthenticatedMember authenticatedMember,
            @Parameter(
                    description = "상세 조회할 리포트 ID",
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
                ReportResponseCode.REPORT_DETAIL_SUCCESS,
                reportDetailService.getDetail(
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
