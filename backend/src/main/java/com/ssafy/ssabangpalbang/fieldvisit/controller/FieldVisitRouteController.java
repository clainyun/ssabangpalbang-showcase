package com.ssafy.ssabangpalbang.fieldvisit.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.fieldvisit.dto.RouteResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitRouteDetailResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitRouteGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitRouteService;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/studies/{studyId}/field-visit/route")
@Tag(name = "임장 추천 경로", description = "체크리스트 기반 공유 추천 경로 생성·조회")
public class FieldVisitRouteController {

    private final FieldVisitRouteService routeService;

    @PostMapping
    @Operation(summary = "추천 경로 생성")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "신규 추천 경로 생성"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "이미 생성된 추천 경로 반환"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "진행 중인 임장 참여자 아님"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "스터디 또는 임장 세션 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "체크리스트·시설 선행조건 또는 임장 상태 충돌"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "카카오 주변 시설·보행 경로 조회 일시 실패"
            )
    })
    public ResponseEntity<ApiResponse<FieldVisitRouteGenerateResponse>> generate(
            @PathVariable
            @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        FieldVisitRouteService.GenerateResult result = routeService.generate(
                studyId, authenticatedMember.memberId()
        );
        return ResponseEntity.status(result.httpStatus())
                .body(ApiResponse.success(result.responseCode(), result.body()));
    }

    @GetMapping
    @Operation(summary = "추천 경로 조회")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공(미생성이면 route=null)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "승인된 스터디 멤버 아님"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "스터디 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "기존 경로 보행 경로 재계산 일시 실패"
            )
    })
    public ResponseEntity<ApiResponse<FieldVisitRouteDetailResponse>> getRoute(
            @PathVariable
            @Positive(message = "스터디 ID는 1 이상이어야 합니다.")
            Long studyId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        FieldVisitRouteDetailResponse body = routeService.getRoute(
                studyId, authenticatedMember.memberId()
        );
        return ResponseEntity.ok(
                ApiResponse.success(RouteResponseCode.ROUTE_DETAIL_SUCCESS, body)
        );
    }
}
