package com.ssafy.ssabangpalbang.region.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.region.dto.response.DistrictListResponse;
import com.ssafy.ssabangpalbang.region.dto.response.DongListResponse;
import com.ssafy.ssabangpalbang.region.dto.response.RegionResponseCode;
import com.ssafy.ssabangpalbang.region.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
@Tag(name = "지역", description = "서울 자치구·법정동 기준정보 조회")
public class RegionController {

    private final RegionService regionService;

    @Operation(summary = "서울 자치구 목록 조회", description = "서울특별시 25개 자치구 목록을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "자치구 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "회원을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "서버 내부 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "지역 기준정보 조회 불가")
    })
    @GetMapping("/districts")
    public ApiResponse<DistrictListResponse> getDistricts(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return ApiResponse.success(
                RegionResponseCode.REGION_DISTRICT_LIST_SUCCESS,
                regionService.getDistricts(authenticatedMember.memberId())
        );
    }

    @Operation(summary = "선택한 구의 동 목록 조회", description = "선택한 서울 자치구의 법정동 목록을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "동 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "자치구 코드 형식이 올바르지 않거나 서울 외 지역"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "회원 또는 자치구를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "서버 내부 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "지역 기준정보 조회 불가")
    })
    @GetMapping("/districts/{districtCode}/dongs")
    public ApiResponse<DongListResponse> getDongs(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(description = "서울 자치구 코드", example = "11710")
            @PathVariable String districtCode
    ) {
        return ApiResponse.success(
                RegionResponseCode.REGION_DONG_LIST_SUCCESS,
                regionService.getDongs(authenticatedMember.memberId(), districtCode)
        );
    }
}
