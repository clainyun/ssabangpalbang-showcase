package com.ssafy.ssabangpalbang.chatbot.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotMessageListResponse;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatbotMessageController.class)
@Import({
        AuthSecurityConfiguration.class,
        GlobalExceptionHandler.class
})
class ChatbotMessageControllerTest {

    private static final String TOKEN = "access-token";
    private static final String URI =
            "/api/v1/apartments/15/chatbot/conversations/41/messages";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatbotMessageService messageService;

    /** 질문 전송(BE-026 3/3)이 같은 컨트롤러에 붙어 빈이 필요하다. */
    @MockitoBean
    private ChatbotAskService askService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        when(jwtTokenProvider.parseAccessToken(TOKEN)).thenReturn(7L);
    }

    @Test
    void 정상_조회는_200과_성공_코드를_반환한다() throws Exception {
        when(messageService.getMessages(15L, 41L, 7L, null, 20))
                .thenReturn(response());

        mockMvc.perform(authenticatedGet(URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("CHATBOT_MESSAGE_LIST_SUCCESS"));
    }

    @Test
    void 응답은_정본의_필드_이름을_포함한다() throws Exception {
        when(messageService.getMessages(15L, 41L, 7L, null, 20))
                .thenReturn(response());

        mockMvc.perform(authenticatedGet(URI))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.nextCursor").hasJsonPath())
                .andExpect(jsonPath("$.data.hasNext").exists())
                .andExpect(jsonPath("$.data.lastMessageAt").hasJsonPath())
                .andExpect(jsonPath("$.data.hasResponseInProgress").exists())
                .andExpect(jsonPath("$.data.content[0].messageId").exists())
                .andExpect(jsonPath("$.data.content[0].basisType").exists())
                .andExpect(jsonPath("$.data.content[0].retryable").exists());
    }

    @Test
    void size가_범위를_벗어나면_COMMON_INVALID_REQUEST다() throws Exception {
        mockMvc.perform(authenticatedGet(URI + "?size=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
        mockMvc.perform(authenticatedGet(URI + "?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void size를_생략하면_기본값_20을_전달한다() throws Exception {
        when(messageService.getMessages(15L, 41L, 7L, null, 20))
                .thenReturn(response());

        mockMvc.perform(authenticatedGet(URI))
                .andExpect(status().isOk());

        verify(messageService).getMessages(15L, 41L, 7L, null, 20);
    }

    @Test
    void cursor를_Service에_그대로_전달한다() throws Exception {
        when(messageService.getMessages(15L, 41L, 7L, 52L, 10))
                .thenReturn(response());

        mockMvc.perform(authenticatedGet(URI + "?cursor=52&size=10"))
                .andExpect(status().isOk());

        verify(messageService).getMessages(15L, 41L, 7L, 52L, 10);
    }

    @Test
    void 미인증_요청은_401이다() throws Exception {
        mockMvc.perform(get(URI))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(messageService);
    }

    @Test
    void 다른_회원의_대화는_403이다() throws Exception {
        when(messageService.getMessages(15L, 41L, 7L, null, 20))
                .thenThrow(new BusinessException(
                        ErrorCode.CHATBOT_CONVERSATION_ACCESS_DENIED
                ));

        mockMvc.perform(authenticatedGet(URI))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CHATBOT_CONVERSATION_ACCESS_DENIED"));
    }

    @Test
    void 아파트_불일치는_400이다() throws Exception {
        when(messageService.getMessages(15L, 41L, 7L, null, 20))
                .thenThrow(new BusinessException(
                        ErrorCode.CHATBOT_APARTMENT_MISMATCH
                ));

        mockMvc.perform(authenticatedGet(URI))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("CHATBOT_APARTMENT_MISMATCH"));
    }

    @Test
    void apartment에는_address가_없다() throws Exception {
        when(messageService.getMessages(15L, 41L, 7L, null, 20))
                .thenReturn(response());

        mockMvc.perform(authenticatedGet(URI))
                .andExpect(jsonPath("$.data.apartment.apartmentId").exists())
                .andExpect(jsonPath("$.data.apartment.name").exists())
                .andExpect(jsonPath("$.data.apartment.address")
                        .doesNotExist());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    authenticatedGet(String uri) {
        return get(uri).header("Authorization", "Bearer " + TOKEN);
    }

    private ChatbotMessageListResponse response() {
        OffsetDateTime now = OffsetDateTime.parse(
                "2026-07-25T16:05:00+09:00"
        );
        return new ChatbotMessageListResponse(
                41L,
                new ChatbotMessageListResponse.ApartmentBody(15L, "래미안"),
                List.of(new ChatbotMessageListResponse.MessageBody(
                        51L,
                        "USER",
                        "교통 어때요?",
                        "COMPLETED",
                        "NONE",
                        null,
                        List.of(),
                        null,
                        false,
                        now,
                        now
                )),
                null,
                false,
                now,
                false
        );
    }
}
