package com.ssafy.ssabangpalbang.home.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.home.code.HomeResponseCode;
import com.ssafy.ssabangpalbang.home.dto.response.HomeResponse;
import com.ssafy.ssabangpalbang.home.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    @Operation(
            summary = "홈 화면 통합 조회",
            description = "로그인 회원의 다음 임장, 읽지 않은 알림 수, 날씨·미세먼지와 기상청 공식 특보를 한 번에 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "홈 화면 정보 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "위도·경도 누락 또는 범위 오류"
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
    public ApiResponse<HomeResponse> getHome(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(
                    name = "latitude",
                    in = ParameterIn.QUERY,
                    description = "현재 위치 위도. longitude와 함께 전달",
                    schema = @Schema(
                            type = "number",
                            format = "double",
                            minimum = "-90",
                            maximum = "90"
                    )
            )
            @RequestParam(required = false) String latitude,
            @Parameter(
                    name = "longitude",
                    in = ParameterIn.QUERY,
                    description = "현재 위치 경도. latitude와 함께 전달",
                    schema = @Schema(
                            type = "number",
                            format = "double",
                            minimum = "-180",
                            maximum = "180"
                    )
            )
            @RequestParam(required = false) String longitude
    ) {
        return ApiResponse.success(
                HomeResponseCode.HOME_RETRIEVED,
                homeService.getHome(
                        authenticatedMember.memberId(),
                        latitude,
                        longitude
                )
        );
    }
}
