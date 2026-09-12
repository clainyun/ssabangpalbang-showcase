package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecordSourceType;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.report.dto.response.ReportAcquireResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportAcquireStatus;
import com.ssafy.ssabangpalbang.report.dto.response.ReportInputResponse;
import com.ssafy.ssabangpalbang.report.service.ReportAcquireService;
import com.ssafy.ssabangpalbang.report.service.ReportCompleteService;
import com.ssafy.ssabangpalbang.report.service.ReportFailService;
import com.ssafy.ssabangpalbang.report.service.ReportInputService;
import com.ssafy.ssabangpalbang.report.service.ReportProgressService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportInternalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReportInternalControllerTest {

    private static final String URI = "/internal/v1/reports/acquire";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportAcquireService reportAcquireService;

    @MockitoBean
    private ReportProgressService reportProgressService;

    @MockitoBean
    private ReportInputService reportInputService;

    @MockitoBean
    private ReportCompleteService reportCompleteService;

    @MockitoBean
    private ReportFailService reportFailService;

    @Test
    void 처리권_획득_결과를_공통_5필드_응답으로_반환한다() throws Exception {
        when(reportAcquireService.acquire(any()))
                .thenReturn(ReportAcquireResponse.acquired(
                        48L,
                        "opaque-token",
                        1,
                        OffsetDateTime.of(
                                2026,
                                8,
                                2,
                                13,
                                0,
                                0,
                                0,
                                ZoneOffset.ofHours(9)
                        )
                ));

        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("REPORT_ACQUIRE_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("리포트 처리권을 확인했습니다."))
                .andExpect(jsonPath("$.data.status").value("ACQUIRED"))
                .andExpect(jsonPath("$.data.reportId").value(48L))
                .andExpect(jsonPath("$.data.processingToken")
                        .value("opaque-token"))
                .andExpect(jsonPath("$.data.processingAttempt").value(1))
                .andExpect(jsonPath("$.data.retryAfterSeconds").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void CONTRACT_CONFLICT도_200의_판정_결과로_반환한다() throws Exception {
        when(reportAcquireService.acquire(any()))
                .thenReturn(ReportAcquireResponse.terminal(
                        ReportAcquireStatus.CONTRACT_CONFLICT,
                        48L
                ));

        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("CONTRACT_CONFLICT"))
                .andExpect(jsonPath("$.data.reportId").value(48L));
    }

    @Test
    void ID가_1보다_작으면_400을_반환한다() throws Exception {
        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studyId": 0,
                                  "sessionId": 3,
                                  "apartmentId": 100,
                                  "occurredAt": "2026-08-02T12:30:00+09:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("studyId"));

        verifyNoInteractions(reportAcquireService);
    }

    @Test
    void offset이_없는_occurredAt은_400을_반환한다() throws Exception {
        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studyId": 7,
                                  "sessionId": 3,
                                  "apartmentId": 100,
                                  "occurredAt": "2026-08-02T12:30:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(reportAcquireService);
    }

    @Test
    void 정규화_입력_원본을_공통_5필드_응답으로_반환한다()
            throws Exception {
        when(reportInputService.getInput(48L))
                .thenReturn(sampleInputResponse());

        mockMvc.perform(get(
                        "/internal/v1/reports/{reportId}/input",
                        48L
                ))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("REPORT_INPUT_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("리포트 입력을 조회했습니다."))
                .andExpect(jsonPath("$.data.schemaVersion").value(1))
                .andExpect(jsonPath("$.data.reportId").value(48L))
                .andExpect(jsonPath("$.data.studyId").value(7L))
                .andExpect(jsonPath("$.data.apartmentId").value(15L))
                .andExpect(jsonPath("$.data.fieldSessionId").value(900L))
                .andExpect(jsonPath("$.data.sessionStatus").value("ENDED"))
                .andExpect(jsonPath("$.data.participants[0].memberId")
                        .value(7L))
                .andExpect(jsonPath("$.data.checklistItems[0].completed")
                        .value(true))
                .andExpect(jsonPath("$.data.authoritativeSourceIds[0]")
                        .value(201L))
                .andExpect(jsonPath("$.data.fieldRecords[0].sourceType")
                        .value("TEXT"))
                .andExpect(jsonPath("$.data.incompleteSttJobs[0].status")
                        .value("PENDING"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(reportInputService).getInput(48L);
    }

    @Test
    void 정규화_입력의_reportId가_1보다_작으면_400을_반환한다()
            throws Exception {
        mockMvc.perform(get(
                        "/internal/v1/reports/{reportId}/input",
                        0L
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(reportInputService);
    }

    @Test
    void 정규화_입력의_Report가_없으면_404를_반환한다()
            throws Exception {
        when(reportInputService.getInput(999L))
                .thenThrow(new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        mockMvc.perform(get(
                        "/internal/v1/reports/{reportId}/input",
                        999L
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 진행_단계_저장_성공은_정확한_빈_204를_반환한다() throws Exception {
        mockMvc.perform(patch("/internal/v1/reports/{reportId}/progress", 48L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProgressRequest()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));

        verify(reportProgressService).updateProgress(any(), any());
    }

    @Test
    void COMPLETED_단계는_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/internal/v1/reports/{reportId}/progress", 48L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processingToken": "opaque-token",
                                  "processingAttempt": 1,
                                  "stage": "COMPLETED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(reportProgressService);
    }

    @Test
    void 빈_Token과_0_Attempt는_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/internal/v1/reports/{reportId}/progress", 48L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processingToken": " ",
                                  "processingAttempt": 0,
                                  "stage": "NORMALIZATION"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(reportProgressService);
    }

    @Test
    void reportId가_1보다_작으면_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/internal/v1/reports/{reportId}/progress", 0L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProgressRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(reportProgressService);
    }

    @Test
    void 처리권_충돌은_409_공통_응답을_반환한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.STALE_PROCESSING_TOKEN))
                .when(reportProgressService)
                .updateProgress(any(), any());

        mockMvc.perform(patch("/internal/v1/reports/{reportId}/progress", 48L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProgressRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("STALE_PROCESSING_TOKEN"));
    }

    @Test
    void 완료_저장_성공은_정확한_빈_204를_반환한다() throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/complete",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCompleteRequest()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));

        verify(reportCompleteService).complete(any(), any());
    }

    @Test
    void 완료_요청의_중첩_집계값이_유효하지_않으면_400을_반환한다()
            throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/complete",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCompleteRequest().replace(
                                "\"totalChecklistItemCount\": 0",
                                "\"totalChecklistItemCount\": -1"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field")
                        .value("generationResult.metrics"
                                + ".totalChecklistItemCount"));

        verifyNoInteractions(reportCompleteService);
    }

    @Test
    void 완료_요청의_중첩_unknown_field는_400을_반환한다()
            throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/complete",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCompleteRequest().replace(
                                "\"fieldRecordCount\": 0",
                                "\"fieldRecordCount\": 0, "
                                        + "\"unexpectedMetric\": 1"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(reportCompleteService);
    }

    @Test
    void 완료_payload_충돌은_409_공통_응답을_반환한다()
            throws Exception {
        doThrow(new BusinessException(ErrorCode.COMPLETE_PAYLOAD_CONFLICT))
                .when(reportCompleteService)
                .complete(any(), any());

        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/complete",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCompleteRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMPLETE_PAYLOAD_CONFLICT"));
    }

    @Test
    void 실패_저장_성공은_정확한_빈_204를_반환한다() throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));

        verify(reportFailService).fail(any(), any());
    }

    @Test
    void 실패_요청의_reportId가_1보다_작으면_400을_반환한다()
            throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        0L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(reportFailService);
    }

    @Test
    void 실패_요청의_빈_Token은_400을_반환한다() throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest().replace(
                                "\"processingToken\": \"opaque-token\"",
                                "\"processingToken\": \" \""
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(reportFailService);
    }

    @Test
    void 실패_요청의_0_Attempt는_400을_반환한다() throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest().replace(
                                "\"processingAttempt\": 1",
                                "\"processingAttempt\": 0"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(reportFailService);
    }

    @Test
    void 실패_요청의_종료_단계는_400을_반환한다() throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest().replace(
                                "\"failedStage\": \"NORMALIZATION\"",
                                "\"failedStage\": \"COMPLETED\""
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(reportFailService);
    }

    @Test
    void 실패_요청의_허용되지_않은_errorCode는_400을_반환한다()
            throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest().replace(
                                "\"errorCode\": \"NORMALIZATION_FAILED\"",
                                "\"errorCode\": \"RAW_PROVIDER_ERROR\""
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(reportFailService);
    }

    @Test
    void 실패_요청의_500자_초과_message는_400을_반환한다()
            throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest().replace(
                                "리포트 입력 정규화에 실패했습니다.",
                                "가".repeat(501)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(reportFailService);
    }

    @Test
    void 실패_요청의_retryable이_누락되면_400을_반환한다()
            throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest().replace(
                                "\"retryable\": false",
                                "\"retryable\": null"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(reportFailService);
    }

    @Test
    void 실패_요청의_unknown_field는_400을_반환한다()
            throws Exception {
        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest().replace(
                                "\"retryable\": false",
                                "\"retryable\": false, "
                                        + "\"unexpected\": true"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verifyNoInteractions(reportFailService);
    }

    @Test
    void errorCode와_다른_안전하지_않은_message는_400을_반환한다()
            throws Exception {
        doThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE))
                .when(reportFailService)
                .fail(any(), any());

        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest().replace(
                                "리포트 입력 정규화에 실패했습니다.",
                                "외부 제공자의 원문 오류입니다."
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void 실패_저장_Report가_없으면_404를_반환한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.REPORT_NOT_FOUND))
                .when(reportFailService)
                .fail(any(), any());

        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        999L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    void 실패_저장의_처리권_충돌은_409를_반환한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.STALE_PROCESSING_TOKEN))
                .when(reportFailService)
                .fail(any(), any());

        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("STALE_PROCESSING_TOKEN"));
    }

    @Test
    void 실패_payload_충돌은_409를_반환한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.FAIL_PAYLOAD_CONFLICT))
                .when(reportFailService)
                .fail(any(), any());

        mockMvc.perform(put(
                        "/internal/v1/reports/{reportId}/fail",
                        48L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFailRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("FAIL_PAYLOAD_CONFLICT"));
    }

    private String validRequest() {
        return """
                {
                  "studyId": 7,
                  "sessionId": 3,
                  "apartmentId": 100,
                  "occurredAt": "2026-08-02T12:30:00+09:00"
                }
                """;
    }

    private String validProgressRequest() {
        return """
                {
                  "processingToken": "opaque-token",
                  "processingAttempt": 1,
                  "stage": "NORMALIZATION"
                }
                """;
    }

    private String validCompleteRequest() {
        return """
                {
                  "processingToken": "opaque-token",
                  "processingAttempt": 1,
                  "generationResult": {
                    "title": "반포 자이 임장 리포트",
                    "summary": "참여자 의견을 종합한 요약입니다.",
                    "metrics": {
                      "totalChecklistItemCount": 0,
                      "completedChecklistItemCount": 0,
                      "averageCompletionRate": 0.0,
                      "fieldRecordCount": 0
                    },
                    "topPositiveFeatures": [],
                    "topCautionFeatures": [],
                    "commonOpinions": [],
                    "conflictingOpinions": [],
                    "categories": []
                  },
                  "evidenceResult": {
                    "claims": []
                  }
                }
                """;
    }

    private String validFailRequest() {
        return """
                {
                  "processingToken": "opaque-token",
                  "processingAttempt": 1,
                  "failedStage": "NORMALIZATION",
                  "errorCode": "NORMALIZATION_FAILED",
                  "message": "리포트 입력 정규화에 실패했습니다.",
                  "retryable": false
                }
                """;
    }

    private ReportInputResponse sampleInputResponse() {
        OffsetDateTime startedAt = dateTime(14, 0);
        OffsetDateTime endedAt = dateTime(16, 0);
        return new ReportInputResponse(
                ReportInputResponse.SCHEMA_VERSION,
                48L,
                7L,
                15L,
                900L,
                FieldSessionStatus.ENDED,
                startedAt,
                endedAt,
                dateTime(16, 5),
                List.of(new ReportInputResponse.Participant(
                        71L,
                        7L,
                        FieldParticipantStatus.ENDED,
                        startedAt,
                        endedAt
                )),
                List.of(new ReportInputResponse.ChecklistItem(
                        55L,
                        501L,
                        7L,
                        false,
                        "교통",
                        "지하철역 접근성",
                        null,
                        1,
                        true,
                        dateTime(14, 35)
                )),
                List.of(201L),
                List.of(new ReportInputResponse.FieldRecord(
                        201L,
                        900L,
                        501L,
                        7L,
                        FieldRecordSourceType.TEXT,
                        "지하철역까지 약 8분입니다.",
                        null,
                        null,
                        null,
                        dateTime(14, 30),
                        dateTime(14, 31)
                )),
                List.of(new ReportInputResponse.IncompleteSttJob(
                        "stt-pending-1",
                        7L,
                        501L,
                        SttStatus.PENDING,
                        true,
                        null,
                        dateTime(15, 20)
                ))
        );
    }

    private OffsetDateTime dateTime(int hour, int minute) {
        return OffsetDateTime.of(
                2026,
                7,
                20,
                hour,
                minute,
                0,
                0,
                ZoneOffset.ofHours(9)
        );
    }
}
