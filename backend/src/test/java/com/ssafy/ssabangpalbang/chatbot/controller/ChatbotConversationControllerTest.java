package com.ssafy.ssabangpalbang.chatbot.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotConversationCreateResponse;
import com.ssafy.ssabangpalbang.chatbot.service.ChatbotConversationService;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatbotConversationController.class)
@Import({
        AuthSecurityConfiguration.class,
        GlobalExceptionHandler.class
})
class ChatbotConversationControllerTest {

    private static final String TOKEN = "access-token";
    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.parse("2026-07-25T16:00:00+09:00");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatbotConversationService conversationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        when(jwtTokenProvider.parseAccessToken(TOKEN)).thenReturn(42L);
    }

    @Test
    void 정상_요청은_201과_성공_코드를_반환한다() throws Exception {
        when(conversationService.create(15L, 42L))
                .thenReturn(reportResponse());

        mockMvc.perform(post(
                        "/api/v1/apartments/15/chatbot/conversations"
                ).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code")
                        .value("CHATBOT_CONVERSATION_CREATE_SUCCESS"));
    }

    @Test
    void 응답은_정본의_필드_이름을_모두_포함한다() throws Exception {
        when(conversationService.create(15L, 42L))
                .thenReturn(reportResponse());

        mockMvc.perform(post(
                        "/api/v1/apartments/15/chatbot/conversations"
                ).header("Authorization", "Bearer " + TOKEN))
                .andExpect(jsonPath("$.data.conversationId").exists())
                .andExpect(jsonPath("$.data.preferredBasisType").exists())
                .andExpect(jsonPath("$.data.preferredBasisLabel").exists())
                .andExpect(jsonPath("$.data.availableReport").exists())
                .andExpect(jsonPath("$.data.recommendedQuestions").exists())
                .andExpect(jsonPath("$.data.lastMessageAt").hasJsonPath())
                .andExpect(jsonPath("$.data.apartment.address").exists());
    }

    @Test
    void apartmentId가_0이면_COMMON_INVALID_REQUEST다() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/apartments/0/chatbot/conversations"
                ).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void 미인증_요청은_401이다() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/apartments/15/chatbot/conversations"
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(conversationService);
    }

    @Test
    void 리포트가_없으면_WEB과_null_리포트를_반환한다() throws Exception {
        when(conversationService.create(15L, 42L))
                .thenReturn(webResponse());

        mockMvc.perform(post(
                        "/api/v1/apartments/15/chatbot/conversations"
                ).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.availableReport").hasJsonPath())
                .andExpect(jsonPath("$.data.availableReport").isEmpty())
                .andExpect(jsonPath("$.data.preferredBasisType").value("WEB"));
    }

    private ChatbotConversationCreateResponse reportResponse() {
        return new ChatbotConversationCreateResponse(
                41L,
                new ChatbotConversationCreateResponse.ApartmentBody(
                        15L,
                        "래미안 옥수 리버젠",
                        "서울특별시 성동구 매봉길 15"
                ),
                "REPORT",
                "리포트 기반",
                new ChatbotConversationCreateResponse.AvailableReportBody(
                        48L,
                        "래미안 옥수 리버젠 임장 리포트"
                ),
                List.of("교통 어때요?", "시세 알려줘", "스터디 추천"),
                null,
                CREATED_AT
        );
    }

    private ChatbotConversationCreateResponse webResponse() {
        return new ChatbotConversationCreateResponse(
                42L,
                new ChatbotConversationCreateResponse.ApartmentBody(
                        15L,
                        "래미안 옥수 리버젠",
                        "서울특별시 성동구 매봉길 15"
                ),
                "WEB",
                "웹 기반",
                null,
                List.of("교통 어때요?", "시세 알려줘", "스터디 추천"),
                null,
                CREATED_AT
        );
    }
}
