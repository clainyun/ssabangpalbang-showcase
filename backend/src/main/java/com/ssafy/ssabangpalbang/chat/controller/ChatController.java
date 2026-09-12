package com.ssafy.ssabangpalbang.chat.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageDeleteResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageEditRequest;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageEditResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageListResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatNotificationSettingRequest;
import com.ssafy.ssabangpalbang.chat.dto.ChatNotificationSettingResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatReadRequest;
import com.ssafy.ssabangpalbang.chat.dto.ChatReadResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatResponseCode;
import com.ssafy.ssabangpalbang.chat.dto.ChatUnreadCountResponse;
import com.ssafy.ssabangpalbang.chat.service.ChatRestService;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * BE-012: 스터디 채팅 이력 조회·읽음 처리·안 읽은 수 조회 REST API다.
 * 실시간 송수신은 WebSocket(STOMP) /ws 연결을 사용하며, 이 컨트롤러는
 * 과거 이력 복원과 읽음 상태 관리만 담당한다.
 */
@RestController
@RequestMapping("/api/v1/studies/{studyId}/chat")
@RequiredArgsConstructor
@Tag(name = "채팅", description = "스터디 채팅 이력 조회·읽음 처리·안 읽은 수 조회")
public class ChatController {

    private final ChatRestService chatRestService;

    @GetMapping("/messages")
    @Operation(
            summary = "스터디 채팅 이력 조회",
            description = "커서 기반으로 이전 메시지를 최신순으로 조회합니다. "
                    + "TEXT·IMAGE·SYSTEM 메시지를 모두 포함합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "채팅 이력 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "조회 개수가 1~50 범위를 벗어남"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 스터디의 멤버가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디 또는 회원 정보를 찾을 수 없음")
    })
    public ApiResponse<ChatMessageListResponse> getMessages(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long studyId,
            @Parameter(description = "다음(과거) 목록 조회 커서. 첫 요청은 생략한다.")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "조회 개수. 1 이상 50 이하, 기본값 30.")
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(
                ChatResponseCode.CHAT_MESSAGE_LIST_SUCCESS,
                chatRestService.getHistory(studyId, authenticatedMember.memberId(), cursor, size)
        );
    }

    @PatchMapping("/read")
    @Operation(
            summary = "스터디 채팅 읽음 처리",
            description = "lastReadMessageId를 생략하면 현재 시각 기준으로 전체 읽음 처리합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "읽음 처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "lastReadMessageId가 해당 스터디의 메시지가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 스터디의 멤버가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디 또는 회원 정보를 찾을 수 없음")
    })
    public ApiResponse<ChatReadResponse> readMessages(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long studyId,
            @RequestBody(required = false) ChatReadRequest request
    ) {
        ChatReadRequest safeRequest = request == null ? new ChatReadRequest(null) : request;
        return ApiResponse.success(
                ChatResponseCode.CHAT_READ_SUCCESS,
                chatRestService.markRead(studyId, authenticatedMember.memberId(), safeRequest)
        );
    }

    @DeleteMapping("/messages/{messageId}")
    @Operation(
            summary = "스터디 채팅 메시지 삭제",
            description = "본인이 보낸 TEXT·IMAGE 메시지를 소프트 삭제합니다. "
                    + "이력에는 '삭제된 메시지' 톰스톤으로 남고, 실시간 구독자에게는 "
                    + "deleted=true 페이로드가 발행됩니다. 이미 삭제된 메시지는 다시 "
                    + "삭제해도 성공으로 응답합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "메시지 삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 스터디의 멤버가 아니거나, 본인이 보낸 메시지가 아니거나, 완료된 스터디임"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디·회원·메시지를 찾을 수 없음")
    })
    public ApiResponse<ChatMessageDeleteResponse> deleteMessage(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long studyId,
            @PathVariable Long messageId
    ) {
        return ApiResponse.success(
                ChatResponseCode.CHAT_MESSAGE_DELETE_SUCCESS,
                chatRestService.deleteMessage(studyId, authenticatedMember.memberId(), messageId)
        );
    }

    @PatchMapping("/messages/{messageId}")
    @Operation(
            summary = "스터디 채팅 메시지 수정",
            description = "본인이 보낸 TEXT 메시지의 본문을 수정합니다. IMAGE·SYSTEM은 "
                    + "수정할 수 없고, 삭제된 메시지는 404로 응답합니다. 실시간 구독자에게는 "
                    + "바뀐 본문과 editedAt이 발행됩니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "메시지 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "내용이 비었거나 TEXT 메시지가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 스터디의 멤버가 아니거나, 본인이 보낸 메시지가 아니거나, 완료된 스터디임"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디·회원·메시지를 찾을 수 없음(삭제된 메시지 포함)")
    })
    public ApiResponse<ChatMessageEditResponse> editMessage(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long studyId,
            @PathVariable Long messageId,
            @Valid @RequestBody ChatMessageEditRequest request
    ) {
        return ApiResponse.success(
                ChatResponseCode.CHAT_MESSAGE_EDIT_SUCCESS,
                chatRestService.editMessage(
                        studyId,
                        authenticatedMember.memberId(),
                        messageId,
                        request
                )
        );
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "읽지 않은 스터디 채팅 수 조회",
            description = "본인이 보낸 메시지는 제외하고 계산합니다. SYSTEM 메시지는 포함합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "안 읽은 채팅 수 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 스터디의 멤버가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디 또는 회원 정보를 찾을 수 없음")
    })
    public ApiResponse<ChatUnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long studyId
    ) {
        return ApiResponse.success(
                ChatResponseCode.CHAT_UNREAD_COUNT_SUCCESS,
                chatRestService.getUnreadCount(studyId, authenticatedMember.memberId())
        );
    }

    @GetMapping("/notification-settings")
    @Operation(summary = "스터디 채팅 푸시 알림 설정 조회")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "알림 설정 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 스터디의 멤버가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디 또는 회원 정보를 찾을 수 없음")
    })
    public ApiResponse<ChatNotificationSettingResponse> getNotificationSetting(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long studyId
    ) {
        return ApiResponse.success(
                ChatResponseCode.CHAT_NOTIFICATION_SETTING_SUCCESS,
                chatRestService.getNotificationSetting(
                        studyId,
                        authenticatedMember.memberId()
                )
        );
    }

    @PatchMapping("/notification-settings")
    @Operation(summary = "스터디 채팅 푸시 알림 설정 변경")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "알림 설정 변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "pushEnabled가 누락되었거나 boolean이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 스터디의 멤버가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스터디 또는 회원 정보를 찾을 수 없음")
    })
    public ApiResponse<ChatNotificationSettingResponse> updateNotificationSetting(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long studyId,
            @Valid @RequestBody ChatNotificationSettingRequest request
    ) {
        return ApiResponse.success(
                ChatResponseCode.CHAT_NOTIFICATION_SETTING_UPDATED,
                chatRestService.updateNotificationSetting(
                        studyId,
                        authenticatedMember.memberId(),
                        request.pushEnabled()
                )
        );
    }
}
