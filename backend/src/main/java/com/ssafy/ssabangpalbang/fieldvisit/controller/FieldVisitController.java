package com.ssafy.ssabangpalbang.fieldvisit.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitCloseRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitFinishCancelRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitFinishRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitStartRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitCloseResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitCloseVoteResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitFinishCancelResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitFinishResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitParticipantsResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitStartResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitStatusResponse;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitCloseService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitCloseVoteService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitFinishCancelService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitFinishService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitParticipantsService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitStartService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitStatusService;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/studies/{studyId}/field-visit")
@Tag(
        name = "임장 세션",
        description = "임장 세션 상태 조회와 GPS 검증 후 임장 시작(BE-014), "
                + "개인 종료·전체 마감·과반수 종료 요청(BE-018/BE-018-1)"
)
public class FieldVisitController {

    private final FieldVisitStatusService fieldVisitStatusService;
    private final FieldVisitParticipantsService fieldVisitParticipantsService;
    private final FieldVisitStartService fieldVisitStartService;
    private final FieldVisitFinishService fieldVisitFinishService;
    private final FieldVisitFinishCancelService fieldVisitFinishCancelService;
    private final FieldVisitCloseService fieldVisitCloseService;
    private final FieldVisitCloseVoteService fieldVisitCloseVoteService;

    @GetMapping
    @Operation(
            summary = "임장 세션 상태 조회",
            description = "세션·본인 참여·체크리스트 진행·기능 권한을 조회한다. "
                    + "세션이 없으면 status=NOT_STARTED, session/participant=null."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    ref = "#/components/schemas/"
                                            + "ApiResponseFieldVisitStatusResponse"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 studyId",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "미인증",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "승인 멤버 아님",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "스터디 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "일시적인 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
    public ResponseEntity<ApiResponse<FieldVisitStatusResponse>> getStatus(
            @Parameter(description = "스터디 ID", example = "7")
            @PathVariable @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        FieldVisitStatusService.StatusResult result =
                fieldVisitStatusService.getStatus(
                        studyId, authenticatedMember.memberId()
                );
        return ResponseEntity.ok(
                ApiResponse.success(result.responseCode(), result.body())
        );
    }

    @GetMapping("/participants")
    @Operation(
            summary = "참여자별 임장 상태 조회",
            description = "세션에 고정된 참여 후보 전원의 임장 상태(NOT_JOINED/IN_PROGRESS/"
                    + "ENDED)·시작·종료 시각·체류시간·종료 사유를 조회한다. "
                    + "세션이 없으면 status=NOT_STARTED, 빈 participants."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    ref = "#/components/schemas/"
                                            + "ApiResponseFieldVisitParticipantsResponse"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 studyId",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "미인증",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "승인 멤버 아님",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "스터디 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "일시적인 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
    public ResponseEntity<ApiResponse<FieldVisitParticipantsResponse>> getParticipants(
            @Parameter(description = "스터디 ID", example = "7")
            @PathVariable @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        FieldVisitParticipantsService.ParticipantsResult result =
                fieldVisitParticipantsService.getParticipants(
                        studyId, authenticatedMember.memberId()
                );
        return ResponseEntity.ok(
                ApiResponse.success(result.responseCode(), result.body())
        );
    }

    @PostMapping("/start")
    @Operation(
            summary = "GPS 검증 후 임장 시작",
            description = "허용 반경 안에서 세션·참여자를 시작한다. "
                    + "최초 참여자 생성은 201, 동일/추가 멱등 재요청은 200."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "최초 임장 시작",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    ref = "#/components/schemas/"
                                            + "ApiResponseFieldVisitStartResponse"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "이미 시작한 상태 또는 동일 요청 재전송",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    ref = "#/components/schemas/"
                                            + "ApiResponseFieldVisitStartResponse"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "위치/UUID/멱등 키 충돌",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "미인증",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 또는 시작 조건 미충족",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "스터디 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "세션·참여자 종료",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422",
                    description = "허용 반경 밖",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "일시적인 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
    public ResponseEntity<ApiResponse<FieldVisitStartResponse>> start(
            @Parameter(description = "스터디 ID", example = "7")
            @PathVariable @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @Valid @RequestBody FieldVisitStartRequestBody request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        FieldVisitStartService.StartResult result = fieldVisitStartService.start(
                studyId, authenticatedMember.memberId(), request
        );
        return ResponseEntity.status(result.httpStatus())
                .body(ApiResponse.success(result.responseCode(), result.body()));
    }

    @PostMapping("/finish")
    @Operation(
            summary = "개인 임장 종료",
            description = "본인의 임장을 종료하고 마지막 참여자라면 세션을 종료해 "
                    + "리포트 생성 이벤트를 발행한다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "개인 종료 성공 / 세션까지 종료 / 이미 종료됨",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    ref = "#/components/schemas/"
                                            + "ApiResponseFieldVisitFinishResponse"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 studyId, 확인 누락 또는 요청 ID 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "미인증",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "ACTIVE 멤버 또는 세션 참여자 아님",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "스터디 또는 임장 세션 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "일시적인 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
    public ResponseEntity<ApiResponse<FieldVisitFinishResponse>> finish(
            @Parameter(description = "스터디 ID", example = "7")
            @PathVariable @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @Valid @RequestBody FieldVisitFinishRequestBody request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        FieldVisitFinishService.FinishResult result =
                fieldVisitFinishService.finish(
                        studyId,
                        authenticatedMember.memberId(),
                        request
                );
        return ResponseEntity.ok(
                ApiResponse.success(result.responseCode(), result.body())
        );
    }

    @PostMapping("/finish/cancel")
    @Operation(
            summary = "개인 임장 종료 취소",
            description = "본인이 직접 종료한 임장을 다시 진행 중으로 되돌린다. "
                    + "세션이 이미 종료되어 리포트 생성이 시작되었으면 취소할 수 없다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "종료 취소 성공 또는 이미 진행 중",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    ref = "#/components/schemas/"
                                            + "ApiResponseFieldVisitFinishCancelResponse"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 studyId 또는 요청 ID 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "미인증",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "ACTIVE 멤버 또는 세션 참여자 아님",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "스터디 또는 임장 세션 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "세션 종료됨 또는 본인이 직접 종료한 임장이 아님",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "일시적인 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
    public ResponseEntity<ApiResponse<FieldVisitFinishCancelResponse>> finishCancel(
            @Parameter(description = "스터디 ID", example = "7")
            @PathVariable @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @Valid @RequestBody FieldVisitFinishCancelRequestBody request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        FieldVisitFinishCancelService.FinishCancelResult result =
                fieldVisitFinishCancelService.cancel(
                        studyId,
                        authenticatedMember.memberId(),
                        request
                );
        return ResponseEntity.ok(
                ApiResponse.success(result.responseCode(), result.body())
        );
    }

    @PostMapping("/close")
    @Operation(
            summary = "스터디장 전체 임장 마감",
            description = "스터디장이 미종료 참여자를 강제 종료하고 세션을 마감해 "
                    + "리포트 생성 이벤트를 발행한다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "전체 마감 성공 또는 이미 마감됨",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    ref = "#/components/schemas/"
                                            + "ApiResponseFieldVisitCloseResponse"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 studyId, 확인 누락 또는 요청 ID 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "미인증",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "ACTIVE 멤버 또는 스터디장 아님",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "스터디 또는 임장 세션 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "일시적인 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
    public ResponseEntity<ApiResponse<FieldVisitCloseResponse>> close(
            @Parameter(description = "스터디 ID", example = "7")
            @PathVariable @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @Valid @RequestBody FieldVisitCloseRequestBody request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        FieldVisitCloseService.CloseResult result =
                fieldVisitCloseService.close(
                        studyId,
                        authenticatedMember.memberId(),
                        request
                );
        return ResponseEntity.ok(
                ApiResponse.success(result.responseCode(), result.body())
        );
    }

    @PostMapping("/close-votes")
    @Operation(
            summary = "전체 임장 종료 요청(과반수 투표)",
            description = "실제 임장을 시작한 참여자가 전체 임장 종료에 동의한다. "
                    + "과반수에 도달하면 공용 세션을 MAJORITY_FORCED로 종료한다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "투표 반영 또는 과반수 종료",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "투표 자격 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "스터디 또는 임장 세션 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
    public ResponseEntity<ApiResponse<FieldVisitCloseVoteResponse>> voteClose(
            @Parameter(description = "스터디 ID", example = "7")
            @PathVariable @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        FieldVisitCloseVoteService.VoteResult result =
                fieldVisitCloseVoteService.vote(
                        studyId,
                        authenticatedMember.memberId()
                );
        return ResponseEntity.ok(
                ApiResponse.success(result.responseCode(), result.body())
        );
    }
}
