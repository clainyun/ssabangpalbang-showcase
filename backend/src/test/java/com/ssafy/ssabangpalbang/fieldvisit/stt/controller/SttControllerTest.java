package com.ssafy.ssabangpalbang.fieldvisit.stt.controller;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.request.SttCreateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.response.SttCreateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.response.SttStatusResponse;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SttController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SttControllerTest {

    private static final String URI =
            "/api/v1/studies/{studyId}/field-visit/stt";
    private static final String STATUS_URI =
            "/api/v1/studies/{studyId}/field-visit/stt/{sttId}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SttService sttService;

    @Test
    void 신규_요청은_202와_전체_응답_필드를_반환한다() throws Exception {
        when(sttService.createStt(eq(7L), any(SttCreateRequest.class)))
                .thenReturn(result(
                        HttpStatus.ACCEPTED,
                        SttResponseCode.FIELD_STT_ACCEPTED
                ));

        mockMvc.perform(post(URI, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("FIELD_STT_ACCEPTED"))
                .andExpect(jsonPath("$.data.sttId").value("stt-4c7186fb"))
                .andExpect(jsonPath("$.data.studyId").value(7L))
                .andExpect(jsonPath("$.data.sessionId").value(100L))
                .andExpect(jsonPath("$.data.audioFileId").value(90L))
                .andExpect(jsonPath("$.data.checklistItemId").value(501L))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.sourceId").doesNotExist())
                .andExpect(jsonPath("$.data.requestedAt").value(
                        "2026-07-25T14:25:00+09:00"
                ));

        verify(sttService).createStt(eq(7L), any(SttCreateRequest.class));
    }

    @Test
    void 동일_요청은_200을_반환한다() throws Exception {
        when(sttService.createStt(eq(7L), any(SttCreateRequest.class)))
                .thenReturn(result(
                        HttpStatus.OK,
                        SttResponseCode.FIELD_STT_ALREADY_REQUESTED
                ));

        mockMvc.perform(post(URI, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("FIELD_STT_ALREADY_REQUESTED"));
    }

    @Test
    void 필수_ID와_studyId_범위를_검증한다() throws Exception {
        mockMvc.perform(post(URI, 0L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "audioFileId": 0,
                                  "checklistItemId": null,
                                  "clientRequestId": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(sttService);
    }

    @Test
    void UUID_형식_오류의_필드_정보를_반환한다() throws Exception {
        when(sttService.createStt(eq(7L), any(SttCreateRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.INVALID_INPUT_VALUE,
                        Map.of(
                                "field", "clientRequestId",
                                "reason", "clientRequestId는 UUID 형식이어야 합니다."
                        )
                ));

        mockMvc.perform(post(URI, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "audioFileId": 90,
                                  "checklistItemId": 501,
                                  "clientRequestId": "invalid"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("clientRequestId"));
    }

    @Test
    void STT_업무_예외를_명세_HTTP_status로_매핑한다() throws Exception {
        assertMapped(ErrorCode.FIELD_STT_AUDIO_INVALID, 400);
        assertMapped(ErrorCode.FIELD_STT_IDEMPOTENCY_KEY_REUSED, 400);
        assertMapped(ErrorCode.FIELD_STT_REQUEST_FORBIDDEN, 403);
        assertMapped(ErrorCode.MEDIA_FILE_NOT_FOUND, 404);
        assertMapped(ErrorCode.CHECKLIST_ITEM_NOT_FOUND, 404);
        assertMapped(ErrorCode.FIELD_PARTICIPANT_ALREADY_ENDED, 409);
        assertMapped(ErrorCode.FIELD_STT_AUDIO_EXPIRED, 410);
    }

    @Test
    void 처리_중_상태는_200과_no_store를_반환한다() throws Exception {
        when(sttService.getSttStatus(7L, "stt-processing"))
                .thenReturn(statusResponse(
                        "stt-processing",
                        SttStatus.PROCESSING,
                        null,
                        null,
                        null,
                        false,
                        null
                ));

        mockMvc.perform(get(STATUS_URI, 7L, "stt-processing"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("FIELD_STT_STATUS_SUCCESS"))
                .andExpect(jsonPath("$.data.sttId")
                        .value("stt-processing"))
                .andExpect(jsonPath("$.data.studyId").value(7L))
                .andExpect(jsonPath("$.data.checklistItemId").value(501L))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.sourceId").doesNotExist())
                .andExpect(jsonPath("$.data.textContent").doesNotExist())
                .andExpect(jsonPath("$.data.failReason").doesNotExist())
                .andExpect(jsonPath("$.data.retryable").value(false))
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());
    }

    @Test
    void 완료_상태는_현장_기록_결과를_반환한다() throws Exception {
        when(sttService.getSttStatus(7L, "stt-done"))
                .thenReturn(statusResponse(
                        "stt-done",
                        SttStatus.DONE,
                        830L,
                        "역에서 단지 입구까지 경사가 있습니다.",
                        null,
                        false,
                        OffsetDateTime.of(
                                2026,
                                7,
                                25,
                                14,
                                25,
                                8,
                                0,
                                ZoneOffset.ofHours(9)
                        )
                ));

        mockMvc.perform(get(STATUS_URI, 7L, "stt-done"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("FIELD_STT_STATUS_SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.sourceId").value(830L))
                .andExpect(jsonPath("$.data.textContent")
                        .value("역에서 단지 입구까지 경사가 있습니다."))
                .andExpect(jsonPath("$.data.completedAt")
                        .value("2026-07-25T14:25:08+09:00"))
                .andExpect(jsonPath("$.data.audioFileId").doesNotExist())
                .andExpect(jsonPath("$.data.objectKey").doesNotExist())
                .andExpect(jsonPath("$.data.s3Key").doesNotExist())
                .andExpect(jsonPath("$.data.audioUrl").doesNotExist());
    }

    @Test
    void 실패_상태는_사용자용_사유와_retryable을_반환한다() throws Exception {
        when(sttService.getSttStatus(7L, "stt-failed"))
                .thenReturn(statusResponse(
                        "stt-failed",
                        SttStatus.FAILED,
                        null,
                        null,
                        "인식 가능한 발화를 찾지 못했습니다.",
                        true,
                        null
                ));

        mockMvc.perform(get(STATUS_URI, 7L, "stt-failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failReason")
                        .value("인식 가능한 발화를 찾지 못했습니다."))
                .andExpect(jsonPath("$.data.retryable").value(true))
                .andExpect(jsonPath("$.data.sourceId").doesNotExist())
                .andExpect(jsonPath("$.data.textContent").doesNotExist())
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());
    }

    @Test
    void 상태_조회_오류를_명세_HTTP_status로_매핑한다() throws Exception {
        assertStatusMapped(ErrorCode.FIELD_STT_STUDY_MISMATCH, 400);
        assertStatusMapped(ErrorCode.FIELD_STT_STATUS_FORBIDDEN, 403);
        assertStatusMapped(ErrorCode.FIELD_STT_NOT_FOUND, 404);
    }

    @Test
    void 다른_회원_상태_조회는_확정된_오류_메시지를_반환한다() throws Exception {
        when(sttService.getSttStatus(7L, "stt-status"))
                .thenThrow(new BusinessException(
                        ErrorCode.FIELD_STT_STATUS_FORBIDDEN
                ));

        mockMvc.perform(get(STATUS_URI, 7L, "stt-status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("본인이 요청한 음성 변환만 확인할 수 있습니다."));
    }

    @Test
    void 상태_조회_studyId_범위를_검증한다() throws Exception {
        mockMvc.perform(get(STATUS_URI, 0L, "stt-status"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(sttService);
    }

    private void assertMapped(
            ErrorCode errorCode,
            int expectedStatus
    ) throws Exception {
        reset(sttService);
        when(sttService.createStt(eq(7L), any(SttCreateRequest.class)))
                .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(post(URI, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.getCode()));
    }

    private void assertStatusMapped(
            ErrorCode errorCode,
            int expectedStatus
    ) throws Exception {
        reset(sttService);
        when(sttService.getSttStatus(7L, "stt-status"))
                .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(get(STATUS_URI, 7L, "stt-status"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.getCode()));
    }

    private SttService.CreateResult result(
            HttpStatus status,
            SttResponseCode responseCode
    ) {
        return new SttService.CreateResult(
                status,
                responseCode,
                new SttCreateResponse(
                        "stt-4c7186fb",
                        7L,
                        100L,
                        90L,
                        501L,
                        SttStatus.PENDING,
                        null,
                        OffsetDateTime.of(
                                2026,
                                7,
                                25,
                                14,
                                25,
                                0,
                                0,
                                ZoneOffset.ofHours(9)
                        )
                )
        );
    }

    private SttStatusResponse statusResponse(
            String sttId,
            SttStatus status,
            Long sourceId,
            String textContent,
            String failReason,
            boolean retryable,
            OffsetDateTime completedAt
    ) {
        return new SttStatusResponse(
                sttId,
                7L,
                501L,
                status,
                sourceId,
                textContent,
                failReason,
                retryable,
                OffsetDateTime.of(
                        2026,
                        7,
                        25,
                        14,
                        25,
                        0,
                        0,
                        ZoneOffset.ofHours(9)
                ),
                completedAt
        );
    }

    private String validRequest() {
        return """
                {
                  "audioFileId": 90,
                  "checklistItemId": 501,
                  "clientRequestId": "81197c8f-780b-40c2-abf6-b83473de9c82"
                }
                """;
    }
}
