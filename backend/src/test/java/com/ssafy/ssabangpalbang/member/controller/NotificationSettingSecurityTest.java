package com.ssafy.ssabangpalbang.member.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.member.dto.response.NotificationSettingResponse;
import com.ssafy.ssabangpalbang.member.dto.response.NotificationSettingUpdateResponse;
import com.ssafy.ssabangpalbang.member.service.NotificationSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationSettingController.class)
@Import({
        AuthSecurityConfiguration.class,
        GlobalExceptionHandler.class
})
class NotificationSettingSecurityTest {

    private static final String URI =
            "/api/v1/members/me/notification-settings";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationSettingService notificationSettingService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void Access_Token이_없으면_401_응답을_반환한다() throws Exception {
        mockMvc.perform(get(URI))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(notificationSettingService, jwtTokenProvider);
    }

    @Test
    void HEAD_요청도_Access_Token이_없으면_401_응답을_반환한다() throws Exception {
        mockMvc.perform(head(URI))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationSettingService, jwtTokenProvider);
    }

    @Test
    void 유효한_Access_Token으로_알림_수신_설정을_조회한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(1L);
        when(notificationSettingService.getNotificationSettings())
                .thenReturn(new NotificationSettingResponse(true, false));

        mockMvc.perform(get(URI)
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_NOTIFICATION_SETTINGS_SUCCESS"))
                .andExpect(jsonPath("$.data.serviceNotificationAgreed")
                        .value(true))
                .andExpect(jsonPath("$.data.adNotificationAgreed")
                        .value(false));

        verify(jwtTokenProvider).parseAccessToken("access-token");
        verify(notificationSettingService).getNotificationSettings();
    }

    @Test
    void 위변조된_Access_Token은_401_응답을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("tampered-access-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(get(URI)
                        .header("Authorization", "Bearer tampered-access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verify(jwtTokenProvider).parseAccessToken("tampered-access-token");
        verifyNoInteractions(notificationSettingService);
    }

    @Test
    void 만료된_Access_Token은_401_응답을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("expired-access-token"))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_ACCESS_TOKEN_EXPIRED
                ));

        mockMvc.perform(get(URI)
                        .header("Authorization", "Bearer expired-access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_EXPIRED"));

        verify(jwtTokenProvider).parseAccessToken("expired-access-token");
        verifyNoInteractions(notificationSettingService);
    }

    @Test
    void PATCH_요청은_Access_Token이_없으면_401_응답을_반환한다() throws Exception {
        mockMvc.perform(patch(URI)
                        .contentType(APPLICATION_JSON)
                        .content("{\"serviceNotificationAgreed\":false}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(notificationSettingService, jwtTokenProvider);
    }

    @Test
    void 유효한_Access_Token으로_알림_수신_설정을_수정한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(1L);
        when(notificationSettingService.update(any(JsonNode.class)))
                .thenReturn(new NotificationSettingUpdateResponse(
                        false,
                        true,
                        OffsetDateTime.now(ZoneOffset.ofHours(9))
                ));

        mockMvc.perform(patch(URI)
                        .header("Authorization", "Bearer access-token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"serviceNotificationAgreed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_NOTIFICATION_SETTINGS_UPDATED"))
                .andExpect(jsonPath("$.data.serviceNotificationAgreed")
                        .value(false))
                .andExpect(jsonPath("$.data.adNotificationAgreed")
                        .value(true));

        verify(jwtTokenProvider).parseAccessToken("access-token");
        verify(notificationSettingService).update(any(JsonNode.class));
    }

    @Test
    void 위변조된_Access_Token으로_PATCH_요청하면_401_응답을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("tampered-access-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(patch(URI)
                        .header(
                                "Authorization",
                                "Bearer tampered-access-token"
                        )
                        .contentType(APPLICATION_JSON)
                        .content("{\"serviceNotificationAgreed\":false}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verify(jwtTokenProvider).parseAccessToken("tampered-access-token");
        verifyNoInteractions(notificationSettingService);
    }
}
