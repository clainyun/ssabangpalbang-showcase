package com.ssafy.ssabangpalbang.study.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.study.dto.request.StudyNoticeCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.request.StudyNoticeUpdateRequest;
import com.ssafy.ssabangpalbang.study.dto.response.StudyNoticeDeleteResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyNoticeListResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyNoticeResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyResponseCode;
import com.ssafy.ssabangpalbang.study.service.StudyNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/studies/{studyId}/notices")
@RequiredArgsConstructor
@Validated
@Tag(name = "스터디 공지", description = "스터디 공지 등록·조회·수정·삭제")
public class StudyNoticeController {
    private final StudyNoticeService studyNoticeService;

    @PostMapping
    @Operation(summary = "스터디 공지 등록", description = "스터디장만 공지를 등록할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "공지 등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "공지 내용이 비어 있음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "등록 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 상태에서 등록 불가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "일시적인 오류")
    })
    public ResponseEntity<ApiResponse<StudyNoticeResponse>> create(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long studyId,
            @Valid @RequestBody StudyNoticeCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                StudyResponseCode.STUDY_NOTICE_CREATE_SUCCESS,
                studyNoticeService.createNotice(member.memberId(), studyId, request)));
    }

    @GetMapping
    @Operation(summary = "스터디 공지 목록 조회", description = "스터디장과 승인된 멤버가 공지를 최신순으로 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "페이징 값 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "조회 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "일시적인 오류")
    })
    public ApiResponse<StudyNoticeListResponse> list(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long studyId,
            @Parameter(description = "이 값보다 작은 공지 ID 조회", example = "18")
            @RequestParam(required = false) @Positive Long cursor,
            @Parameter(description = "조회 개수(1~100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size
    ) {
        return ApiResponse.success(
                StudyResponseCode.STUDY_NOTICE_LIST_SUCCESS,
                studyNoticeService.getNotices(member.memberId(), studyId, cursor, size));
    }

    @PatchMapping("/{noticeId}")
    @Operation(summary = "스터디 공지 수정", description = "스터디장만 공지를 수정할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "공지 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 또는 공지 소속 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "수정 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디 또는 공지를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 상태에서 수정 불가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "일시적인 오류")
    })
    public ApiResponse<StudyNoticeResponse> update(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long studyId,
            @PathVariable Long noticeId,
            @Valid @RequestBody StudyNoticeUpdateRequest request
    ) {
        return ApiResponse.success(
                StudyResponseCode.STUDY_NOTICE_UPDATE_SUCCESS,
                studyNoticeService.updateNotice(member.memberId(), studyId, noticeId, request));
    }

    @DeleteMapping("/{noticeId}")
    @Operation(summary = "스터디 공지 삭제", description = "스터디장만 공지를 삭제할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "공지 삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "공지 소속 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "삭제 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디 또는 공지를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 삭제되었거나 현재 상태에서 삭제 불가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "일시적인 오류")
    })
    public ApiResponse<StudyNoticeDeleteResponse> delete(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long studyId,
            @PathVariable Long noticeId
    ) {
        return ApiResponse.success(
                StudyResponseCode.STUDY_NOTICE_DELETE_SUCCESS,
                studyNoticeService.deleteNotice(member.memberId(), studyId, noticeId));
    }
}
