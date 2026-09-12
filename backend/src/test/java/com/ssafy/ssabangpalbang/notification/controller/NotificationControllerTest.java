package com.ssafy.ssabangpalbang.notification.controller;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationItemResponse;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationListResponse;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationReadAllResponse;
import com.ssafy.ssabangpalbang.notification.dto.response.NotificationReadResponse;
import com.ssafy.ssabangpalbang.notification.response.NotificationResponseCode;
import com.ssafy.ssabangpalbang.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void 기본_쿼리_파라미터로_알림_목록을_조회한다() throws Exception {
        successResult();

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("NOTIFICATION_LIST_SUCCESS"))
                .andExpect(jsonPath("$.message").value("알림 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.content[0].notificationId").value(81))
                .andExpect(jsonPath("$.data.content[0].isRead").value(false))
                .andExpect(jsonPath("$.data.content[0].sentAt").exists())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.nextCursor").value("next-opaque-cursor"))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(notificationService).getNotifications(false, null, 10);
    }

    @Test
    void 전달된_쿼리_파라미터를_서비스에_전달한다() throws Exception {
        successResult();

        mockMvc.perform(get("/api/v1/notifications")
                        .queryParam("unreadOnly", "true")
                        .queryParam("cursor", "opaque-cursor")
                        .queryParam("size", "50"))
                .andExpect(status().isOk());

        verify(notificationService).getNotifications(true, "opaque-cursor", 50);
    }

    @Test
    void 유효하지_않은_커서는_전용_오류_응답을_반환한다() throws Exception {
        when(notificationService.getNotifications(anyBoolean(), nullable(String.class), anyInt()))
                .thenThrow(new BusinessException(
                        ErrorCode.NOTIFICATION_CURSOR_INVALID,
                        java.util.Map.of(
                                "field", "cursor",
                                "reason", "커서가 유효하지 않거나 요청 조건과 일치하지 않습니다."
                        )
                ));

        mockMvc.perform(get("/api/v1/notifications").queryParam("cursor", "tampered"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_CURSOR_INVALID"))
                .andExpect(jsonPath("$.data.field").value("cursor"));
    }

    @Test
    void 알림_한_건을_읽음_처리한다() throws Exception {
        readResult(NotificationResponseCode.NOTIFICATION_READ_SUCCESS);

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", "81"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("NOTIFICATION_READ_SUCCESS"))
                .andExpect(jsonPath("$.message").value("알림을 읽음 처리했습니다."))
                .andExpect(jsonPath("$.data.notificationId").value(81))
                .andExpect(jsonPath("$.data.isRead").value(true))
                .andExpect(jsonPath("$.data.readAt").exists())
                .andExpect(jsonPath("$.data.unreadCount").value(2))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(notificationService).readNotification("81");
    }

    @Test
    void 이미_읽은_알림도_정상_응답을_반환한다() throws Exception {
        readResult(NotificationResponseCode.NOTIFICATION_ALREADY_READ);

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", "81"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("NOTIFICATION_ALREADY_READ"))
                .andExpect(jsonPath("$.message").value("이미 읽은 알림입니다."))
                .andExpect(jsonPath("$.data.isRead").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(2));

        verify(notificationService).readNotification("81");
    }

    @Test
    void 알림을_전체_읽음_처리한다() throws Exception {
        readAllResult(NotificationResponseCode.NOTIFICATION_READ_ALL_SUCCESS, 5);

        mockMvc.perform(patch("/api/v1/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("NOTIFICATION_READ_ALL_SUCCESS"))
                .andExpect(jsonPath("$.message").value("모든 알림을 읽음 처리했습니다."))
                .andExpect(jsonPath("$.data.updatedCount").value(5))
                .andExpect(jsonPath("$.data.unreadCount").value(0))
                .andExpect(jsonPath("$.data.readAt").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(notificationService).readAllNotifications();
    }

    @Test
    void 이미_모든_알림을_읽은_경우도_정상_응답을_반환한다() throws Exception {
        readAllResult(NotificationResponseCode.NOTIFICATION_ALREADY_ALL_READ, 0);

        mockMvc.perform(patch("/api/v1/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_ALREADY_ALL_READ"))
                .andExpect(jsonPath("$.message").value("이미 모든 알림을 확인했습니다."))
                .andExpect(jsonPath("$.data.updatedCount").value(0))
                .andExpect(jsonPath("$.data.unreadCount").value(0))
                .andExpect(jsonPath("$.data.readAt").exists());

        verify(notificationService).readAllNotifications();
    }

    @Test
    void 문자_알림_ID도_서비스_검증을_거쳐_전용_오류를_반환한다() throws Exception {
        when(notificationService.readNotification("abc"))
                .thenThrow(new BusinessException(
                        ErrorCode.NOTIFICATION_ID_INVALID,
                        java.util.Map.of(
                                "field", "notificationId",
                                "reason", "알림 ID는 1 이상의 숫자여야 합니다."
                        )
                ));

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_ID_INVALID"))
                .andExpect(jsonPath("$.data.field").value("notificationId"))
                .andExpect(jsonPath("$.data.reason")
                        .value("알림 ID는 1 이상의 숫자여야 합니다."));

        verify(notificationService).readNotification("abc");
    }

    private void successResult() {
        NotificationItemResponse item = new NotificationItemResponse(
                81L,
                "REPORT",
                "REPORT_COMPLETED",
                "AI 임장 리포트가 완성됐어요",
                "임장 리포트가 생성되었습니다.",
                null,
                false,
                null,
                "REPORT_DETAIL",
                48L,
                null,
                true,
                OffsetDateTime.now(ZoneOffset.ofHours(9))
        );
        when(notificationService.getNotifications(anyBoolean(), nullable(String.class), anyInt()))
                .thenReturn(new NotificationListResponse(
                        List.of(item),
                        1L,
                        "next-opaque-cursor",
                        true
                ));
    }

    private void readResult(NotificationResponseCode responseCode) {
        NotificationReadResponse response = new NotificationReadResponse(
                81L,
                true,
                OffsetDateTime.of(
                        2026,
                        7,
                        22,
                        10,
                        30,
                        0,
                        0,
                        ZoneOffset.ofHours(9)
                ),
                2L
        );
        when(notificationService.readNotification(anyString()))
                .thenReturn(new NotificationService.ReadResult(responseCode, response));
    }

    private void readAllResult(
            NotificationResponseCode responseCode,
            int updatedCount
    ) {
        NotificationReadAllResponse response = new NotificationReadAllResponse(
                updatedCount,
                0L,
                OffsetDateTime.of(
                        2026,
                        7,
                        22,
                        10,
                        30,
                        0,
                        0,
                        ZoneOffset.ofHours(9)
                )
        );
        when(notificationService.readAllNotifications())
                .thenReturn(new NotificationService.ReadAllResult(responseCode, response));
    }
}
