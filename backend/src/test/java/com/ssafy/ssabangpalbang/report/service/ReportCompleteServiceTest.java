package com.ssafy.ssabangpalbang.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportCompleteRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportEvidenceResultRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest;
import com.ssafy.ssabangpalbang.report.integration.ReportCompletedEvent;
import com.ssafy.ssabangpalbang.report.repository.ReportEvidenceWriteRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportCompleteServiceTest {

    private static final long REPORT_ID = 48L;
    private static final Instant NOW = Instant.parse("2026-08-02T03:30:00Z");
    private static final OffsetDateTime SNAPSHOT_AT = OffsetDateTime.ofInstant(
            NOW,
            ZoneOffset.UTC
    );

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ReportInputQueryRepository reportInputQueryRepository;
    @Mock
    private ReportEvidenceWriteRepository evidenceWriteRepository;
    @Mock
    private ReportCompleteValidator validator;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();
    private final ReportProcessingTokenIssuer tokenIssuer =
            new ReportProcessingTokenIssuer();
    private ReportCompletionPayloadHasher payloadHasher;
    private ReportCompleteService service;
    private Report report;
    private ReportProcessingToken token;

    @BeforeEach
    void setUp() {
        payloadHasher = new ReportCompletionPayloadHasher(objectMapper);
        service = new ReportCompleteService(
                reportRepository,
                reportInputQueryRepository,
                evidenceWriteRepository,
                tokenIssuer,
                payloadHasher,
                validator,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                eventPublisher
        );
        token = tokenIssuer.issue();
        report = Report.create(7L, 3L, 100L);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        report.acquire(token.sha256Hash(), NOW.plusSeconds(120));
        when(reportRepository.findByIdForUpdate(REPORT_ID))
                .thenReturn(Optional.of(report));
    }

    @Test
    void 유효한_완료_요청은_결과와_근거를_한번만_저장하고_DONE으로_전환한다() {
        ReportCompleteRequest request = request("완료 결과", 1, token.rawValue());
        ReportInputQueryRepository.Snapshot snapshot = validSnapshot();
        List<ReportEvidenceWriteRepository.EvidenceRow> evidenceRows = List.of(
                new ReportEvidenceWriteRepository.EvidenceRow(
                        101L,
                        "feature.transport",
                        1
                )
        );
        when(evidenceWriteRepository.countByReportId(REPORT_ID)).thenReturn(0L);
        when(reportInputQueryRepository.loadSnapshot(REPORT_ID))
                .thenReturn(Optional.of(snapshot));
        when(validator.validate(request, snapshot)).thenReturn(evidenceRows);

        service.complete(REPORT_ID, request);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.DONE);
        assertThat(report.getProgressStage()).isEqualTo("COMPLETED");
        assertThat(report.getResultJson()).isEqualTo(
                objectMapper.valueToTree(request.generationResult())
        );
        assertThat(report.getCompletePayloadHash())
                .isEqualTo(payloadHasher.hash(request));
        assertThat(report.getCompletedAt()).isEqualTo(NOW);
        assertThat(report.getProcessingLeaseExpiresAt()).isNull();
        assertThat(report.getProcessingAttempt()).isEqualTo(1);
        assertThat(report.getProcessingTokenHash())
                .isEqualTo(token.sha256Hash());
        verify(evidenceWriteRepository).insertAll(REPORT_ID, evidenceRows);
        verify(eventPublisher).publishEvent(
                new ReportCompletedEvent(REPORT_ID, 7L)
        );
    }

    @Test
    void 근거_저장에_실패하면_Report_완료_상태를_변경하지_않는다() {
        ReportCompleteRequest request = request("완료 결과", 1, token.rawValue());
        ReportInputQueryRepository.Snapshot snapshot = validSnapshot();
        List<ReportEvidenceWriteRepository.EvidenceRow> evidenceRows = List.of(
                new ReportEvidenceWriteRepository.EvidenceRow(
                        101L,
                        "feature.transport",
                        1
                )
        );
        when(evidenceWriteRepository.countByReportId(REPORT_ID)).thenReturn(0L);
        when(reportInputQueryRepository.loadSnapshot(REPORT_ID))
                .thenReturn(Optional.of(snapshot));
        when(validator.validate(request, snapshot)).thenReturn(evidenceRows);
        doThrow(new IllegalStateException("insert failed"))
                .when(evidenceWriteRepository)
                .insertAll(REPORT_ID, evidenceRows);

        assertThatThrownBy(() -> service.complete(REPORT_ID, request))
                .isInstanceOf(IllegalStateException.class);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.IN_PROGRESS);
        assertThat(report.getResultJson()).isNull();
        assertThat(report.getCompletePayloadHash()).isNull();
        assertThat(report.getCompletedAt()).isNull();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 완료된_Report에_동일한_Token_attempt_payload를_재전송하면_멱등_성공한다() {
        ReportCompleteRequest request = request("완료 결과", 1, token.rawValue());
        completeReport(request);

        service.complete(REPORT_ID, request);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.DONE);
        verifyNoInteractions(
                reportInputQueryRepository,
                evidenceWriteRepository,
                validator
        );
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 완료된_Report에_변경된_payload를_재전송하면_충돌이다() {
        ReportCompleteRequest original = request(
                "최초 완료 결과",
                1,
                token.rawValue()
        );
        completeReport(original);

        assertError(
                request("변경된 완료 결과", 1, token.rawValue()),
                ErrorCode.COMPLETE_PAYLOAD_CONFLICT
        );
        verifyNoInteractions(
                reportInputQueryRepository,
                evidenceWriteRepository,
                validator
        );
    }

    @Test
    void processingAttempt가_다르면_만료된_처리권이다() {
        assertError(
                request("완료 결과", 2, token.rawValue()),
                ErrorCode.STALE_PROCESSING_TOKEN
        );
        verify(evidenceWriteRepository, never()).countByReportId(REPORT_ID);
    }

    @Test
    void processingToken이_다르면_만료된_처리권이다() {
        assertError(
                request("완료 결과", 1, "different-token"),
                ErrorCode.STALE_PROCESSING_TOKEN
        );
        verify(evidenceWriteRepository, never()).countByReportId(REPORT_ID);
    }

    @Test
    void Lease가_현재_시각과_같으면_만료된_처리권이다() {
        ReflectionTestUtils.setField(
                report,
                "processingLeaseExpiresAt",
                NOW
        );

        assertError(
                request("완료 결과", 1, token.rawValue()),
                ErrorCode.STALE_PROCESSING_TOKEN
        );
        verify(evidenceWriteRepository, never()).countByReportId(REPORT_ID);
    }

    private void completeReport(ReportCompleteRequest request) {
        report.complete(
                objectMapper.valueToTree(request.generationResult()),
                payloadHasher.hash(request),
                NOW.minusSeconds(1)
        );
    }

    private void assertError(
            ReportCompleteRequest request,
            ErrorCode expected
    ) {
        assertThatThrownBy(() -> service.complete(REPORT_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expected)
                );
    }

    private ReportCompleteRequest request(
            String summary,
            int attempt,
            String rawToken
    ) {
        return new ReportCompleteRequest(
                rawToken,
                attempt,
                new ReportGenerationResultRequest(
                        "테스트 리포트",
                        summary,
                        new ReportGenerationResultRequest.Metrics(
                                0,
                                0,
                                0.0,
                                0
                        ),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                new ReportEvidenceResultRequest(List.of())
        );
    }

    private ReportInputQueryRepository.Snapshot validSnapshot() {
        return new ReportInputQueryRepository.Snapshot(
                new ReportInputQueryRepository.ContextRow(
                        REPORT_ID,
                        7L,
                        100L,
                        3L,
                        FieldSessionStatus.ENDED,
                        SNAPSHOT_AT.minusHours(1),
                        SNAPSHOT_AT.minusMinutes(1),
                        SNAPSHOT_AT,
                        true
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
