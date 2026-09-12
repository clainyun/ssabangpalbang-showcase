package com.ssafy.ssabangpalbang.member.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFavoriteApartmentResponse;
import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;
import com.ssafy.ssabangpalbang.member.service.MemberFavoriteApartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/members/me/favorite-apartments")
@RequiredArgsConstructor
@Tag(name = "회원", description = "로그인 회원의 프로필 및 온보딩 관리")
public class MemberFavoriteApartmentController {

    private final MemberFavoriteApartmentService service;

    @GetMapping
    @Operation(
            summary = "내가 찜한 아파트 목록 조회",
            description = "로그인 회원이 찜한 아파트를 최근 찜한 순서로 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "찜한 아파트 목록 조회 성공"
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류"
            )
    })
    public ApiResponse<PageResponse<MemberFavoriteApartmentResponse>>
            getFavoriteApartments(
                    @AuthenticationPrincipal
                    AuthenticatedMember authenticatedMember,
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
                MemberResponseCode.FAVORITE_APARTMENT_LIST_RETRIEVED,
                service.getFavoriteApartments(
                        authenticatedMember.memberId(),
                        page,
                        size
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
}
