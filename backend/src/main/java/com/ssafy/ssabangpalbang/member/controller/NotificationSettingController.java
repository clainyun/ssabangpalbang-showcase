package com.ssafy.ssabangpalbang.member.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.member.dto.response.NotificationSettingResponse;
import com.ssafy.ssabangpalbang.member.dto.response.NotificationSettingUpdateResponse;
import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;
import com.ssafy.ssabangpalbang.member.service.NotificationSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me/notification-settings")
@RequiredArgsConstructor
@Tag(name = "회원 알림 설정", description = "로그인한 회원의 알림 수신 설정 조회")
public class NotificationSettingController {

    private final NotificationSettingService notificationSettingService;

    @GetMapping
    @Operation(
            summary = "알림 수신 설정 조회",
            description = "로그인한 회원의 서비스 알림과 광고성 알림 수신 동의 상태를 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "알림 수신 설정 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 유효하지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "비활성 회원 또는 회원 정보 없음"
            )
    })
    public ApiResponse<NotificationSettingResponse> getNotificationSettings() {
        return ApiResponse.success(
                MemberResponseCode.MEMBER_NOTIFICATION_SETTINGS_SUCCESS,
                notificationSettingService.getNotificationSettings()
        );
    }

    @PatchMapping
    @Operation(
            summary = "알림 수신 설정 수정",
            description = "로그인한 회원의 서비스 알림 및 광고성 알림 수신 동의 상태를 수정합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "알림 수신 설정 수정 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "변경 항목이 없거나 알림 동의 값이 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 유효하지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "비활성 회원 또는 회원 정보 없음"
            )
    })
    public ApiResponse<NotificationSettingUpdateResponse> updateNotificationSettings(
            @RequestBody(required = false) JsonNode requestBody
    ) {
        return ApiResponse.success(
                MemberResponseCode.NOTIFICATION_SETTINGS_UPDATED,
                notificationSettingService.update(requestBody)
        );
    }
}
