package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.report.dto.request.ReportAcquireRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportCompleteRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportFailRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportProgressRequest;
import com.ssafy.ssabangpalbang.report.dto.response.ReportAcquireResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportInputResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportResponseCode;
import com.ssafy.ssabangpalbang.report.service.ReportAcquireService;
import com.ssafy.ssabangpalbang.report.service.ReportCompleteService;
import com.ssafy.ssabangpalbang.report.service.ReportFailService;
import com.ssafy.ssabangpalbang.report.service.ReportInputService;
import com.ssafy.ssabangpalbang.report.service.ReportProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/reports")
@RequiredArgsConstructor
@Validated
@Tag(
        name = "내부 리포트 Worker",
        description = "AI Report Worker와 Backend 사이의 내부 전용 API"
)
public class ReportInternalController {

    private final ReportAcquireService reportAcquireService;
    private final ReportProgressService reportProgressService;
    private final ReportInputService reportInputService;
    private final ReportCompleteService reportCompleteService;
    private final ReportFailService reportFailService;

    @PostMapping("/acquire")
    @Operation(
            summary = "AI 리포트 처리권 획득",
            description = "스터디당 하나의 리포트를 create-or-get하고, "
                    + "Backend Lease 기반 처리권을 원자적으로 판정합니다. "
                    + "사용자 Access Token은 허용하지 않습니다."
    )
    @SecurityRequirement(name = "internalBearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "처리권 판정 성공",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    ref = "#/components/schemas/"
                                            + "ApiResponseReportAcquireResponse"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수값 누락 또는 요청 형식 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "내부 Token 누락 또는 불일치",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "내부 API 접근 권한 없음",
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
    public ResponseEntity<ApiResponse<ReportAcquireResponse>> acquire(
            @Valid @RequestBody ReportAcquireRequest request
    ) {
        ApiResponse<ReportAcquireResponse> response = ApiResponse.success(
                ReportResponseCode.REPORT_ACQUIRE_SUCCESS,
                reportAcquireService.acquire(request)
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping("/{reportId}/input")
    @Operation(
            summary = "AI 리포트 정규화 입력 원본 조회",
            description = "V16의 권위 있는 임장 세션을 기준으로 AI-004가 "
                    + "정규화할 참여자·체크리스트·현장 기록·미완료 STT "
                    + "스냅샷을 조회합니다. 사용자 Access Token과 "
                    + "processingToken은 사용하지 않습니다."
    )
    @SecurityRequirement(name = "internalBearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "리포트 정규화 입력 원본 조회 성공",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    ref = "#/components/schemas/"
                                            + "ApiResponseReportInputResponse"
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
                    description = "내부 Token 누락 또는 불일치",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "내부 API 접근 권한 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "리포트 또는 권위 있는 원본 관계 없음",
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
    public ResponseEntity<ApiResponse<ReportInputResponse>> getInput(
            @Parameter(
                    description = "조회할 리포트 ID",
                    required = true,
                    example = "48",
                    schema = @Schema(
                            type = "integer",
                            format = "int64",
                            minimum = "1"
                    )
            )
            @PathVariable @Positive Long reportId
    ) {
        ApiResponse<ReportInputResponse> response = ApiResponse.success(
                ReportResponseCode.REPORT_INPUT_SUCCESS,
                reportInputService.getInput(reportId)
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PatchMapping("/{reportId}/progress")
    @Operation(
            summary = "AI 리포트 진행 단계 저장",
            description = "현재 처리권의 Token·Attempt·Lease를 검증하고 "
                    + "진행 단계를 저장한 뒤 Lease를 전체 TTL로 갱신합니다. "
                    + "사용자 Access Token은 허용하지 않습니다."
    )
    @SecurityRequirement(name = "internalBearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204",
                    description = "진행 단계 저장 및 Lease 갱신 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수값 누락 또는 요청 형식 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "내부 Token 누락 또는 불일치",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "내부 API 접근 권한 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "리포트 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "처리권 또는 진행 단계 전이 충돌",
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
    public ResponseEntity<Void> updateProgress(
            @PathVariable @Positive Long reportId,
            @Valid @RequestBody ReportProgressRequest request
    ) {
        reportProgressService.updateProgress(reportId, request);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @PutMapping("/{reportId}/complete")
    @Operation(
            summary = "AI 리포트 결과·근거 완료 저장",
            description = "현재 처리권을 검증한 뒤 AI-005 결과와 검증된 "
                    + "AI-006 TEXT·STT 근거를 원자적으로 저장하고 "
                    + "리포트를 DONE·COMPLETED로 전환합니다. 동일 완료 "
                    + "요청의 재전달은 멱등 처리합니다."
    )
    @SecurityRequirement(name = "internalBearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204",
                    description = "리포트 결과·근거 완료 저장 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 또는 권위 집계 검증 실패",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "내부 Token 누락 또는 불일치",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "내부 API 접근 권한 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "리포트 또는 권위 원본 관계 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "처리권 만료·불일치 또는 완료 payload 충돌",
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
    public ResponseEntity<Void> complete(
            @Parameter(
                    description = "완료할 리포트 ID",
                    required = true,
                    example = "48",
                    schema = @Schema(
                            type = "integer",
                            format = "int64",
                            minimum = "1"
                    )
            )
            @PathVariable @Positive Long reportId,
            @Valid @RequestBody ReportCompleteRequest request
    ) {
        reportCompleteService.complete(reportId, request);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @PutMapping("/{reportId}/fail")
    @Operation(
            summary = "AI 리포트 실패 정보 저장",
            description = "현재 처리권을 검증한 뒤 allowlist 실패 코드와 "
                    + "고정 안전 문구를 저장하고 리포트를 FAILED로 "
                    + "전환합니다. 동일 실패 명령의 재전달은 멱등 "
                    + "처리합니다."
    )
    @SecurityRequirement(name = "internalBearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204",
                    description = "리포트 실패 정보 저장 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 또는 안전 실패 문구 검증 실패",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "내부 Token 누락 또는 불일치",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "내부 API 접근 권한 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "리포트 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "처리권 만료·불일치 또는 실패 payload 충돌",
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
    public ResponseEntity<Void> fail(
            @Parameter(
                    description = "실패 처리할 리포트 ID",
                    required = true,
                    example = "48",
                    schema = @Schema(
                            type = "integer",
                            format = "int64",
                            minimum = "1"
                    )
            )
            @PathVariable @Positive Long reportId,
            @Valid @RequestBody ReportFailRequest request
    ) {
        reportFailService.fail(reportId, request);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
