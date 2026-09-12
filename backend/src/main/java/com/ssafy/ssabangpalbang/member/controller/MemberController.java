package com.ssafy.ssabangpalbang.member.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.dto.request.MemberMessageSendRequest;
import com.ssafy.ssabangpalbang.member.dto.request.MemberProfileUpdateRequest;
import com.ssafy.ssabangpalbang.member.dto.request.MemberWithdrawalRequest;
import com.ssafy.ssabangpalbang.member.dto.request.MemberPublicProfileSection;
import com.ssafy.ssabangpalbang.member.dto.request.MemberPublicStudyStatus;
import com.ssafy.ssabangpalbang.member.dto.request.MemberStudyStatus;
import com.ssafy.ssabangpalbang.member.dto.request.OnboardingRequest;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFollowResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFollowResult;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFollowingListResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberMessageSendResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberMessageSendResult;
import com.ssafy.ssabangpalbang.member.dto.response.MemberProfileResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberProfileUpdateResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberPublicProfileResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberStudyResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberUnfollowResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberUnfollowResult;
import com.ssafy.ssabangpalbang.member.dto.response.MemberVisitCalendarResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberWithdrawalResponse;
import com.ssafy.ssabangpalbang.member.dto.response.OnboardingResponse;
import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;
import com.ssafy.ssabangpalbang.member.service.MemberFollowService;
import com.ssafy.ssabangpalbang.member.service.MemberMessageService;
import com.ssafy.ssabangpalbang.member.service.MemberService;
import com.ssafy.ssabangpalbang.member.service.MemberStudyService;
import com.ssafy.ssabangpalbang.member.service.MemberVisitCalendarService;
import com.ssafy.ssabangpalbang.member.service.MemberWithdrawalService;
import com.ssafy.ssabangpalbang.report.dto.response.MemberReportResponse;
import com.ssafy.ssabangpalbang.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Tag(name = "회원", description = "로그인 회원의 프로필 및 온보딩 관리")
public class MemberController {

    private final MemberService memberService;
    private final MemberFollowService memberFollowService;
    private final MemberMessageService memberMessageService;
    private final ReportService reportService;
    private final MemberVisitCalendarService memberVisitCalendarService;
    private final MemberStudyService memberStudyService;
    private final MemberWithdrawalService memberWithdrawalService;

    @GetMapping("/me")
    @Operation(
            summary = "내 프로필 조회",
            description = "로그인 회원의 프로필, 온보딩 선호 정보와 마이페이지 요약 수를 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "내 프로필 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "탈퇴한 회원"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 정보를 찾을 수 없음"
            )
    })
    public ApiResponse<MemberProfileResponse> getMyProfile(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return ApiResponse.success(
                MemberResponseCode.PROFILE_RETRIEVED,
                memberService.getMyProfile(authenticatedMember.memberId())
        );
    }

    @GetMapping("/me/followings")
    @Operation(
            summary = "내 팔로잉 목록 조회",
            description = "로그인 회원이 팔로우한 활성 회원을 최근 팔로우한 순서로 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "팔로잉 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "cursor 또는 size가 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 정보를 찾을 수 없음"
            )
    })
    public ApiResponse<MemberFollowingListResponse> getMyFollowings(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(
                    description = "다음 목록 조회용 팔로우 ID",
                    example = "30",
                    schema = @Schema(
                            type = "integer",
                            format = "int64",
                            minimum = "1"
                    )
            )
            @RequestParam(required = false) String cursor,
            @Parameter(
                    description = "조회 개수 (1~100)",
                    example = "20",
                    schema = @Schema(
                            type = "integer",
                            minimum = "1",
                            maximum = "100",
                            defaultValue = "20"
                    )
            )
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                MemberResponseCode.FOLLOWING_LIST_RETRIEVED,
                memberFollowService.getFollowings(
                        authenticatedMember.memberId(),
                        parseFollowingCursor(cursor),
                        size
                )
        );
    }

    @GetMapping("/me/visit-calendar")
    @Operation(
            summary = "월별 임장 달력 조회",
            description = "로그인 회원이 스터디장 또는 ACTIVE 멤버인 임장 일정을 월 단위로 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "월별 임장 달력 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "연도 또는 월이 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 정보를 찾을 수 없음"
            )
    })
    public ApiResponse<MemberVisitCalendarResponse> getVisitCalendar(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(
                    description = "조회 연도 (2000~2100)",
                    example = "2026",
                    required = true,
                    schema = @Schema(
                            type = "integer",
                            minimum = "2000",
                            maximum = "2100"
                    )
            )
            @RequestParam(required = false)
            @NotNull(message = "조회할 연도는 필수 값입니다.")
            Integer year,
            @Parameter(
                    description = "조회 월 (1~12)",
                    example = "7",
                    required = true,
                    schema = @Schema(
                            type = "integer",
                            minimum = "1",
                            maximum = "12"
                    )
            )
            @RequestParam(required = false)
            @NotNull(message = "조회할 월은 필수 값입니다.")
            Integer month
    ) {
        return ApiResponse.success(
                MemberResponseCode.VISIT_CALENDAR_RETRIEVED,
                memberVisitCalendarService.getVisitCalendar(
                        authenticatedMember.memberId(),
                        year,
                        month
                )
        );
    }

    @GetMapping("/me/studies")
    @Operation(
            summary = "내 스터디 목록 조회",
            description = "로그인 회원이 스터디장 또는 ACTIVE 멤버로 참여 중인 스터디를 상태별로 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "내 스터디 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "상태, 페이지 번호 또는 크기가 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 정보를 찾을 수 없음"
            )
    })
    public ApiResponse<PageResponse<MemberStudyResponse>> getMyStudies(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(
                    description = "스터디 상태 필터",
                    schema = @Schema(
                            allowableValues = {
                                    "ALL", "ACTIVE", "IN_PROGRESS",
                                    "COMPLETED"
                            },
                            defaultValue = "ALL"
                    )
            )
            @RequestParam(defaultValue = "ALL") String status,
            @Parameter(
                    description = "페이지 번호 (0 이상)",
                    schema = @Schema(
                            type = "integer",
                            minimum = "0",
                            defaultValue = "0"
                    )
            )
            @RequestParam(defaultValue = "0") int page,
            @Parameter(
                    description = "페이지 크기 (1~100)",
                    schema = @Schema(
                            type = "integer",
                            minimum = "1",
                            maximum = "100",
                            defaultValue = "20"
                    )
            )
            @RequestParam(defaultValue = "20") int size
    ) {
        validatePage(page, size);
        return ApiResponse.success(
                MemberResponseCode.STUDY_LIST_RETRIEVED,
                memberStudyService.getMyStudies(
                        authenticatedMember.memberId(),
                        MemberStudyStatus.from(status),
                        page,
                        size
                )
        );
    }

    @GetMapping("/{memberId}")
    @Operation(
            summary = "다른 사용자 공개 프로필 조회",
            description = "활성 회원의 공개 프로필과 스터디·리포트·팔로잉 탭을 페이지 단위로 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "공개 프로필 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "회원 ID가 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "탈퇴한 로그인 회원"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "조회 대상 회원을 찾을 수 없음"
            )
    })
    public ApiResponse<MemberPublicProfileResponse> getPublicProfile(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(
                    description = "공개 프로필을 조회할 회원 ID",
                    example = "12",
                    schema = @Schema(
                            type = "integer",
                            format = "int64",
                            minimum = "1"
                    )
            )
            @PathVariable String memberId,
            @Parameter(
                    description = "조회할 탭",
                    schema = @Schema(
                            allowableValues = {
                                    "STUDIES", "REPORTS", "FOLLOWINGS"
                            },
                            defaultValue = "STUDIES"
                    )
            )
            @RequestParam(defaultValue = "STUDIES") String section,
            @Parameter(
                    description = "스터디 탭 상태 필터",
                    schema = @Schema(
                            allowableValues = {
                                    "ACTIVE", "COMPLETED", "ALL"
                            },
                            defaultValue = "ACTIVE"
                    )
            )
            @RequestParam(defaultValue = "ACTIVE") String studyStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validatePage(page, size);
        return ApiResponse.success(
                MemberResponseCode.PUBLIC_PROFILE_RETRIEVED,
                memberService.getPublicProfile(
                        authenticatedMember.memberId(),
                        parseMemberId(memberId),
                        MemberPublicProfileSection.from(section),
                        MemberPublicStudyStatus.from(studyStatus),
                        page,
                        size
                )
        );
    }

    @PostMapping("/{memberId}/messages")
    @Operation(
            summary = "팔로잉 사용자에게 MVP 쪽지 전송",
            description = "팔로잉 중인 활성 회원에게 단건 텍스트 쪽지를 알림으로 전송합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "쪽지 신규 전송 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "동일 clientMessageId의 기존 전송 결과 반환"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "회원 ID, 내용 또는 clientMessageId가 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "탈퇴한 로그인 회원이거나 팔로잉 관계가 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "수신 회원을 찾을 수 없음"
            )
    })
    public ResponseEntity<ApiResponse<MemberMessageSendResponse>> sendMessage(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(
                    description = "쪽지를 받을 회원 ID",
                    example = "12",
                    schema = @Schema(
                            type = "integer",
                            format = "int64",
                            minimum = "1"
                    )
            )
            @PathVariable String memberId,
            @RequestBody MemberMessageSendRequest request
    ) {
        MemberMessageSendResult result = memberMessageService.send(
                authenticatedMember.memberId(),
                parseMemberId(memberId),
                request
        );
        HttpStatus status = result.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(
                ApiResponse.success(
                        result.responseCode(),
                        result.response()
                )
        );
    }

    @PutMapping("/{memberId}/follow")
    @Operation(
            summary = "사용자 팔로우",
            description = "로그인 회원이 활성 사용자를 멱등하게 팔로우합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "신규 팔로우 또는 이미 팔로우 중인 상태 반환"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "회원 ID가 올바르지 않거나 본인을 팔로우함"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "탈퇴한 로그인 회원"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "팔로우 대상 회원을 찾을 수 없음"
            )
    })
    public ApiResponse<MemberFollowResponse> follow(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(
                    description = "팔로우할 회원 ID",
                    example = "15",
                    schema = @Schema(
                            type = "integer",
                            format = "int64",
                            minimum = "1"
                    )
            )
            @PathVariable String memberId
    ) {
        MemberFollowResult result = memberFollowService.follow(
                authenticatedMember.memberId(),
                parseMemberId(memberId)
        );
        return ApiResponse.success(
                result.responseCode(),
                result.response()
        );
    }

    @DeleteMapping("/{memberId}/follow")
    @Operation(
            summary = "사용자 팔로우 해제",
            description = "로그인 회원이 활성 사용자의 팔로우를 멱등하게 해제합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "팔로우 해제 또는 이미 해제된 상태 반환"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "회원 ID가 올바르지 않거나 본인을 대상으로 지정함"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "탈퇴한 로그인 회원"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "팔로우 해제 대상 회원을 찾을 수 없음"
            )
    })
    public ApiResponse<MemberUnfollowResponse> unfollow(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(
                    description = "팔로우를 해제할 회원 ID",
                    example = "15",
                    schema = @Schema(
                            type = "integer",
                            format = "int64",
                            minimum = "1"
                    )
            )
            @PathVariable String memberId
    ) {
        MemberUnfollowResult result = memberFollowService.unfollow(
                authenticatedMember.memberId(),
                parseMemberId(memberId)
        );
        return ApiResponse.success(
                result.responseCode(),
                result.response()
        );
    }

    @PatchMapping("/me")
    @Operation(
            summary = "내 프로필 수정",
            description = "로그인 회원이 요청에 포함한 프로필 및 생활 조건만 수정합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "내 프로필 수정 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "수정 항목이 없거나 입력값이 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "탈퇴한 회원"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 정보를 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 사용 중인 닉네임"
            )
    })
    public ApiResponse<MemberProfileUpdateResponse> updateMyProfile(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody MemberProfileUpdateRequest request
    ) {
        return ApiResponse.success(
                MemberResponseCode.PROFILE_UPDATED,
                memberService.updateMyProfile(
                        authenticatedMember.memberId(),
                        request
                )
        );
    }

    @DeleteMapping("/me")
    @Operation(
            summary = "회원 탈퇴",
            description = "로그인 회원을 소프트 탈퇴 처리하고 인증·알림 수신 수단을 폐기합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "회원 탈퇴 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수값 누락 또는 확인 문구 불일치"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token 또는 Refresh Token이 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 정보를 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "진행 중인 임장, 운영 중인 리더 스터디 또는 이미 탈퇴한 회원"
            )
    })
    public ApiResponse<MemberWithdrawalResponse> withdraw(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody MemberWithdrawalRequest request
    ) {
        return ApiResponse.success(
                MemberResponseCode.WITHDRAWN,
                memberWithdrawalService.withdraw(
                        authenticatedMember.memberId(),
                        request
                )
        );
    }

    @GetMapping("/me/reports")
    @Operation(
            summary = "내 리포트 목록 조회",
            description = "로그인 회원이 스터디장 또는 현재 참여 중인 스터디의 완료 리포트를 최신순으로 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "내 리포트 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "페이지 번호 또는 크기가 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 정보를 찾을 수 없음"
            )
    })
    public ApiResponse<PageResponse<MemberReportResponse>> getMyReports(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validatePage(page, size);
        return ApiResponse.success(
                MemberResponseCode.REPORT_LIST_RETRIEVED,
                reportService.getMyReports(
                        authenticatedMember.memberId(),
                        page,
                        size
                )
        );
    }

    @PutMapping("/me/onboarding")
    @Operation(
            summary = "온보딩 정보 저장",
            description = "로그인 회원의 임장 조건과 선택 캐릭터를 저장하거나 갱신합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "온보딩 저장 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수 항목 누락 또는 허용되지 않은 선택값"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "탈퇴한 회원"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 정보를 찾을 수 없음"
            )
    })
    public ApiResponse<OnboardingResponse> saveOnboarding(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody OnboardingRequest request
    ) {
        return ApiResponse.success(
                MemberResponseCode.ONBOARDING_SAVED,
                memberService.saveOnboarding(
                        authenticatedMember.memberId(),
                        request
                )
        );
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of(
                            "field", "page",
                            "reason", "페이지 번호는 0 이상이어야 합니다."
                    )
            );
        }
        if (size < 1 || size > 100) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of(
                            "field", "size",
                            "reason", "페이지 크기는 1 이상 100 이하이어야 합니다."
                    )
            );
        }
    }

    private Long parseMemberId(String memberId) {
        try {
            long parsedMemberId = Long.parseLong(memberId);
            if (parsedMemberId < 1) {
                throw invalidMemberId();
            }
            return parsedMemberId;
        } catch (NumberFormatException exception) {
            throw invalidMemberId();
        }
    }

    private Long parseFollowingCursor(String cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            long parsedCursor = Long.parseLong(cursor);
            if (parsedCursor < 1) {
                throw invalidFollowingCursor();
            }
            return parsedCursor;
        } catch (NumberFormatException exception) {
            throw invalidFollowingCursor();
        }
    }

    private BusinessException invalidFollowingCursor() {
        return new BusinessException(
                ErrorCode.MEMBER_FOLLOWING_CURSOR_INVALID,
                Map.of(
                        "field", "cursor",
                        "reason", "커서는 1 이상의 숫자여야 합니다."
                )
        );
    }

    private BusinessException invalidMemberId() {
        return new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                Map.of(
                        "field", "memberId",
                        "reason", "회원 ID는 1 이상의 숫자여야 합니다."
                )
        );
    }
}
