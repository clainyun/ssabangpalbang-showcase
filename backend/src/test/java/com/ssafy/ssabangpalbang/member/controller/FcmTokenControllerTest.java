package com.ssafy.ssabangpalbang.member.controller;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.member.dto.response.FcmTokenDeleteResponse;
import com.ssafy.ssabangpalbang.member.dto.response.FcmTokenResponse;
import com.ssafy.ssabangpalbang.member.dto.response.FcmTestPushResponse;
import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;
import com.ssafy.ssabangpalbang.member.service.FcmTestPushService;
import com.ssafy.ssabangpalbang.member.service.FcmTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FcmTokenController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FcmTokenControllerTest {

    private static final String URI =
            "/api/v1/members/me/devices/{deviceId}/fcm-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FcmTokenService fcmTokenService;

    @MockitoBean
    private FcmTestPushService fcmTestPushService;

    @Test
    void FCM_테스트_예약_성공_응답은_202와_예약_시각만_반환한다() throws Exception {
        when(fcmTestPushService.sendToCurrentDevice("device-A"))
                .thenReturn(new FcmTestPushResponse(
                        "device-A",
                        OffsetDateTime.now(ZoneOffset.ofHours(9))
                ));

        mockMvc.perform(post("/api/v1/members/me/devices/{deviceId}/test-fcm-push", "device-A"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_FCM_TEST_PUSH_SCHEDULED"))
                .andExpect(jsonPath("$.data.deviceId").value("device-A"))
                .andExpect(jsonPath("$.data.scheduledAt").exists())
                .andExpect(jsonPath("$.data.sentAt").doesNotExist())
                .andExpect(jsonPath("$.data.fcmToken").doesNotExist())
                .andExpect(jsonPath("$.data.messageId").doesNotExist());
    }

    @Test
    void FCM_테스트_전송의_토큰_미등록_오류를_반환한다() throws Exception {
        when(fcmTestPushService.sendToCurrentDevice("device-A"))
                .thenThrow(new BusinessException(ErrorCode.MEMBER_FCM_TOKEN_NOT_FOUND));

        mockMvc.perform(post("/api/v1/members/me/devices/{deviceId}/test-fcm-push", "device-A"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_FCM_TOKEN_NOT_FOUND"));
    }

    @Test
    void FCM_테스트_예약의_설정과_스케줄러_오류는_503을_반환한다() throws Exception {
        when(fcmTestPushService.sendToCurrentDevice("disabled-device"))
                .thenThrow(new BusinessException(ErrorCode.FCM_PUSH_NOT_CONFIGURED));
        when(fcmTestPushService.sendToCurrentDevice("rejected-device"))
                .thenThrow(new BusinessException(ErrorCode.FCM_PUSH_SCHEDULING_FAILED));

        mockMvc.perform(post(
                        "/api/v1/members/me/devices/{deviceId}/test-fcm-push",
                        "disabled-device"
                ))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FCM_PUSH_NOT_CONFIGURED"));

        mockMvc.perform(post(
                        "/api/v1/members/me/devices/{deviceId}/test-fcm-push",
                        "rejected-device"
                ))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FCM_PUSH_SCHEDULING_FAILED"));
    }

    @Test
    void FCM_dry_run_검증_실패는_502를_반환한다() throws Exception {
        when(fcmTestPushService.sendToCurrentDevice("invalid-provider-device"))
                .thenThrow(new BusinessException(ErrorCode.FCM_PUSH_DELIVERY_FAILED));

        mockMvc.perform(post(
                        "/api/v1/members/me/devices/{deviceId}/test-fcm-push",
                        "invalid-provider-device"
                ))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("FCM_PUSH_DELIVERY_FAILED"));
    }

    @Test
    void 성공_응답은_공통_응답_필드와_토큰을_제외한_데이터를_포함한다() throws Exception {
        successResult();

        mockMvc.perform(put(URI, "device-A")
                        .contentType(APPLICATION_JSON)
                        .content("{\"fcmToken\":\"token-A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_FCM_TOKEN_SAVED"))
                .andExpect(jsonPath("$.message")
                        .value("기기 알림 정보가 등록되었습니다."))
                .andExpect(jsonPath("$.data.deviceId").value("device-A"))
                .andExpect(jsonPath("$.data.registered").value(true))
                .andExpect(jsonPath("$.data.updatedAt").exists())
                .andExpect(jsonPath("$.data.fcmToken").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 누락된_FCM_토큰은_공통_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(put(URI, "device-A")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("fcmToken"))
                .andExpect(jsonPath("$.data.reason")
                        .value("FCM 토큰은 필수 값입니다."));
    }

    @Test
    void 빈_FCM_토큰은_공통_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(put(URI, "device-A")
                        .contentType(APPLICATION_JSON)
                        .content("{\"fcmToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void 응답_시간은_서울_오프셋_형식으로_직렬화된다() throws Exception {
        successResult();

        mockMvc.perform(put(URI, "device-A")
                        .contentType(APPLICATION_JSON)
                        .content("{\"fcmToken\":\"token-A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedAt").value(matchesPattern(
                        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+09:00$"
                )))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern(
                        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+09:00$"
                )));
    }

    @Test
    void 인증_해결에_실패하면_401을_반환한다() throws Exception {
        when(fcmTokenService.register(anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(put(URI, "device-A")
                        .contentType(APPLICATION_JSON)
                        .content("{\"fcmToken\":\"token-A\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 비활성_회원이면_404를_반환한다() throws Exception {
        when(fcmTokenService.register(anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        mockMvc.perform(put(URI, "device-A")
                        .contentType(APPLICATION_JSON)
                        .content("{\"fcmToken\":\"token-A\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void FCM_잠금_대기시간을_초과하면_503을_반환한다() throws Exception {
        when(fcmTokenService.register(anyString(), any()))
                .thenThrow(new BusinessException(
                        ErrorCode.MEMBER_FCM_TOKEN_LOCK_TIMEOUT
                ));

        mockMvc.perform(put(URI, "device-A")
                        .contentType(APPLICATION_JSON)
                        .content("{\"fcmToken\":\"token-A\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_FCM_TOKEN_LOCK_TIMEOUT"));
    }

    @Test
    void FCM_토큰_삭제_성공_응답은_삭제_상태를_반환한다() throws Exception {
        deleteSuccessResult(MemberResponseCode.FCM_TOKEN_DELETED);

        mockMvc.perform(delete(URI, "device-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_FCM_TOKEN_DELETED"))
                .andExpect(jsonPath("$.message")
                        .value("기기 알림 연결이 해제되었습니다."))
                .andExpect(jsonPath("$.data.deviceId").value("device-A"))
                .andExpect(jsonPath("$.data.registered").value(false))
                .andExpect(jsonPath("$.data.deletedAt").value(matchesPattern(
                        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+09:00$"
                )))
                .andExpect(jsonPath("$.data.fcmToken").doesNotExist())
                .andExpect(jsonPath("$.timestamp").value(matchesPattern(
                        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+09:00$"
                )));
    }

    @Test
    void 이미_삭제된_FCM_토큰_연결도_성공_응답을_반환한다() throws Exception {
        deleteSuccessResult(MemberResponseCode.FCM_TOKEN_ALREADY_DELETED);

        mockMvc.perform(delete(URI, "device-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("MEMBER_FCM_TOKEN_ALREADY_DELETED"))
                .andExpect(jsonPath("$.message")
                        .value("이미 기기 알림 연결이 해제되어 있습니다."))
                .andExpect(jsonPath("$.data.registered").value(false));
    }

    @Test
    void FCM_토큰_삭제의_인증_실패는_401을_반환한다() throws Exception {
        when(fcmTokenService.delete(anyString()))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(delete(URI, "device-A"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void FCM_토큰_삭제의_비활성_회원은_404를_반환한다() throws Exception {
        when(fcmTokenService.delete(anyString()))
                .thenThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        mockMvc.perform(delete(URI, "device-A"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private void successResult() {
        OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.ofHours(9));
        FcmTokenResponse response = new FcmTokenResponse(
                "device-A",
                true,
                updatedAt
        );
        FcmTokenService.RegistrationResult result =
                new FcmTokenService.RegistrationResult(
                        MemberResponseCode.FCM_TOKEN_REGISTERED,
                        response
                );

        when(fcmTokenService.register(anyString(), any())).thenReturn(result);
    }

    private void deleteSuccessResult(MemberResponseCode responseCode) {
        OffsetDateTime deletedAt = OffsetDateTime.now(ZoneOffset.ofHours(9));
        FcmTokenDeleteResponse response = new FcmTokenDeleteResponse(
                "device-A",
                false,
                deletedAt
        );
        FcmTokenService.DeletionResult result =
                new FcmTokenService.DeletionResult(responseCode, response);

        when(fcmTokenService.delete(anyString())).thenReturn(result);
    }
}
