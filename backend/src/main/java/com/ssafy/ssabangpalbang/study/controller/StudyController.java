package com.ssafy.ssabangpalbang.study.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.study.dto.request.StudyCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.request.StudyApplicationCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.request.StudyUpdateRequest;
import com.ssafy.ssabangpalbang.study.dto.response.StudyApplicationCreateResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyCreateResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyResponseCode;
import com.ssafy.ssabangpalbang.study.dto.response.StudyDetailResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyMemberListResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyRecruitmentCloseResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyMemberKickResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyMemberLeaveResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyUpdateResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyCancelResponse;
import com.ssafy.ssabangpalbang.study.service.StudyCancelService;
import com.ssafy.ssabangpalbang.study.service.StudyRecruitmentService;
import com.ssafy.ssabangpalbang.study.service.StudyMemberCommandService;
import com.ssafy.ssabangpalbang.study.service.StudyMemberQueryService;
import com.ssafy.ssabangpalbang.study.service.StudyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/studies")
@RequiredArgsConstructor
@Tag(name = "스터디", description = "임장 스터디 생성·신청과 상세 조회")
public class StudyController {

    private final StudyService studyService;
    private final ObjectProvider<StudyMemberQueryService> studyMemberQueryServiceProvider;
    private final ObjectProvider<StudyRecruitmentService> studyRecruitmentServiceProvider;
    private final ObjectProvider<StudyMemberCommandService> studyMemberCommandServiceProvider;
    private final ObjectProvider<StudyCancelService> studyCancelServiceProvider;

    @PostMapping
    @Operation(
            summary = "스터디 생성",
            description = "로그인 회원을 스터디장으로 등록합니다. 임장 일정은 별도 API로 등록합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "스터디 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수 항목 누락 · 최대 참여 인원 범위 초과 · 허용되지 않은 스터디 목적"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "아파트 또는 회원 정보를 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<StudyCreateResponse>> create(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody StudyCreateRequest request
    ) {
        StudyCreateResponse response =
                studyService.create(authenticatedMember.memberId(), request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(StudyResponseCode.STUDY_CREATE_SUCCESS, response));
    }

    @PostMapping("/{studyId}/applications")
    @Operation(
            summary = "스터디 참여 신청",
            description = "모집 중인 스터디에 자기소개와 참여 목적을 제출합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "신청 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청값 또는 신청 목적 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "회원 또는 스터디를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "중복 신청, 이미 참여 중, 신청 불가 또는 정원 마감")
    })
    public ResponseEntity<ApiResponse<StudyApplicationCreateResponse>> apply(
            @PathVariable Long studyId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody StudyApplicationCreateRequest request
    ) {
        StudyApplicationCreateResponse response = studyService.applyToStudy(
                studyId,
                authenticatedMember.memberId(),
                request
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        StudyResponseCode.STUDY_APPLICATION_CREATE_SUCCESS,
                        response
                ));
    }

    @GetMapping("/{studyId}")
    @Operation(
            summary = "스터디 모집/홈 상세 조회",
            description = "참여 상태에 따라 반환 필드가 달라집니다. 비멤버에게는 목표·멤버·채팅·임장 정보를 반환하지 않습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "스터디 상세 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디를 찾을 수 없음")
    })
    public ApiResponse<StudyDetailResponse> getDetail(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long studyId
    ) {
        return ApiResponse.success(
                StudyResponseCode.STUDY_DETAIL_SUCCESS,
                studyService.getDetail(authenticatedMember.memberId(), studyId)
        );
    }

    @PatchMapping("/{studyId}")
    @Operation(
            summary = "스터디 제목·목표·소개 수정",
            description = "스터디장이 제목, 목표, 소개를 수정합니다. 전달하지 않은 필드는 유지됩니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "수정할 필드가 없거나 입력값이 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "스터디장이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "회원 또는 스터디를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "완료·취소 상태라 수정할 수 없음")
    })
    public ApiResponse<StudyUpdateResponse> updateDetails(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long studyId,
            @Valid @RequestBody StudyUpdateRequest request
    ) {
        return ApiResponse.success(
                StudyResponseCode.STUDY_UPDATE_SUCCESS,
                studyService.updateDetails(authenticatedMember.memberId(), studyId, request)
        );
    }

    @GetMapping("/{studyId}/members")
    @Operation(
            summary = "스터디 멤버 목록 조회",
            description = "스터디장과 승인된 참여자만 조회할 수 있습니다. 스터디장이 첫 번째로 반환되며, 로그인 회원의 평가 완료 여부를 함께 제공합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "스터디 멤버 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "스터디 멤버가 아니어서 조회 권한이 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "일시적인 오류")
    })
    public ApiResponse<StudyMemberListResponse> getMembers(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long studyId
    ) {
        return ApiResponse.success(
                StudyResponseCode.STUDY_MEMBER_LIST_SUCCESS,
                studyMemberQueryServiceProvider.getObject()
                        .getMembers(authenticatedMember.memberId(), studyId)
        );
    }

    @PatchMapping("/{studyId}/recruitment/close")
    @Operation(
            summary = "스터디 모집 조기 마감",
            description = "스터디장만 모집 중인 스터디를 조기에 마감할 수 있습니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "모집 마감 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "스터디장이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "회원 또는 스터디를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 마감되었거나 현재 상태에서 마감할 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "일시적인 오류")
    })
    public ApiResponse<StudyRecruitmentCloseResponse> closeRecruitment(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long studyId
    ) {
        return ApiResponse.success(
                StudyResponseCode.STUDY_RECRUITMENT_CLOSE_SUCCESS,
                studyRecruitmentServiceProvider.getObject()
                        .closeRecruitment(authenticatedMember.memberId(), studyId)
        );
    }

    @DeleteMapping("/{studyId}/members/me")
    @Operation(
            summary = "스터디 나가기",
            description = "일반 멤버가 임장 시작 전에 스터디에서 나갑니다. 스터디장은 나갈 수 없습니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "나가기 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "회원, 스터디 또는 가입 정보를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "스터디장이거나 현재 상태에서 나갈 수 없음")
    })
    public ApiResponse<StudyMemberLeaveResponse> leave(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long studyId
    ) {
        return ApiResponse.success(
                StudyResponseCode.STUDY_MEMBER_LEAVE_SUCCESS,
                studyMemberCommandServiceProvider.getObject()
                        .leave(authenticatedMember.memberId(), studyId)
        );
    }

    @PatchMapping("/{studyId}/recruitment/open")
    @Operation(
            summary = "스터디 모집 재개",
            description = "스터디장만 조기 마감된 스터디의 모집을 다시 재개할 수 있습니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "모집 재개 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "스터디장이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "회원 또는 스터디를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 모집 중이거나 현재 상태에서 재개할 수 없거나 정원이 가득 참"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "일시적인 오류")
    })
    public ApiResponse<StudyRecruitmentCloseResponse> reopenRecruitment(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long studyId
    ) {
        return ApiResponse.success(
                StudyResponseCode.STUDY_RECRUITMENT_OPEN_SUCCESS,
                studyRecruitmentServiceProvider.getObject()
                        .reopenRecruitment(authenticatedMember.memberId(), studyId)
        );
    }

    @DeleteMapping("/{studyId}/members/{memberId}")
    @Operation(
            summary = "스터디 멤버 강퇴",
            description = "스터디장이 임장 시작 전에 승인된 일반 멤버를 강퇴합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "강퇴 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "스터디장 강퇴 시도"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "스터디장이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "회원, 스터디 또는 대상 멤버를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 상태 또는 임장 시작으로 강퇴할 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "일시적인 오류")
    })
    public ApiResponse<StudyMemberKickResponse> kick(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long studyId,
            @PathVariable Long memberId
    ) {
        return ApiResponse.success(
                StudyResponseCode.STUDY_MEMBER_KICK_SUCCESS,
                studyMemberCommandServiceProvider.getObject()
                        .kick(authenticatedMember.memberId(), studyId, memberId)
        );
    }
    @DeleteMapping("/{studyId}")
    @Operation(
            summary = "스터디 취소",
            description = "스터디장이 모집 중이거나 모집 마감된 스터디를 취소합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "스터디 취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_ACCESS_TOKEN_INVALID",
                                      "message": "로그인이 필요합니다.",
                                      "data": null,
                                      "timestamp": "2026-07-30T15:00:00+09:00"
                                    }
                                    """)
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "스터디장이 아니어서 취소 권한이 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "STUDY_CANCEL_FORBIDDEN",
                                      "message": "스터디를 취소할 권한이 없습니다.",
                                      "data": null,
                                      "timestamp": "2026-07-30T15:00:00+09:00"
                                    }
                                    """)
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 또는 스터디를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "memberNotFound", value = """
                                            {
                                              "success": false,
                                              "code": "MEMBER_NOT_FOUND",
                                              "message": "회원 정보를 찾을 수 없습니다.",
                                              "data": null,
                                              "timestamp": "2026-07-30T15:00:00+09:00"
                                            }
                                            """),
                                    @ExampleObject(name = "studyNotFound", value = """
                                            {
                                              "success": false,
                                              "code": "STUDY_NOT_FOUND",
                                              "message": "스터디를 찾을 수 없습니다.",
                                              "data": null,
                                              "timestamp": "2026-07-30T15:00:00+09:00"
                                            }
                                            """)
                            }
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 취소됐거나 현재 상태에서 취소할 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "alreadyCanceled", value = """
                                            {
                                              "success": false,
                                              "code": "STUDY_ALREADY_CANCELED",
                                              "message": "이미 취소된 스터디입니다.",
                                              "data": null,
                                              "timestamp": "2026-07-30T15:00:00+09:00"
                                            }
                                            """),
                                    @ExampleObject(name = "cancelNotAllowed", value = """
                                            {
                                              "success": false,
                                              "code": "STUDY_CANCEL_NOT_ALLOWED",
                                              "message": "현재 상태에서는 스터디를 취소할 수 없습니다.",
                                              "data": {
                                                "studyId": 10,
                                                "status": "IN_PROGRESS"
                                              },
                                              "timestamp": "2026-07-30T15:00:00+09:00"
                                            }
                                            """)
                            }
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "일시적인 서버 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "COMMON_INTERNAL_SERVER_ERROR",
                                      "message": "일시적인 오류가 발생했습니다.",
                                      "data": null,
                                      "timestamp": "2026-07-30T15:00:00+09:00"
                                    }
                                    """)
                    ))
    })
    public ApiResponse<StudyCancelResponse> cancel(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long studyId
    ) {
        return ApiResponse.success(
                StudyResponseCode.STUDY_CANCEL_SUCCESS,
                studyCancelServiceProvider.getObject()
                        .cancel(authenticatedMember.memberId(), studyId)
        );
    }
}
