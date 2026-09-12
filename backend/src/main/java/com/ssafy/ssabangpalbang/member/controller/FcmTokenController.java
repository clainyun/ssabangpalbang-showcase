package com.ssafy.ssabangpalbang.member.controller;

import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.member.dto.request.FcmTokenRegisterRequest;
import com.ssafy.ssabangpalbang.member.dto.response.FcmTokenDeleteResponse;
import com.ssafy.ssabangpalbang.member.dto.response.FcmTokenResponse;
import com.ssafy.ssabangpalbang.member.dto.response.FcmTestPushResponse;
import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;
import com.ssafy.ssabangpalbang.member.service.FcmTestPushService;
import com.ssafy.ssabangpalbang.member.service.FcmTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me/devices")
@RequiredArgsConstructor
@Tag(name = "회원 기기", description = "로그인한 회원의 기기별 FCM 토큰 관리")
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;
    private final FcmTestPushService fcmTestPushService;

    @PostMapping("/{deviceId}/test-fcm-push")
    @Operation(
            summary = "현재 기기로 FCM 테스트 알림 예약",
            description = "로그인한 회원의 요청 기기에 등록된 최신 FCM 토큰으로 5초 뒤 테스트 알림을 전송합니다. "
                    + "Authorization 헤더의 Bearer Access Token이 필요합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "FCM 테스트 알림 예약 수락"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "유효하지 않은 기기 식별자"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "활성 회원 또는 현재 기기의 FCM 토큰을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "FCM dry-run 검증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "FCM 발송 설정 또는 예약 실행기가 준비되지 않음"
            )
    })
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<FcmTestPushResponse> sendTestPush(
            @Parameter(
                    description = "현재 기기의 앱 생성 UUID",
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable String deviceId
    ) {
        return ApiResponse.success(
                MemberResponseCode.FCM_TEST_PUSH_SCHEDULED,
                fcmTestPushService.sendToCurrentDevice(deviceId)
        );
    }

    @PutMapping("/{deviceId}/fcm-token")
    @Operation(
            summary = "기기 FCM 토큰 등록 또는 갱신",
            description = "같은 회원·기기의 기존 토큰은 새 값으로 갱신합니다. "
                    + "Authorization 헤더에 Bearer Access Token이 필요합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "FCM 토큰 등록 또는 갱신 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "deviceId 또는 fcmToken이 비어 있거나 허용 길이를 초과함"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "비활성 회원"
            )
    })
    public ApiResponse<FcmTokenResponse> register(
            @Parameter(
                    description = "앱 설치별 UUID. IMEI나 Android ID를 사용하지 않습니다.",
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable String deviceId,
            @Valid @RequestBody FcmTokenRegisterRequest request
    ) {
        FcmTokenService.RegistrationResult result = fcmTokenService.register(
                deviceId,
                request
        );

        return ApiResponse.success(result.responseCode(), result.response());
    }

    @DeleteMapping("/{deviceId}/fcm-token")
    @Operation(
            summary = "기기 FCM 토큰 연결 해제",
            description = "현재 로그인한 회원과 기기의 FCM 토큰 연결을 물리적으로 삭제합니다. "
                    + "Authorization 헤더에 Bearer Access Token이 필요합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "FCM 토큰 연결 해제 또는 이미 해제된 상태"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "deviceId가 비어 있거나 허용 길이를 초과함"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "비활성 회원"
            )
    })
    public ApiResponse<FcmTokenDeleteResponse> delete(
            @Parameter(
                    description = "앱 설치별 UUID. IMEI나 Android ID를 사용하지 않습니다.",
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable String deviceId
    ) {
        FcmTokenService.DeletionResult result = fcmTokenService.delete(deviceId);

        return ApiResponse.success(result.responseCode(), result.response());
    }
}
