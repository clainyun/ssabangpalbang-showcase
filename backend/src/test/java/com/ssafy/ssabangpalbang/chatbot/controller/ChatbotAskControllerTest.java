package com.ssafy.ssabangpalbang.chatbot.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotMessageCreateResponse;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotMessageCreateResponse.BasisPolicyBody;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotMessageCreateResponse.MessageBody;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotMessageCreateResponse.PollingBody;
import com.ssafy.ssabangpalbang.chatbot.service.ChatbotAskService;
import com.ssafy.ssabangpalbang.chatbot.service.ChatbotMessageService;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatbotMessageController.class)
@Import({
        AuthSecurityConfiguration.class,
        GlobalExceptionHandler.class
})
class ChatbotAskControllerTest {

    private static final String TOKEN = "access-token";
    private static final String URI =
            "/api/v1/apartments/15/chatbot/conversations/41/messages";
    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.parse("2026-07-25T16:10:00+09:00");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatbotAskService askService;

    @MockitoBean
    private ChatbotMessageService messageService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        when(jwtTokenProvider.parseAccessToken(TOKEN)).thenReturn(7L);
    }

    private MockHttpServletRequestBuilder ask(String body) {
        return post(URI)
                .header("Authorization", "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    void w1_정상_요청은_202와_성공_코드를_반환한다() throws Exception {
        when(askService.ask(eq(15L), eq(41L), eq(7L), any()))
                .thenReturn(response());

        mockMvc.perform(ask("{\"content\":\"교통 어때요?\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code")
                        .value("CHATBOT_MESSAGE_ACCEPTED"));
    }

    @Test
    void w2_응답은_정본의_필드를_모두_포함한다() throws Exception {
        when(askService.ask(eq(15L), eq(41L), eq(7L), any()))
                .thenReturn(response());

        mockMvc.perform(ask("{\"content\":\"교통 어때요?\"}"))
                .andExpect(jsonPath("$.data.conversationId").value(41))
                .andExpect(jsonPath("$.data.basisPolicy.basisType")
                        .value("REPORT"))
                .andExpect(jsonPath("$.data.basisPolicy.reportId").value(48))
                .andExpect(jsonPath("$.data.userMessage.messageId").value(53))
                .andExpect(jsonPath("$.data.assistantMessage.status")
                        .value("PENDING"))
                .andExpect(jsonPath("$.data.polling.messageHistoryApi")
                        .exists())
                .andExpect(jsonPath("$.data.polling.recommendedIntervalMs")
                        .value(2500));
    }

    @Test
    void w3_assistantMessage의_content는_null이다() throws Exception {
        when(askService.ask(eq(15L), eq(41L), eq(7L), any()))
                .thenReturn(response());

        mockMvc.perform(ask("{\"content\":\"교통 어때요?\"}"))
                .andExpect(jsonPath("$.data.assistantMessage.content")
                        .hasJsonPath())
                .andExpect(jsonPath("$.data.assistantMessage.content")
                        .isEmpty());
    }

    @Test
    void w4_빈_content는_400이다() throws Exception {
        mockMvc.perform(ask("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(askService);
    }

    @Test
    void w5_미인증_요청은_401이다() throws Exception {
        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"교통 어때요?\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(askService);
    }

    @Test
    void w6_이전_답변_진행_중이면_409다() throws Exception {
        when(askService.ask(eq(15L), eq(41L), eq(7L), any()))
                .thenThrow(new BusinessException(
                        ErrorCode.CHATBOT_RESPONSE_IN_PROGRESS
                ));

        mockMvc.perform(ask("{\"content\":\"교통 어때요?\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("CHATBOT_RESPONSE_IN_PROGRESS"));
    }

    @Test
    void w7_응답에_retryable_필드가_없다() throws Exception {
        when(askService.ask(eq(15L), eq(41L), eq(7L), any()))
                .thenReturn(response());

        mockMvc.perform(ask("{\"content\":\"교통 어때요?\"}"))
                .andExpect(jsonPath("$.data.userMessage.retryable")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.assistantMessage.retryable")
                        .doesNotExist());
    }

    @Test
    void 대화가_없으면_404다() throws Exception {
        when(askService.ask(eq(15L), eq(41L), eq(7L), any()))
                .thenThrow(new BusinessException(
                        ErrorCode.CHATBOT_CONVERSATION_NOT_FOUND
                ));

        mockMvc.perform(ask("{\"content\":\"교통 어때요?\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("CHATBOT_CONVERSATION_NOT_FOUND"));
    }

    @Test
    void 아파트_ID가_0이면_400이다() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/apartments/0/chatbot/conversations/41/messages"
                )
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"교통 어때요?\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    private ChatbotMessageCreateResponse response() {
        return new ChatbotMessageCreateResponse(
                41L,
                new BasisPolicyBody("REPORT", "리포트 기반", 48L),
                new MessageBody(
                        53L, "USER", "교통 어때요?", "COMPLETED",
                        "NONE", null, List.of(), null, CREATED_AT, CREATED_AT
                ),
                new MessageBody(
                        54L, "ASSISTANT", null, "PENDING",
                        "REPORT", "리포트 기반", List.of(), null,
                        CREATED_AT, null
                ),
                new PollingBody(
                        "/api/v1/apartments/15/chatbot/conversations/41/messages",
                        2500
                )
        );
    }
}
