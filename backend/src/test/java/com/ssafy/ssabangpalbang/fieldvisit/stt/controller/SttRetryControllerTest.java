package com.ssafy.ssabangpalbang.fieldvisit.stt.controller;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.request.SttRetryRequest;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.response.SttRetryResponse;
import com.ssafy.ssabangpalbang.fieldvisit.stt.response.SttResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttService;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SttController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SttRetryControllerTest {

    private static final String URI =
            "/api/v1/studies/{studyId}/field-visit/stt/{sttId}/retry";
    private static final OffsetDateTime RETRY_REQUESTED_AT =
            OffsetDateTime.of(
                    2026,
                    7,
                    25,
                    14,
                    30,
                    0,
                    0,
                    ZoneOffset.ofHours(9)
            );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SttService sttService;

    @Test
    void 신규_재처리는_202와_확정_응답을_반환한다() throws Exception {
        when(sttService.retryStt(
                eq(7L),
                eq("stt-retry"),
                any(SttRetryRequest.class)
        )).thenReturn(result(
                HttpStatus.ACCEPTED,
                SttResponseCode.FIELD_STT_RETRY_ACCEPTED,
                SttStatus.PENDING
        ));

        mockMvc.perform(post(URI, 7L, "stt-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("FIELD_STT_RETRY_ACCEPTED"))
                .andExpect(jsonPath("$.message")
                        .value("음성 변환을 다시 시작했습니다."))
                .andExpect(jsonPath("$.data.sttId").value("stt-retry"))
                .andExpect(jsonPath("$.data.studyId").value(7L))
                .andExpect(jsonPath("$.data.checklistItemId").value(503L))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.sourceId").doesNotExist())
                .andExpect(jsonPath("$.data.retryRequestedAt").value(
                        "2026-07-25T14:30:00+09:00"
                ));
    }

    @Test
    void 진행_중_재처리는_200과_최초_수락_시각을_반환한다()
            throws Exception {
        when(sttService.retryStt(
                eq(7L),
                eq("stt-retry"),
                any(SttRetryRequest.class)
        )).thenReturn(result(
                HttpStatus.OK,
                SttResponseCode.FIELD_STT_RETRY_ALREADY_IN_PROGRESS,
                SttStatus.PROCESSING
        ));

        mockMvc.perform(post(URI, 7L, "stt-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(
                        "FIELD_STT_RETRY_ALREADY_IN_PROGRESS"
                ))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.retryRequestedAt").value(
                        "2026-07-25T14:30:00+09:00"
                ));
    }

    @Test
    void 재처리_요청_필수값을_검증한다() throws Exception {
        mockMvc.perform(post(URI, 0L, " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(sttService);
    }

    @Test
    void UUID_오류는_clientRequestId_필드를_반환한다() throws Exception {
        when(sttService.retryStt(
                eq(7L),
                eq("stt-retry"),
                any(SttRetryRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                Map.of(
                        "field", "clientRequestId",
                        "reason", "clientRequestId는 UUID 형식이어야 합니다."
                )
        ));

        mockMvc.perform(post(URI, 7L, "stt-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId": "invalid"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("clientRequestId"));
    }

    @Test
    void 재처리_업무_예외를_HTTP_status로_매핑한다() throws Exception {
        assertMapped(ErrorCode.FIELD_STT_STUDY_MISMATCH, 400);
        assertMapped(ErrorCode.FIELD_STT_RETRY_FORBIDDEN, 403);
        assertMapped(ErrorCode.FIELD_STT_NOT_FOUND, 404);
        assertMapped(ErrorCode.FIELD_STT_RETRY_NOT_ALLOWED, 409);
        assertMapped(ErrorCode.FIELD_STT_AUDIO_EXPIRED, 410);
    }

    private void assertMapped(
            ErrorCode errorCode,
            int expectedStatus
    ) throws Exception {
        reset(sttService);
        when(sttService.retryStt(
                eq(7L),
                eq("stt-retry"),
                any(SttRetryRequest.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(post(URI, 7L, "stt-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.getCode()));
    }

    private SttService.RetryResult result(
            HttpStatus httpStatus,
            SttResponseCode responseCode,
            SttStatus status
    ) {
        return new SttService.RetryResult(
                httpStatus,
                responseCode,
                new SttRetryResponse(
                        "stt-retry",
                        7L,
                        503L,
                        status,
                        null,
                        RETRY_REQUESTED_AT
                )
        );
    }

    private String validRequest() {
        return """
                {
                  "clientRequestId": "55591972-492e-4c29-81bd-eb203f37be49"
                }
                """;
    }
}
