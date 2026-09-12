package com.ssafy.ssabangpalbang.fieldvisit.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitStartRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitParticipantsResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitStartResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitStatusResponse;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitCloseService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitCloseVoteService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitFinishCancelService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitFinishService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitParticipantsService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitStartService;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitStatusService;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FieldVisitController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FieldVisitControllerTest {

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

    @BeforeEach
    void setUp() {
        AuthenticatedMember member = new AuthenticatedMember(42L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member, null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 상태_조회는_200을_반환한다() throws Exception {
        when(statusService.getStatus(7L, 42L)).thenReturn(
                new FieldVisitStatusService.StatusResult(
                        FieldVisitResponseCode.FIELD_VISIT_STATUS_SUCCESS,
                        new FieldVisitStatusResponse(
                                7L,
                                "NOT_STARTED",
                                null,
                                null,
                                new FieldVisitStatusResponse.ChecklistProgressBody(
                                        false, null, 0, 0, 0
                                ),
                                new FieldVisitStatusResponse.PermissionsBody(
                                        true, false, false, false, false
                                ),
                                new com.ssafy.ssabangpalbang.fieldvisit.dto.response
                                        .FieldVisitCloseVoteStatusBody(
                                        0, 0, 0, false, false
                                )
                        )
                )
        );

        mockMvc.perform(get("/api/v1/studies/7/field-visit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FIELD_VISIT_STATUS_SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.permissions.canStart").value(true));
    }

    @Test
    void 참여자_상태_조회는_200을_반환한다() throws Exception {
        OffsetDateTime startedAt = OffsetDateTime.parse("2026-07-25T14:00:00+09:00");
        when(participantsService.getParticipants(7L, 42L)).thenReturn(
                new FieldVisitParticipantsService.ParticipantsResult(
                        FieldVisitResponseCode.FIELD_VISIT_PARTICIPANTS_SUCCESS,
                        new FieldVisitParticipantsResponse(
                                7L,
                                100L,
                                "IN_PROGRESS",
                                2,
                                1,
                                0,
                                1,
                                List.of(
                                        new FieldVisitParticipantsResponse.ParticipantBody(
                                                301L, 42L, "루돌푸", null, "PALBANG",
                                                "LEADER", "IN_PROGRESS", startedAt, null,
                                                null, 1830, true, true, false
                                        ),
                                        new FieldVisitParticipantsResponse.ParticipantBody(
                                                null, 72L, "옥수초보", null, "PALBANG",
                                                "MEMBER", "NOT_JOINED", null, null,
                                                null, 0, false, false, false
                                        )
                                )
                        )
                )
        );

        mockMvc.perform(get("/api/v1/studies/7/field-visit/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FIELD_VISIT_PARTICIPANTS_SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.sessionId").value(100))
                .andExpect(jsonPath("$.data.participantCount").value(2))
                .andExpect(jsonPath("$.data.notJoinedCount").value(1))
                .andExpect(jsonPath("$.data.participants[0].memberId").value(42))
                .andExpect(jsonPath("$.data.participants[0].isLeader").value(true))
                .andExpect(jsonPath("$.data.participants[0].isMe").value(true))
                .andExpect(jsonPath("$.data.participants[1].participantId").value(
                        org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.participants[1].status").value("NOT_JOINED"));
    }

    @Test
    void 최초_시작은_201을_반환한다() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-25T14:00:00+09:00");
        when(startService.start(
                eq(7L),
                eq(42L),
                argThat(FieldVisitStartRequestBody::isScheduleOverrideConfirmed)
        )).thenReturn(
                new FieldVisitStartService.StartResult(
                        HttpStatus.CREATED,
                        FieldVisitResponseCode.FIELD_VISIT_START_SUCCESS,
                        new FieldVisitStartResponse(
                                7L, 25L, 82, 1000,
                                new FieldVisitStartResponse.SessionBody(
                                        100L, "IN_PROGRESS", now
                                ),
                                new FieldVisitStartResponse.ParticipantBody(
                                        301L, "IN_PROGRESS", now
                                ),
                                false
                        )
                )
        );

        mockMvc.perform(post("/api/v1/studies/7/field-visit/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": 37.5133,
                                  "longitude": 127.0842,
                                  "clientRequestId": "a1b2c3d4-1234-5678-90ab-cdef12345678",
                                  "scheduleOverrideConfirmed": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("FIELD_VISIT_START_SUCCESS"))
                .andExpect(jsonPath("$.data.session.sessionId").value(100));
    }

    @Test
    void 위도_누락은_FIELD_VISIT_LOCATION_INVALID다() throws Exception {
        mockMvc.perform(post("/api/v1/studies/7/field-visit/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "longitude": 127.0842,
                                  "clientRequestId": "a1b2c3d4-1234-5678-90ab-cdef12345678"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FIELD_VISIT_LOCATION_INVALID"))
                .andExpect(jsonPath("$.data.field").value("latitude"));
    }

    @Test
    void UUID_오류는_COMMON_INVALID_REQUEST다() throws Exception {
        mockMvc.perform(post("/api/v1/studies/7/field-visit/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": 37.5133,
                                  "longitude": 127.0842,
                                  "clientRequestId": "not-a-uuid"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void 반경_밖은_422다() throws Exception {
        when(startService.start(eq(7L), eq(42L), any())).thenThrow(
                new BusinessException(
                        ErrorCode.FIELD_VISIT_OUT_OF_RANGE,
                        Map.of("distanceMeters", 1200, "allowedRadiusMeters", 1000)
                )
        );

        mockMvc.perform(post("/api/v1/studies/7/field-visit/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": 37.5200,
                                  "longitude": 127.0842,
                                  "clientRequestId": "a1b2c3d4-1234-5678-90ab-cdef12345678"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FIELD_VISIT_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.message").value("임장 지역이 아닙니다."))
                .andExpect(jsonPath("$.data.distanceMeters").value(1200))
                .andExpect(jsonPath("$.data.allowedRadiusMeters").value(1000));
    }

    @Test
    void 과반수_종료_투표는_200을_반환한다() throws Exception {
        when(closeVoteService.vote(7L, 42L)).thenReturn(
                new FieldVisitCloseVoteService.VoteResult(
                        FieldVisitResponseCode.FIELD_VISIT_CLOSE_VOTE_SUCCESS,
                        new com.ssafy.ssabangpalbang.fieldvisit.dto.response
                                .FieldVisitCloseVoteResponse(
                                7L,
                                100L,
                                3,
                                1,
                                2,
                                true,
                                false,
                                false,
                                null,
                                false,
                                null,
                                null
                        )
                )
        );

        mockMvc.perform(post("/api/v1/studies/7/field-visit/close-votes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FIELD_VISIT_CLOSE_VOTE_SUCCESS"))
                .andExpect(jsonPath("$.data.startedParticipantCount").value(3))
                .andExpect(jsonPath("$.data.voteCount").value(1))
                .andExpect(jsonPath("$.data.requiredVoteCount").value(2))
                .andExpect(jsonPath("$.data.hasVoted").value(true))
                .andExpect(jsonPath("$.data.canVote").value(false))
                .andExpect(jsonPath("$.data.sessionEnded").value(false));
    }

    @Test
    void 과반수_도달_투표는_종료_성공_코드를_반환한다() throws Exception {
        when(closeVoteService.vote(7L, 42L)).thenReturn(
                new FieldVisitCloseVoteService.VoteResult(
                        FieldVisitResponseCode
                                .FIELD_VISIT_CLOSE_VOTE_AND_SESSION_END_SUCCESS,
                        new com.ssafy.ssabangpalbang.fieldvisit.dto.response
                                .FieldVisitCloseVoteResponse(
                                7L,
                                100L,
                                3,
                                2,
                                2,
                                true,
                                false,
                                true,
                                "MAJORITY_FORCED",
                                true,
                                48L,
                                "PENDING"
                        )
                )
        );

        mockMvc.perform(post("/api/v1/studies/7/field-visit/close-votes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("FIELD_VISIT_CLOSE_VOTE_AND_SESSION_END_SUCCESS"))
                .andExpect(jsonPath("$.data.sessionEnded").value(true))
                .andExpect(jsonPath("$.data.sessionEndReason").value("MAJORITY_FORCED"))
                .andExpect(jsonPath("$.data.reportTriggered").value(true));
    }

    @Test
    void 투표_자격_없으면_403이다() throws Exception {
        when(closeVoteService.vote(7L, 42L)).thenThrow(
                new BusinessException(ErrorCode.FIELD_VISIT_CLOSE_VOTE_FORBIDDEN)
        );

        mockMvc.perform(post("/api/v1/studies/7/field-visit/close-votes"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("FIELD_VISIT_CLOSE_VOTE_FORBIDDEN"));
    }
}
