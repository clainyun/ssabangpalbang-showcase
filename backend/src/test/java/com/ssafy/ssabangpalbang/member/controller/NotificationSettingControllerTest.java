package com.ssafy.ssabangpalbang.member.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.member.dto.response.NotificationSettingResponse;
import com.ssafy.ssabangpalbang.member.dto.response.NotificationSettingUpdateResponse;
import com.ssafy.ssabangpalbang.member.service.NotificationSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationSettingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class NotificationSettingControllerTest {

    private static final String URI =
            "/api/v1/members/me/notification-settings";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationSettingService notificationSettingService;

    @Test
    void 알림_수신_설정을_공통_응답으로_반환한다() throws Exception {
        when(notificationSettingService.getNotificationSettings())
                .thenReturn(new NotificationSettingResponse(true, false));

        mockMvc.perform(get(URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_NOTIFICATION_SETTINGS_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("알림 수신 설정 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.serviceNotificationAgreed")
                        .value(true))
                .andExpect(jsonPath("$.data.adNotificationAgreed")
                        .value(false))
                .andExpect(jsonPath("$.data.fcmToken").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.nickname").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(notificationSettingService).getNotificationSettings();
    }

    @Test
    void 회원을_찾지_못하면_404_응답을_반환한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND))
                .when(notificationSettingService)
                .getNotificationSettings();

        mockMvc.perform(get(URI))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 인증에_실패하면_401_응답을_반환한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED))
                .when(notificationSettingService)
                .getNotificationSettings();

        mockMvc.perform(get(URI))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 알림_수신_설정을_수정하고_최신_설정과_수정_시각을_반환한다() throws Exception {
        OffsetDateTime updatedAt = OffsetDateTime.of(
                2026,
                7,
                22,
                10,
                30,
                0,
                0,
                ZoneOffset.ofHours(9)
        );
        when(notificationSettingService.update(any(JsonNode.class)))
                .thenReturn(new NotificationSettingUpdateResponse(
                        false,
                        true,
                        updatedAt
                ));

        mockMvc.perform(patch(URI)
                        .contentType(APPLICATION_JSON)
                        .content("{\"serviceNotificationAgreed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_NOTIFICATION_SETTINGS_UPDATED"))
                .andExpect(jsonPath("$.message")
                        .value("알림 수신 설정이 수정되었습니다."))
                .andExpect(jsonPath("$.data.serviceNotificationAgreed")
                        .value(false))
                .andExpect(jsonPath("$.data.adNotificationAgreed")
                        .value(true))
                .andExpect(jsonPath("$.data.updatedAt")
                        .value("2026-07-22T10:30:00+09:00"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(notificationSettingService).update(any(JsonNode.class));
    }

    @Test
    void 본문이_없으면_변경_항목_없음_오류를_반환한다() throws Exception {
        when(notificationSettingService.update(isNull()))
                .thenThrow(new BusinessException(
                        ErrorCode.MEMBER_NOTIFICATION_SETTINGS_UPDATE_EMPTY
                ));

        mockMvc.perform(patch(URI))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_NOTIFICATION_SETTINGS_UPDATE_EMPTY"))
                .andExpect(jsonPath("$.message")
                        .value("변경할 알림 설정을 입력해 주세요."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 빈_객체는_변경_항목_없음_오류를_반환한다() throws Exception {
        when(notificationSettingService.update(any(JsonNode.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.MEMBER_NOTIFICATION_SETTINGS_UPDATE_EMPTY
                ));

        mockMvc.perform(patch(URI)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_NOTIFICATION_SETTINGS_UPDATE_EMPTY"));
    }

    @Test
    void null_값은_입력값_오류를_반환한다() throws Exception {
        when(notificationSettingService.update(any(JsonNode.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.INVALID_INPUT_VALUE,
                        java.util.Map.of(
                                "field",
                                "serviceNotificationAgreed",
                                "reason",
                                "값을 비워 둘 수 없습니다."
                        )
                ));

        mockMvc.perform(patch(URI)
                        .contentType(APPLICATION_JSON)
                        .content("{\"serviceNotificationAgreed\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field")
                        .value("serviceNotificationAgreed"))
                .andExpect(jsonPath("$.data.reason")
                        .value("값을 비워 둘 수 없습니다."));
    }

    @Test
    void Boolean이_아닌_값은_입력값_오류를_반환한다() throws Exception {
        when(notificationSettingService.update(any(JsonNode.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.INVALID_INPUT_VALUE,
                        java.util.Map.of(
                                "field",
                                "adNotificationAgreed",
                                "reason",
                                "true 또는 false 값을 입력해 주세요."
                        )
                ));

        mockMvc.perform(patch(URI)
                        .contentType(APPLICATION_JSON)
                        .content("{\"adNotificationAgreed\":\"true\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field")
                        .value("adNotificationAgreed"))
                .andExpect(jsonPath("$.data.reason")
                        .value("true 또는 false 값을 입력해 주세요."));
    }
}
