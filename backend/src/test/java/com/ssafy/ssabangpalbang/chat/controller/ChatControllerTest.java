package com.ssafy.ssabangpalbang.chat.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.chat.dto.ChatNotificationSettingResponse;
import com.ssafy.ssabangpalbang.chat.service.ChatRestService;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ChatControllerTest {

    private static final String URI = "/api/v1/studies/7/chat/notification-settings";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatRestService chatRestService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedMember(42L),
                        null
                )
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 채팅_푸시_설정_조회는_인증_회원과_스터디를_전달한다() throws Exception {
        when(chatRestService.getNotificationSetting(7L, 42L))
                .thenReturn(new ChatNotificationSettingResponse(7L, true));

        mockMvc.perform(get(URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studyId").value(7))
                .andExpect(jsonPath("$.data.pushEnabled").value(true));

        verify(chatRestService).getNotificationSetting(7L, 42L);
    }

    @Test
    void 채팅_푸시_설정_변경은_boolean_값을_전달한다() throws Exception {
        when(chatRestService.updateNotificationSetting(7L, 42L, false))
                .thenReturn(new ChatNotificationSettingResponse(7L, false));

        mockMvc.perform(patch(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pushEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pushEnabled").value(false));

        verify(chatRestService).updateNotificationSetting(7L, 42L, false);
    }

    @Test
    void 채팅_푸시_설정은_문자열과_숫자_boolean을_거절한다() throws Exception {
        for (String body : new String[]{
                "{\"pushEnabled\":\"false\"}",
                "{\"pushEnabled\":0}"
        }) {
            mockMvc.perform(patch(URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
        }

        verifyNoInteractions(chatRestService);
    }

    @Test
    void 채팅_푸시_설정_누락은_거절한다() throws Exception {
        mockMvc.perform(patch(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("pushEnabled"));

        verifyNoInteractions(chatRestService);
    }
}
