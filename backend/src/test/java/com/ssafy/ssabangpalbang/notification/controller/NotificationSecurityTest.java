package com.ssafy.ssabangpalbang.notification.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationListResponse;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationReadAllResponse;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationReadResponse;
import com.ssafy.ssabangpalbang.notification.response.NotificationResponseCode;
import com.ssafy.ssabangpalbang.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({
        AuthSecurityConfiguration.class,
        GlobalExceptionHandler.class
})
class NotificationSecurityTest {

    private static final String LIST_URI = "/api/v1/notifications";
    private static final String READ_URI =
            "/api/v1/notifications/{notificationId}/read";
    private static final String READ_ALL_URI = "/api/v1/notifications/read-all";
    private static final String CONTEXT_PATH = "/gateway";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 알림_목록_조회는_Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get(LIST_URI))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void 알림_한_건_읽음_처리는_Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(patch(READ_URI, "81"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void 알림_전체_읽음_처리는_Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(patch(READ_ALL_URI))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void 변조된_Access_Token으로_알림을_전체_읽음_처리하면_401을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("tampered-access-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(patch(READ_ALL_URI)
                        .header("Authorization", "Bearer tampered-access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(notificationService);
        verify(jwtTokenProvider).parseAccessToken("tampered-access-token");
    }

    @Test
    void Refresh_Token으로_알림을_전체_읽음_처리하면_401을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("refresh-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(patch(READ_ALL_URI)
                        .header("Authorization", "Bearer refresh-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(notificationService);
        verify(jwtTokenProvider).parseAccessToken("refresh-token");
    }

    @Test
    void Bearer_형식이_아닌_인증_헤더로_알림_목록을_조회하면_401을_반환한다() throws Exception {
        mockMvc.perform(get(LIST_URI)
                        .header("Authorization", "Basic access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(notificationService, jwtTokenProvider);
    }

    @Test
    void 빈_Bearer_Token으로_알림_한_건을_읽음_처리하면_401을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken(""))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(patch(READ_URI, "81")
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(notificationService);
        verify(jwtTokenProvider).parseAccessToken("");
    }

    @Test
    void 유효한_Access_Token으로_알림_목록을_조회한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(1L);
        when(notificationService.getNotifications(
                anyBoolean(),
                nullable(String.class),
                anyInt()
        )).thenReturn(new NotificationListResponse(List.of(), 0L, null, false));

        mockMvc.perform(get(LIST_URI)
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("NOTIFICATION_LIST_SUCCESS"));

        verify(notificationService).getNotifications(false, null, 10);
    }

    @Test
    void Context_Path가_있어도_유효한_Access_Token으로_알림_목록을_조회한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(1L);
        when(notificationService.getNotifications(
                anyBoolean(),
                nullable(String.class),
                anyInt()
        )).thenReturn(new NotificationListResponse(List.of(), 0L, null, false));

        mockMvc.perform(get(CONTEXT_PATH + LIST_URI)
                        .contextPath(CONTEXT_PATH)
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("NOTIFICATION_LIST_SUCCESS"));

        verify(jwtTokenProvider).parseAccessToken("access-token");
        verify(notificationService).getNotifications(false, null, 10);
    }

    @Test
    void 유효한_Access_Token으로_알림_한_건을_읽음_처리한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(1L);
        when(notificationService.readNotification(anyString()))
                .thenReturn(new NotificationService.ReadResult(
                        NotificationResponseCode.NOTIFICATION_READ_SUCCESS,
                        new NotificationReadResponse(
                                81L,
                                true,
                                OffsetDateTime.now(ZoneOffset.ofHours(9)),
                                0L
                        )
                ));

        mockMvc.perform(patch(READ_URI, "81")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("NOTIFICATION_READ_SUCCESS"));

        verify(notificationService).readNotification("81");
    }

    @Test
    void 유효한_Access_Token으로_알림을_전체_읽음_처리한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(1L);
        when(notificationService.readAllNotifications())
                .thenReturn(new NotificationService.ReadAllResult(
                        NotificationResponseCode.NOTIFICATION_READ_ALL_SUCCESS,
                        new NotificationReadAllResponse(
                                5,
                                0L,
                                OffsetDateTime.now(ZoneOffset.ofHours(9))
                        )
                ));

        mockMvc.perform(patch(READ_ALL_URI)
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("NOTIFICATION_READ_ALL_SUCCESS"));

        verify(notificationService).readAllNotifications();
    }

    @Test
    void 변조된_Access_Token으로_알림_목록을_조회하면_401을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("tampered-access-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(get(LIST_URI)
                        .header("Authorization", "Bearer tampered-access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(notificationService);
        verify(jwtTokenProvider).parseAccessToken("tampered-access-token");
    }

    @Test
    void 만료된_Access_Token으로_알림_한_건을_읽음_처리하면_401을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("expired-access-token"))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_ACCESS_TOKEN_EXPIRED
                ));

        mockMvc.perform(patch(READ_URI, "81")
                        .header("Authorization", "Bearer expired-access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_EXPIRED"));

        verifyNoInteractions(notificationService);
        verify(jwtTokenProvider).parseAccessToken("expired-access-token");
    }

    @Test
    void 만료된_Access_Token으로_알림을_전체_읽음_처리하면_401을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("expired-access-token"))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_ACCESS_TOKEN_EXPIRED
                ));

        mockMvc.perform(patch(READ_ALL_URI)
                        .header("Authorization", "Bearer expired-access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_EXPIRED"));

        verifyNoInteractions(notificationService);
        verify(jwtTokenProvider).parseAccessToken("expired-access-token");
    }

    @Test
    void Refresh_Token으로_알림_목록을_조회하면_401을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("refresh-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(get(LIST_URI)
                        .header("Authorization", "Bearer refresh-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(notificationService);
        verify(jwtTokenProvider).parseAccessToken("refresh-token");
    }

    @Test
    void 유효한_Access_Token의_잘못된_알림_ID는_기존_검증_응답을_유지한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(1L);
        when(notificationService.readNotification("abc"))
                .thenThrow(new BusinessException(
                        ErrorCode.NOTIFICATION_ID_INVALID,
                        java.util.Map.of(
                                "field", "notificationId",
                                "reason", "알림 ID는 1 이상의 숫자여야 합니다."
                        )
                ));

        mockMvc.perform(patch(READ_URI, "abc")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("NOTIFICATION_ID_INVALID"));

        verify(notificationService).readNotification("abc");
    }
}
