package com.ssafy.ssabangpalbang.study.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.study.dto.request.StudyScheduleCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.request.StudyScheduleUpdateRequest;
import com.ssafy.ssabangpalbang.study.dto.response.StudyResponseCode;
import com.ssafy.ssabangpalbang.study.dto.response.StudyScheduleCreateResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyScheduleDeleteResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyScheduleDetailResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyScheduleUpdateResponse;
import com.ssafy.ssabangpalbang.study.service.StudyScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/studies/{studyId}/schedule")
@RequiredArgsConstructor
@Validated
@Tag(name = "임장 일정", description = "스터디 임장 일정 등록·조회·수정·삭제")
public class StudyScheduleController {

    private final StudyScheduleService service;

    @PostMapping
    @Operation(
            summary = "임장 일정 등록",
            description = "스터디장만 임장 일정을 등록할 수 있습니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "임장 일정 등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "입력값 또는 시각 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "등록 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "스터디를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "이미 일정이 있거나 현재 상태에서 등록 불가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "일시적인 오류")
    })
    public ResponseEntity<ApiResponse<StudyScheduleCreateResponse>> create(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Parameter(description = "스터디 ID", example = "10")
            @PathVariable @Positive Long studyId,
            @Valid @RequestBody StudyScheduleCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        StudyResponseCode.STUDY_SCHEDULE_CREATE_SUCCESS,
                        service.createSchedule(member.memberId(), studyId, request)
                ));
    }

    @GetMapping
    @Operation(
            summary = "임장 일정 조회",
            description = "스터디장과 승인된 멤버가 임장 일정을 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "임장 일정 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "잘못된 스터디 ID"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "조회 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "스터디 또는 회원을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "일시적인 오류")
    })
    public ApiResponse<StudyScheduleDetailResponse> get(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Parameter(description = "스터디 ID", example = "10")
            @PathVariable @Positive Long studyId
    ) {
        return ApiResponse.success(
                StudyResponseCode.STUDY_SCHEDULE_DETAIL_SUCCESS,
                service.getSchedule(member.memberId(), studyId)
        );
    }

    @PatchMapping
    @Operation(
            summary = "임장 일정 수정",
            description = "스터디장만 임장 일정을 수정할 수 있습니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "임장 일정 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "입력값 또는 시각 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "수정 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "스터디 또는 일정을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "현재 상태에서 수정 불가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "일시적인 오류")
    })
    public ApiResponse<StudyScheduleUpdateResponse> update(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Parameter(description = "스터디 ID", example = "10")
            @PathVariable @Positive Long studyId,
            @Valid @RequestBody StudyScheduleUpdateRequest request
    ) {
        return ApiResponse.success(
                StudyResponseCode.STUDY_SCHEDULE_UPDATE_SUCCESS,
                service.updateSchedule(member.memberId(), studyId, request)
        );
    }

    @DeleteMapping
    @Operation(
            summary = "임장 일정 삭제",
            description = "스터디장만 임장 일정을 취소 상태로 변경할 수 있습니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "임장 일정 삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "잘못된 스터디 ID"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "삭제 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "스터디 또는 일정을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "현재 상태에서 삭제 불가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "일시적인 오류")
    })
    public ApiResponse<StudyScheduleDeleteResponse> delete(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Parameter(description = "스터디 ID", example = "10")
            @PathVariable @Positive Long studyId
    ) {
        return ApiResponse.success(
                StudyResponseCode.STUDY_SCHEDULE_DELETE_SUCCESS,
                service.deleteSchedule(member.memberId(), studyId)
        );
    }
}
