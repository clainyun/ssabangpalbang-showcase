package com.ssafy.ssabangpalbang.fieldvisit.stt.controller;

import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.request.SttCreateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.request.SttRetryRequest;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.response.SttCreateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.response.SttRetryResponse;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.response.SttStatusResponse;
import com.ssafy.ssabangpalbang.fieldvisit.stt.response.SttResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttService;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/studies/{studyId}/field-visit/stt")
@Tag(name = "현장 임장 STT", description = "현장 음성 기록의 STT 변환 요청과 조회")
public class SttController {

    private final SttService sttService;

    @PostMapping
    @Operation(
            summary = "STT 변환 요청",
            description = "업로드 완료된 임시 음성을 비동기 STT 작업으로 접수합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "신규 STT 작업 접수"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "동일 STT 요청이 이미 접수됨"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 값 또는 음성 파일 상태 오류"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "음성 또는 체크리스트 항목 접근 불가"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "음성 파일 또는 체크리스트 항목 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "개인 임장이 이미 종료됨"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "410",
                    description = "음성 보존 기간 만료"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "처리되지 않은 서버 오류"
            )
    })
    public ResponseEntity<ApiResponse<SttCreateResponse>> createStt(
            @PathVariable
            @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @Valid @RequestBody SttCreateRequest request
    ) {
        SttService.CreateResult result = sttService.createStt(
                studyId,
                request
        );

        return ResponseEntity
                .status(result.httpStatus())
                .body(ApiResponse.success(
                        result.responseCode(),
                        result.response()
                ));
    }

    @GetMapping("/{sttId}")
    @Operation(
            summary = "STT 진행 상태 및 결과 조회",
            description = "작성자가 접수한 STT 작업의 현재 상태와 결과를 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "STT 상태 조회 성공",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    ref = "#/components/schemas/"
                                            + "ApiResponseSttStatusResponse"
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "PROCESSING",
                                            value = """
                                                    {
                                                      "success": true,
                                                      "code": "FIELD_STT_STATUS_SUCCESS",
                                                      "message": "음성 변환 상태 조회에 성공했습니다.",
                                                      "data": {
                                                        "sttId": "stt-4c7186fb",
                                                        "studyId": 7,
                                                        "checklistItemId": 501,
                                                        "status": "PROCESSING",
                                                        "sourceId": null,
                                                        "textContent": null,
                                                        "failReason": null,
                                                        "retryable": false,
                                                        "requestedAt": "2026-07-25T14:25:00+09:00",
                                                        "completedAt": null
                                                      },
                                                      "timestamp": "2026-07-25T14:25:04+09:00"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "DONE",
                                            value = """
                                                    {
                                                      "success": true,
                                                      "code": "FIELD_STT_STATUS_SUCCESS",
                                                      "message": "음성 변환 상태 조회에 성공했습니다.",
                                                      "data": {
                                                        "sttId": "stt-4c7186fb",
                                                        "studyId": 7,
                                                        "checklistItemId": 501,
                                                        "status": "DONE",
                                                        "sourceId": 830,
                                                        "textContent": "역에서 단지 입구까지 경사가 있습니다.",
                                                        "failReason": null,
                                                        "retryable": false,
                                                        "requestedAt": "2026-07-25T14:25:00+09:00",
                                                        "completedAt": "2026-07-25T14:25:08+09:00"
                                                      },
                                                      "timestamp": "2026-07-25T14:25:10+09:00"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "FAILED",
                                            value = """
                                                    {
                                                      "success": true,
                                                      "code": "FIELD_STT_STATUS_SUCCESS",
                                                      "message": "음성 변환 상태 조회에 성공했습니다.",
                                                      "data": {
                                                        "sttId": "stt-9f2021ab",
                                                        "studyId": 7,
                                                        "checklistItemId": 503,
                                                        "status": "FAILED",
                                                        "sourceId": null,
                                                        "textContent": null,
                                                        "failReason": "인식 가능한 발화를 찾지 못했습니다.",
                                                        "retryable": true,
                                                        "requestedAt": "2026-07-25T14:27:00+09:00",
                                                        "completedAt": null
                                                      },
                                                      "timestamp": "2026-07-25T14:27:10+09:00"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "스터디 경로 불일치"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "다른 회원의 STT 작업"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "STT 작업 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "처리되지 않은 서버 또는 데이터 정합성 오류"
            )
    })
    public ResponseEntity<ApiResponse<SttStatusResponse>> getSttStatus(
            @PathVariable
            @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @PathVariable
            @NotBlank(message = "STT 작업 ID는 필수입니다.")
            String sttId
    ) {
        SttStatusResponse response = sttService.getSttStatus(studyId, sttId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(
                        SttResponseCode.FIELD_STT_STATUS_SUCCESS,
                        response
                ));
    }

    @PostMapping("/{sttId}/retry")
    @Operation(
            summary = "실패한 STT 재처리 요청",
            description = "보존 중인 원본 음성으로 기존 STT 작업을 다시 접수합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "STT 재처리 접수"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "동일 요청 또는 재처리가 이미 진행 중"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 값, 스터디 경로 또는 멱등 키 오류"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "다른 회원의 STT 작업"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "STT 작업 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "재처리 불가 상태 또는 원본 계약 불일치"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "410",
                    description = "음성 원본 삭제 또는 보존 기간 만료"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "처리되지 않은 서버 오류"
            )
    })
    public ResponseEntity<ApiResponse<SttRetryResponse>> retryStt(
            @PathVariable
            @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @PathVariable
            @NotBlank(message = "STT 작업 ID는 필수입니다.")
            String sttId,
            @Valid @RequestBody SttRetryRequest request
    ) {
        SttService.RetryResult result = sttService.retryStt(
                studyId,
                sttId,
                request
        );

        return ResponseEntity
                .status(result.httpStatus())
                .body(ApiResponse.success(
                        result.responseCode(),
                        result.response()
                ));
    }
}
