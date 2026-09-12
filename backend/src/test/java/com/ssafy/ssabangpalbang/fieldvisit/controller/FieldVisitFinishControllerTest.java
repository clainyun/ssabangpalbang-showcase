package com.ssafy.ssabangpalbang.fieldvisit.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitCloseResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitFinishCancelResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitFinishResponse;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitCloseService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitCloseVoteService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitFinishCancelService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitFinishService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitParticipantsService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitStartService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitStatusService;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FieldVisitController.class)
@Import({
        AuthSecurityConfiguration.class,
        GlobalExceptionHandler.class
})
class FieldVisitFinishControllerTest {

    private static final String TOKEN = "access-token";
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-07-25T16:00:00+09:00");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FieldVisitStatusService statusService;

    @MockitoBean
    private FieldVisitParticipantsService participantsService;

    @MockitoBean
    private FieldVisitStartService startService;

    @MockitoBean
    private FieldVisitFinishService finishService;

    @MockitoBean
    private FieldVisitFinishCancelService finishCancelService;

    @MockitoBean
    private FieldVisitCloseService closeService;

    @MockitoBean
    private FieldVisitCloseVoteService closeVoteService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        when(jwtTokenProvider.parseAccessToken(TOKEN)).thenReturn(42L);
    }

    @Test
    void 개인_종료는_200과_성공_코드를_반환한다() throws Exception {
        when(finishService.finish(eq(7L), eq(42L), any()))
                .thenReturn(finishResult());

        mockMvc.perform(post("/api/v1/studies/7/field-visit/finish")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "finishConfirmed": true,
                                  "clientRequestId": "finish-request-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("FIELD_VISIT_FINISH_SUCCESS"))
                .andExpect(jsonPath("$.data.participant.stayDurationSec")
                        .exists())
                .andExpect(jsonPath("$.data.sessionEndReason").hasJsonPath())
                .andExpect(jsonPath("$.data.reportTriggered").exists());
    }

    @Test
    void 전체_마감은_200과_성공_코드를_반환한다() throws Exception {
        when(closeService.close(eq(7L), eq(42L), any()))
                .thenReturn(closeResult());

        mockMvc.perform(post("/api/v1/studies/7/field-visit/close")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "closeConfirmed": true,
                                  "clientRequestId": "close-request-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("FIELD_VISIT_CLOSE_SUCCESS"));
    }

    @Test
    void studyId가_0이면_COMMON_INVALID_REQUEST다() throws Exception {
        mockMvc.perform(post("/api/v1/studies/0/field-visit/finish")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "finishConfirmed": true,
                                  "clientRequestId": "finish-request-1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void 미인증_요청은_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/studies/7/field-visit/finish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "finishConfirmed": true,
                                  "clientRequestId": "finish-request-1"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(finishService);
    }

    @Test
    void 개인_종료_취소는_200과_성공_코드를_반환한다() throws Exception {
        when(finishCancelService.cancel(eq(7L), eq(42L), any()))
                .thenReturn(finishCancelResult());

        mockMvc.perform(post("/api/v1/studies/7/field-visit/finish/cancel")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientRequestId": "finish-cancel-request-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("FIELD_VISIT_FINISH_CANCEL_SUCCESS"))
                .andExpect(jsonPath("$.data.participant.status")
                        .value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.participant.endedAt").doesNotExist())
                .andExpect(jsonPath("$.data.participant.endReason").doesNotExist())
                .andExpect(jsonPath("$.data.participant.stayDurationSec")
                        .doesNotExist());
    }

    @Test
    void 종료_취소_요청ID가_없으면_COMMON_INVALID_REQUEST다() throws Exception {
        mockMvc.perform(post("/api/v1/studies/7/field-visit/finish/cancel")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientRequestId": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void 미인증_종료_취소_요청은_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/studies/7/field-visit/finish/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientRequestId": "finish-cancel-request-1"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(finishCancelService);
    }

    private FieldVisitFinishCancelService.FinishCancelResult finishCancelResult() {
        return new FieldVisitFinishCancelService.FinishCancelResult(
                FieldVisitResponseCode.FIELD_VISIT_FINISH_CANCEL_SUCCESS,
                new FieldVisitFinishCancelResponse(
                        7L,
                        100L,
                        new FieldVisitFinishCancelResponse.ParticipantBody(
                                301L,
                                "IN_PROGRESS",
                                NOW.minusHours(1),
                                null,
                                null,
                                null
                        )
                )
        );
    }

    private FieldVisitFinishService.FinishResult finishResult() {
        return new FieldVisitFinishService.FinishResult(
                FieldVisitResponseCode.FIELD_VISIT_FINISH_SUCCESS,
                new FieldVisitFinishResponse(
                        7L,
                        100L,
                        new FieldVisitFinishResponse.ParticipantBody(
                                301L,
                                "ENDED",
                                NOW.minusHours(1),
                                NOW,
                                "SELF_ENDED",
                                3600
                        ),
                        new FieldVisitFinishResponse.ChecklistBody(1, 2, 1),
                        false,
                        null,
                        false,
                        null
                )
        );
    }

    private FieldVisitCloseService.CloseResult closeResult() {
        return new FieldVisitCloseService.CloseResult(
                FieldVisitResponseCode.FIELD_VISIT_CLOSE_SUCCESS,
                new FieldVisitCloseResponse(
                        7L,
                        new FieldVisitCloseResponse.SessionBody(
                                100L,
                                "ENDED",
                                NOW.minusHours(1),
                                NOW,
                                42L,
                                "LEADER_FORCED"
                        ),
                        List.of(),
                        0,
                        true,
                        null,
                        null
                )
        );
    }
}
