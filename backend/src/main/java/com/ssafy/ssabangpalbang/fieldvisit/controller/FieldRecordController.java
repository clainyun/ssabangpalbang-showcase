package com.ssafy.ssabangpalbang.fieldvisit.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldRecordResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldRecordCreateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldRecordUpdateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldRecordCreateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldRecordListResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldRecordMutationResponse;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldRecordService;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/studies/{studyId}/field-visit/records")
@Tag(name = "임장 현장 기록", description = "체크리스트 항목별 TEXT/PHOTO 현장 기록 CRUD. STT 생성은 BE-016.")
public class FieldRecordController {

    private final FieldRecordService fieldRecordService;

    @PostMapping
    @Operation(
            summary = "현장 기록 저장",
            description = "TEXT/PHOTO만 생성한다. 동일 clientRequestId+fingerprint 재전송은 200으로 기존 결과를 반환한다. "
                    + "동일 clientRequestId를 다른 요청에 재사용하면 400 FIELD_RECORD_IDEMPOTENCY_KEY_REUSED."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "신규 현장 기록 저장",
                    content = @Content(schema = @Schema(implementation = FieldRecordCreateResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "동일 요청 재전송 시 기존 결과 반환"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청값 검증 실패, 허용되지 않는 sourceType, 동일 clientRequestId를 다른 요청에 재사용"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "미인증"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "항목 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "임장·참여자 종료, 리포트 생성 이후 잠금 등 상태 충돌"
            )
    })
    public ResponseEntity<ApiResponse<FieldRecordCreateResponse>> create(
            @Parameter(description = "스터디 ID", example = "7")
            @PathVariable @Positive(message = "스터디 ID는 1 이상이어야 합니다.") Long studyId,
            @Valid @RequestBody FieldRecordCreateRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        FieldRecordService.CreateResult result = fieldRecordService.create(
                studyId, authenticatedMember.memberId(), request
        );
        return ResponseEntity.status(result.httpStatus())
                .body(ApiResponse.success(result.responseCode(), result.body()));
    }

    @GetMapping
    @Operation(summary = "현장 기록 목록 조회")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "파라미터 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "미인증"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음")
    })
    public ResponseEntity<ApiResponse<FieldRecordListResponse>> list(
            @PathVariable @Positive(message = "스터디 ID는 1 이상이어야 합니다.") Long studyId,
            @Parameter(description = "체크리스트 항목 필터")
            @RequestParam(required = false) @Positive Long checklistItemId,
            @Parameter(description = "기록 유형", schema = @Schema(allowableValues = {"TEXT", "PHOTO", "STT"}))
            @RequestParam(required = false) String sourceType,
            @Parameter(description = "본인만 조회", schema = @Schema(defaultValue = "true"))
            @RequestParam(required = false, defaultValue = "true") Boolean mineOnly,
            @Parameter(description = "keyset cursor = sourceId")
            @RequestParam(required = false) @Positive Long cursor,
            @Parameter(description = "페이지 크기", schema = @Schema(defaultValue = "20", minimum = "1", maximum = "50"))
            @RequestParam(required = false, defaultValue = "20")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 50, message = "size는 50 이하여야 합니다.")
            Integer size,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        FieldRecordListResponse body = fieldRecordService.list(
                studyId,
                authenticatedMember.memberId(),
                checklistItemId,
                sourceType,
                mineOnly,
                cursor,
                size
        );
        return ResponseEntity.ok(
                ApiResponse.success(FieldRecordResponseCode.FIELD_RECORD_LIST_SUCCESS, body)
        );
    }

    @PatchMapping("/{recordId}")
    @Operation(summary = "현장 기록 수정", description = "응답 식별자는 sourceId다. Path의 recordId는 field_record.id와 동일하다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "미인증"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "기록 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "상태 충돌")
    })
    public ResponseEntity<ApiResponse<FieldRecordMutationResponse>> update(
            @PathVariable @Positive Long studyId,
            @Parameter(description = "field_record.id (=sourceId)")
            @PathVariable @Positive(message = "기록 ID는 1 이상이어야 합니다.") Long recordId,
            @Valid @RequestBody FieldRecordUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        FieldRecordMutationResponse body = fieldRecordService.update(
                studyId, authenticatedMember.memberId(), recordId, request
        );
        return ResponseEntity.ok(
                ApiResponse.success(FieldRecordResponseCode.FIELD_RECORD_UPDATE_SUCCESS, body)
        );
    }

    @DeleteMapping("/{recordId}")
    @Operation(summary = "현장 기록 삭제", description = "soft delete. 이미 삭제된 기록은 멱등 200.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공 또는 멱등"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "미인증"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "기록 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "상태 충돌")
    })
    public ResponseEntity<ApiResponse<FieldRecordMutationResponse>> delete(
            @PathVariable @Positive Long studyId,
            @PathVariable @Positive(message = "기록 ID는 1 이상이어야 합니다.") Long recordId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        FieldRecordService.MutationResult result = fieldRecordService.delete(
                studyId, authenticatedMember.memberId(), recordId
        );
        return ResponseEntity.status(result.httpStatus())
                .body(ApiResponse.success(result.responseCode(), result.body()));
    }
}
