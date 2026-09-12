package com.ssafy.ssabangpalbang.chatbot.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.chatbot.dto.ChatbotResponseCode;
import com.ssafy.ssabangpalbang.chatbot.dto.request.ChatbotMessageCreateRequest;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotMessageCreateResponse;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotMessageListResponse;
import com.ssafy.ssabangpalbang.chatbot.service.ChatbotAskService;
import com.ssafy.ssabangpalbang.chatbot.service.ChatbotMessageService;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/api/v1/apartments/{apartmentId}/chatbot/conversations/"
                + "{conversationId}/messages"
)
@RequiredArgsConstructor
@Validated
@Tag(
        name = "아파트 챗봇",
        description = "아파트 상세·임장 지도 공통 AI 챗봇 대화(BE-026)"
)
public class ChatbotMessageController {

    private final ChatbotMessageService messageService;
    private final ChatbotAskService askService;

    @GetMapping
    @Operation(
            summary = "아파트 챗봇 대화 이력 조회",
            description = "대화 소유자의 사용자 질문과 AI 답변 이력을 "
                    + "메시지 ID 커서로 조회한다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "대화 이력 조회 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 ID·커서·크기 또는 아파트 불일치",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "미인증",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "대화 소유자가 아님",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "아파트 또는 대화 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "일시적인 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
    public ApiResponse<ChatbotMessageListResponse> getMessages(
            @Parameter(description = "아파트 ID", example = "15")
            @PathVariable
            @Positive(message = "아파트 ID는 1 이상이어야 합니다.")
            Long apartmentId,
            @Parameter(description = "대화 ID", example = "41")
            @PathVariable
            @Positive(message = "대화 ID는 1 이상이어야 합니다.")
            Long conversationId,
            @Parameter(description = "마지막으로 조회한 메시지 ID", example = "52")
            @RequestParam(required = false)
            @Positive(message = "커서는 1 이상이어야 합니다.")
            Long cursor,
            @Parameter(description = "조회할 메시지 수", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "조회 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "조회 크기는 100 이하여야 합니다.")
            Integer size,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        ChatbotMessageListResponse response = messageService.getMessages(
                apartmentId,
                conversationId,
                authenticatedMember.memberId(),
                cursor,
                size
        );
        return ApiResponse.success(
                ChatbotResponseCode.CHATBOT_MESSAGE_LIST_SUCCESS,
                response
        );
    }

    @PostMapping
    @Operation(
            summary = "아파트 챗봇 질문 전송",
            description = "사용자 질문과 AI 답변 placeholder를 저장한 뒤 "
                    + "비동기로 답변 생성을 요청한다. 결과는 이력 조회로 확인한다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "질문 접수 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "질문 내용 오류 또는 아파트 불일치",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "미인증",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "대화 소유자가 아님",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원·아파트 또는 대화 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이전 답변 생성 중",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "일시적인 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
    public ResponseEntity<ApiResponse<ChatbotMessageCreateResponse>> ask(
            @Parameter(description = "아파트 ID", example = "15")
            @PathVariable
            @Positive(message = "아파트 ID는 1 이상이어야 합니다.")
            Long apartmentId,
            @Parameter(description = "대화 ID", example = "41")
            @PathVariable
            @Positive(message = "대화 ID는 1 이상이어야 합니다.")
            Long conversationId,
            @Valid @RequestBody ChatbotMessageCreateRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        ChatbotMessageCreateResponse response = askService.ask(
                apartmentId,
                conversationId,
                authenticatedMember.memberId(),
                request.content()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(
                        ChatbotResponseCode.CHATBOT_MESSAGE_ACCEPTED,
                        response
                ));
    }
}
