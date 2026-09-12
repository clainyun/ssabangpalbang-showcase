package com.ssafy.ssabangpalbang.notification.controller;

import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationListResponse;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationReadAllResponse;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationReadResponse;
import com.ssafy.ssabangpalbang.notification.response.NotificationResponseCode;
import com.ssafy.ssabangpalbang.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "알림", description = "로그인한 회원의 서비스 알림 조회")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(
            summary = "알림 목록 조회",
            description = "현재 로그인한 회원의 알림을 미읽음 최신순, 읽음 최신순으로 "
                    + "스냅샷 커서 페이지네이션하여 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "알림 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "cursor 또는 size가 유효하지 않음"
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
    public ApiResponse<NotificationListResponse> getNotifications(
            @Parameter(description = "읽지 않은 알림만 조회", example = "false")
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @Parameter(description = "다음 목록 조회용 불투명 커서")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "조회 개수 (1~100)", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(
                NotificationResponseCode.NOTIFICATION_LIST_SUCCESS,
                notificationService.getNotifications(unreadOnly, cursor, size)
        );
    }

    @PatchMapping("/read-all")
    @Operation(
            summary = "알림 전체 읽음 처리",
            description = "현재 로그인한 회원의 읽지 않은 알림을 모두 읽음 처리합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "전체 읽음 처리 또는 이미 모든 알림을 읽음"
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
    public ApiResponse<NotificationReadAllResponse> readAllNotifications() {
        NotificationService.ReadAllResult result = notificationService
                .readAllNotifications();

        return ApiResponse.success(result.responseCode(), result.response());
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(
            summary = "알림 한 건 읽음 처리",
            description = "현재 로그인한 회원의 알림을 읽음 처리합니다. 이미 읽은 알림도 "
                    + "기존 읽음 시각을 유지한 채 정상 응답합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "알림 읽음 처리 또는 이미 읽은 알림"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "notificationId가 유효하지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "비활성 회원, 알림이 없거나 접근할 수 없음"
            )
    })
    public ApiResponse<NotificationReadResponse> readNotification(
            @Parameter(description = "읽음 처리할 알림 ID", example = "81")
            @PathVariable String notificationId
    ) {
        NotificationService.ReadResult result = notificationService
                .readNotification(notificationId);

        return ApiResponse.success(result.responseCode(), result.response());
    }
}
