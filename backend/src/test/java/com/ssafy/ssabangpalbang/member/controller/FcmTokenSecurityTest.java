package com.ssafy.ssabangpalbang.member.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.member.dto.response.FcmTokenResponse;
import com.ssafy.ssabangpalbang.member.dto.response.FcmTestPushResponse;
import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;
import com.ssafy.ssabangpalbang.member.service.FcmTestPushService;
import com.ssafy.ssabangpalbang.member.service.FcmTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FcmTokenController.class)
@Import({
        AuthSecurityConfiguration.class,
        GlobalExceptionHandler.class
})
class FcmTokenSecurityTest {

    private static final String URI =
            "/api/v1/members/me/devices/{deviceId}/fcm-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FcmTokenService fcmTokenService;

    @MockitoBean
    private FcmTestPushService fcmTestPushService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void FCM_토큰_등록은_Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(put(URI, "device-A")
                        .contentType(APPLICATION_JSON)
                        .content("{\"fcmToken\":\"token-A\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));
    }

    @Test
    void FCM_토큰_연결_해제는_Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(delete(URI, "device-A"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));
    }

    @Test
    void FCM_테스트_전송은_Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/members/me/devices/{deviceId}/test-fcm-push", "device-A"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(fcmTestPushService, jwtTokenProvider);
    }

    @Test
    void 유효한_Access_Token으로_현재_기기_FCM_테스트_전송을_요청한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(1L);
        when(fcmTestPushService.sendToCurrentDevice("device-A"))
                .thenReturn(new FcmTestPushResponse(
                        "device-A",
                        OffsetDateTime.now(ZoneOffset.ofHours(9))
                ));

        mockMvc.perform(post("/api/v1/members/me/devices/{deviceId}/test-fcm-push", "device-A")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_FCM_TEST_PUSH_SCHEDULED"));

        verify(jwtTokenProvider).parseAccessToken("access-token");
        verify(fcmTestPushService).sendToCurrentDevice("device-A");
    }

    @Test
    void 유효한_Access_Token으로_FCM_토큰을_등록한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(1L);
        when(fcmTokenService.register(anyString(), any()))
                .thenReturn(new FcmTokenService.RegistrationResult(
                        MemberResponseCode.FCM_TOKEN_REGISTERED,
                        new FcmTokenResponse(
                                "device-A",
                                true,
                                OffsetDateTime.now(ZoneOffset.ofHours(9))
                        )
                ));

        mockMvc.perform(put(URI, "device-A")
                        .header("Authorization", "Bearer access-token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"fcmToken\":\"token-A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registered").value(true));

        verify(fcmTokenService).register(anyString(), any());
    }

    @Test
    void rejectsTamperedAccessToken() throws Exception {
        when(jwtTokenProvider.parseAccessToken("tampered-access-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(put(URI, "device-A")
                        .header("Authorization", "Bearer tampered-access-token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"fcmToken\":\"token-A\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(fcmTokenService);
        verify(jwtTokenProvider).parseAccessToken("tampered-access-token");
    }

    @Test
    void rejectsExpiredAccessToken() throws Exception {
        when(jwtTokenProvider.parseAccessToken("expired-access-token"))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_ACCESS_TOKEN_EXPIRED
                ));

        mockMvc.perform(put(URI, "device-A")
                        .header("Authorization", "Bearer expired-access-token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"fcmToken\":\"token-A\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_EXPIRED"));

        verifyNoInteractions(fcmTokenService);
        verify(jwtTokenProvider).parseAccessToken("expired-access-token");
    }

    @Test
    void rejectsRefreshToken() throws Exception {
        when(jwtTokenProvider.parseAccessToken("refresh-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(delete(URI, "device-A")
                        .header("Authorization", "Bearer refresh-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(fcmTokenService);
        verify(jwtTokenProvider).parseAccessToken("refresh-token");
    }
}
