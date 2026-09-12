package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportEvidenceDetailResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportEvidenceListResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportResponseCode;
import com.ssafy.ssabangpalbang.report.service.ReportEvidenceListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "리포트", description = "AI 임장 리포트 조회 및 관리")
public class ReportEvidenceController {

    private final ReportEvidenceListService reportEvidenceListService;

    @GetMapping("/{reportId}/evidences/{sourceId}")
    @Operation(
            summary = "리포트 근거 원문 조회",
            description = "완료 임장의 TEXT, DONE STT 또는 PHOTO 원문을 "
                    + "조회합니다. AI에 사용되지 않은 원문도 조회할 수 있으며 "
                    + "이 경우 usedIn은 빈 배열입니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "리포트 근거 원문 조회 성공",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    ref = "#/components/schemas/"
                                            + "ApiResponseReportEvidenceDetailResponse"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "경로가 올바르지 않거나 다른 리포트의 근거",
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
                    description = "해당 임장 세션 참여자가 아님",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "리포트, 근거 또는 활성 회원을 찾을 수 없음",
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
                    responseCode = "410",
                    description = "사진 원본이 삭제·만료되어 접근할 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "파일 저장소에 일시적으로 연결할 수 없음",
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
    public ResponseEntity<ApiResponse<ReportEvidenceDetailResponse>>
            getEvidence(
                    @AuthenticationPrincipal
                    AuthenticatedMember authenticatedMember,
                    @Parameter(
                            description = "원문 범위와 접근 권한을 결정할 리포트 ID",
                            required = true,
                            example = "48",
                            schema = @Schema(
                                    type = "integer",
                                    format = "int64",
                                    minimum = "1"
                            )
                    )
                    @PathVariable String reportId,
                    @Parameter(
                            description = "조회할 field_record ID",
                            required = true,
                            example = "201",
                            schema = @Schema(
                                    type = "integer",
                                    format = "int64",
                                    minimum = "1"
                            )
                    )
                    @PathVariable String sourceId
            ) {
        ApiResponse<ReportEvidenceDetailResponse> response =
                ApiResponse.success(
                        ReportResponseCode.REPORT_EVIDENCE_DETAIL_SUCCESS,
                        reportEvidenceListService.getEvidenceDetail(
                                authenticatedMember.memberId(),
                                parseReportId(reportId),
                                parseSourceId(sourceId)
                        )
                );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping("/{reportId}/evidences")
    @Operation(
            summary = "리포트 근거 목록 조회",
            description = "완료 리포트에 AI-006이 직접 연결한 TEXT·STT "
                    + "근거를 익명 목록으로 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "리포트 근거 목록 조회 성공",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    ref = "#/components/schemas/"
                                            + "ApiResponseReportEvidenceListResponse"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "경로·필터가 올바르지 않거나 다른 리포트 근거가 포함됨",
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
                    description = "해당 임장 세션 참여자가 아님",
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
    public ApiResponse<ReportEvidenceListResponse> getEvidences(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(
                    description = "근거를 조회할 리포트 ID",
                    required = true,
                    example = "48",
                    schema = @Schema(
                            type = "integer",
                            format = "int64",
                            minimum = "1"
                    )
            )
            @PathVariable String reportId,
            @Parameter(
                    description = "근거 유형 필터",
                    schema = @Schema(
                            allowableValues = {"TEXT", "PHOTO", "STT"}
                    )
            )
            @RequestParam(required = false) String sourceType,
            @Parameter(
                    description = "동적 체크리스트 카테고리, 1~30자",
                    example = "교통"
            )
            @RequestParam(required = false) String category,
            @Parameter(
                    description = "쉼표로 구분한 sourceId, 최대 100개",
                    example = "201,205,214"
            )
            @RequestParam(required = false) String sourceIds,
            @Parameter(
                    description = "직전 페이지 마지막 sourceId",
                    schema = @Schema(
                            type = "integer",
                            format = "int64",
                            minimum = "1"
                    )
            )
            @RequestParam(required = false) String cursor,
            @Parameter(
                    description = "조회 개수",
                    schema = @Schema(
                            type = "integer",
                            defaultValue = "20",
                            minimum = "1",
                            maximum = "100"
                    )
            )
            @RequestParam(required = false) String size
    ) {
        return ApiResponse.success(
                ReportResponseCode.REPORT_EVIDENCE_LIST_SUCCESS,
                reportEvidenceListService.getEvidenceList(
                        authenticatedMember.memberId(),
                        parseReportId(reportId),
                        sourceType,
                        category,
                        sourceIds,
                        cursor,
                        size
                )
        );
    }

    private Long parseReportId(String reportId) {
        try {
            long parsed = Long.parseLong(reportId);
            if (parsed >= 1) {
                return parsed;
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

    private Long parseSourceId(String sourceId) {
        try {
            long parsed = Long.parseLong(sourceId);
            if (parsed >= 1) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // The common contract below handles every invalid path value.
        }
        throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                Map.of(
                        "field", "sourceId",
                        "reason", "sourceId는 1 이상의 숫자여야 합니다."
                )
        );
    }
}
