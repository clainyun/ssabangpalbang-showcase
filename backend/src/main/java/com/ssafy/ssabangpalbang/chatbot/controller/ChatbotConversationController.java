package com.ssafy.ssabangpalbang.chatbot.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.chatbot.dto.ChatbotResponseCode;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotConversationCreateResponse;
import com.ssafy.ssabangpalbang.chatbot.service.ChatbotConversationService;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/apartments/{apartmentId}/chatbot/conversations")
@RequiredArgsConstructor
@Validated
@Tag(
        name = "아파트 챗봇",
        description = "아파트 상세·임장 지도 공통 AI 챗봇 대화(BE-026)"
)
public class ChatbotConversationController {

    private final ChatbotConversationService conversationService;

    @PostMapping
    @Operation(
            summary = "아파트 챗봇 대화 시작",
            description = "로그인 회원과 아파트를 연결한 새 챗봇 대화를 생성하고 "
                    + "예상 답변 근거와 추천 질문을 반환한다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "대화 생성 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 아파트 ID",
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
                    responseCode = "404",
                    description = "회원 또는 아파트 없음",
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
    public ResponseEntity<ApiResponse<ChatbotConversationCreateResponse>>
    create(
            @Parameter(description = "아파트 ID", example = "15")
            @PathVariable
            @Positive(message = "아파트 ID는 1 이상이어야 합니다.")
            Long apartmentId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        ChatbotConversationCreateResponse response =
                conversationService.create(
                        apartmentId,
                        authenticatedMember.memberId()
                );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        ChatbotResponseCode
                                .CHATBOT_CONVERSATION_CREATE_SUCCESS,
                        response
                ));
    }
}
