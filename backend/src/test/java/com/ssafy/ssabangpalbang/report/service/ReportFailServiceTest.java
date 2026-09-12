package com.ssafy.ssabangpalbang.report.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportFailRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportWorkerErrorCode;
import com.ssafy.ssabangpalbang.report.dto.request.ReportWorkerProgressStage;
import com.ssafy.ssabangpalbang.report.repository.ReportEvidenceWriteRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportFailServiceTest {

    private static final long REPORT_ID = 48L;
    private static final Instant NOW = Instant.parse("2026-08-02T03:30:00Z");

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ReportEvidenceWriteRepository evidenceWriteRepository;

    private final ReportProcessingTokenIssuer tokenIssuer =
            new ReportProcessingTokenIssuer();
    private final ReportFailurePayloadHasher payloadHasher =
            new ReportFailurePayloadHasher();
    private ReportFailService service;
    private ReportProcessingToken token;

    @BeforeEach
    void setUp() {
        service = new ReportFailService(
                reportRepository,
                evidenceWriteRepository,
                tokenIssuer,
                payloadHasher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        token = tokenIssuer.issue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 유효한_실패_요청은_retryable_값과_안전한_실패_정보를_저장한다(
            boolean retryable
    ) {
        Report report = activeReport();
        ReportFailRequest request = request(
                token.rawValue(),
                1,
                ReportWorkerProgressStage.NORMALIZATION,
                ReportWorkerErrorCode.NORMALIZATION_FAILED,
                retryable
        );
        stubReport(report);
        when(evidenceWriteRepository.countByReportId(REPORT_ID)).thenReturn(0L);

        service.fail(REPORT_ID, request);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(report.getProgressStage()).isEqualTo("NORMALIZATION");
        assertThat(report.getFailCode())
                .isEqualTo("NORMALIZATION_FAILED");
        assertThat(report.getFailReason()).isEqualTo(
                ReportWorkerErrorCode.NORMALIZATION_FAILED.safeMessage()
        );
        assertThat(report.isRetryable()).isEqualTo(retryable);
        assertThat(report.getFailPayloadHash())
                .isEqualTo(payloadHasher.hash(request))
                .hasSize(64);
        assertThat(report.getFailedAt()).isEqualTo(NOW);
        assertThat(report.getCompletedAt()).isNull();
        assertThat(report.getProcessingLeaseExpiresAt()).isNull();
        assertThat(report.getProcessingAttempt()).isEqualTo(1);
        assertThat(report.getProcessingTokenHash())
                .isEqualTo(token.sha256Hash());
    }

    @Test
    void 동일한_실패_명령을_재전송하면_멱등_성공한다() {
        Report report = activeReport();
        ReportFailRequest request = request(false);
        stubReport(report);
        when(evidenceWriteRepository.countByReportId(REPORT_ID)).thenReturn(0L);

        service.fail(REPORT_ID, request);
        Instant failedAt = report.getFailedAt();
        service.fail(REPORT_ID, request);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(report.getFailedAt()).isEqualTo(failedAt);
        verify(evidenceWriteRepository, times(1)).countByReportId(REPORT_ID);
    }

    @Test
    void FAILED_Report에_다른_Token을_전송하면_payload_충돌이다() {
        ReportFailRequest original = request(false);
        Report report = failedReport(original);
        stubReport(report);

        assertError(
                request(
                        "different-token",
                        1,
                        ReportWorkerProgressStage.NORMALIZATION,
                        ReportWorkerErrorCode.NORMALIZATION_FAILED,
                        false
                ),
                ErrorCode.FAIL_PAYLOAD_CONFLICT
        );
        verifyNoInteractions(evidenceWriteRepository);
    }

    @Test
    void FAILED_Report에_다른_attempt를_전송하면_payload_충돌이다() {
        ReportFailRequest original = request(false);
        Report report = failedReport(original);
        stubReport(report);

        assertError(
                request(
                        token.rawValue(),
                        2,
                        ReportWorkerProgressStage.NORMALIZATION,
                        ReportWorkerErrorCode.NORMALIZATION_FAILED,
                        false
                ),
                ErrorCode.FAIL_PAYLOAD_CONFLICT
        );
        verifyNoInteractions(evidenceWriteRepository);
    }

    @Test
    void FAILED_Report에_다른_payload를_전송하면_payload_충돌이다() {
        ReportFailRequest original = request(false);
        Report report = failedReport(original);
        stubReport(report);

        assertError(request(true), ErrorCode.FAIL_PAYLOAD_CONFLICT);
        verifyNoInteractions(evidenceWriteRepository);
    }

    @Test
    void PENDING_Report는_만료된_처리권이다() {
        Report report = report();
        stubReport(report);

        assertStale(request(false));
    }

    @Test
    void DONE_Report는_만료된_처리권이다() {
        Report report = activeReport();
        report.complete(
                JsonNodeFactory.instance.objectNode().put("title", "완료"),
                "a".repeat(64),
                NOW.minusSeconds(1)
        );
        stubReport(report);

        assertStale(request(false));
    }

    @Test
    void Lease가_null이면_만료된_처리권이다() {
        Report report = activeReport();
        ReflectionTestUtils.setField(
                report,
                "processingLeaseExpiresAt",
                null
        );
        stubReport(report);

        assertStale(request(false));
    }

    @Test
    void Lease가_현재_시각보다_이전이면_만료된_처리권이다() {
        Report report = activeReport();
        ReflectionTestUtils.setField(
                report,
                "processingLeaseExpiresAt",
                NOW.minusNanos(1)
        );
        stubReport(report);

        assertStale(request(false));
    }

    @Test
    void Lease가_현재_시각과_같으면_만료된_처리권이다() {
        Report report = activeReport();
        ReflectionTestUtils.setField(
                report,
                "processingLeaseExpiresAt",
                NOW
        );
        stubReport(report);

        assertStale(request(false));
    }

    @Test
    void IN_PROGRESS에서_Token이_다르면_만료된_처리권이다() {
        Report report = activeReport();
        stubReport(report);

        assertStale(request(
                "different-token",
                1,
                ReportWorkerProgressStage.NORMALIZATION,
                ReportWorkerErrorCode.NORMALIZATION_FAILED,
                false
        ));
    }

    @Test
    void IN_PROGRESS에서_attempt가_다르면_만료된_처리권이다() {
        Report report = activeReport();
        stubReport(report);

        assertStale(request(
                token.rawValue(),
                2,
                ReportWorkerProgressStage.NORMALIZATION,
                ReportWorkerErrorCode.NORMALIZATION_FAILED,
                false
        ));
    }

    @Test
    void Report가_없으면_REPORT_NOT_FOUND를_반환한다() {
        when(reportRepository.findByIdForUpdate(REPORT_ID))
                .thenReturn(Optional.empty());

        assertError(request(false), ErrorCode.REPORT_NOT_FOUND);
        verifyNoInteractions(evidenceWriteRepository);
    }

    @Test
    void errorCode와_일치하지_않는_message는_입력값_오류다() {
        ReportFailRequest unsafe = new ReportFailRequest(
                token.rawValue(),
                1,
                ReportWorkerProgressStage.NORMALIZATION,
                ReportWorkerErrorCode.NORMALIZATION_FAILED,
                "java.sql.SQLException: password=secret",
                false
        );

        assertError(unsafe, ErrorCode.INVALID_INPUT_VALUE);
        verifyNoInteractions(reportRepository, evidenceWriteRepository);
    }

    @Test
    void 선행_result가_있으면_payload_충돌이다() {
        Report report = activeReport();
        ReflectionTestUtils.setField(
                report,
                "resultJson",
                JsonNodeFactory.instance.objectNode().put("title", "선행 결과")
        );
        stubReport(report);

        assertError(request(false), ErrorCode.FAIL_PAYLOAD_CONFLICT);
        verify(evidenceWriteRepository, never()).countByReportId(REPORT_ID);
    }

    @Test
    void 선행_complete_payload_hash가_있으면_payload_충돌이다() {
        Report report = activeReport();
        ReflectionTestUtils.setField(
                report,
                "completePayloadHash",
                "a".repeat(64)
        );
        stubReport(report);

        assertError(request(false), ErrorCode.FAIL_PAYLOAD_CONFLICT);
        verify(evidenceWriteRepository, never()).countByReportId(REPORT_ID);
    }

    @Test
    void 선행_근거가_있으면_payload_충돌이다() {
        Report report = activeReport();
        stubReport(report);
        when(evidenceWriteRepository.countByReportId(REPORT_ID)).thenReturn(1L);

        assertError(request(false), ErrorCode.FAIL_PAYLOAD_CONFLICT);
    }

    private Report failedReport(ReportFailRequest request) {
        Report report = activeReport();
        report.fail(
                request.failedStage().toDomain(),
                request.errorCode().name(),
                request.errorCode().safeMessage(),
                request.retryable(),
                payloadHasher.hash(request),
                NOW.minusSeconds(1)
        );
        return report;
    }

    private Report activeReport() {
        Report report = report();
        report.acquire(token.sha256Hash(), NOW.plusSeconds(120));
        return report;
    }

    private Report report() {
        Report report = Report.create(7L, 3L, 100L);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        return report;
    }

    private void stubReport(Report report) {
        when(reportRepository.findByIdForUpdate(REPORT_ID))
                .thenReturn(Optional.of(report));
    }

    private ReportFailRequest request(boolean retryable) {
        return request(
                token.rawValue(),
                1,
                ReportWorkerProgressStage.NORMALIZATION,
                ReportWorkerErrorCode.NORMALIZATION_FAILED,
                retryable
        );
    }

    private ReportFailRequest request(
            String rawToken,
            int attempt,
            ReportWorkerProgressStage stage,
            ReportWorkerErrorCode errorCode,
            boolean retryable
    ) {
        return new ReportFailRequest(
                rawToken,
                attempt,
                stage,
                errorCode,
                errorCode.safeMessage(),
                retryable
        );
    }

    private void assertStale(ReportFailRequest request) {
        assertError(request, ErrorCode.STALE_PROCESSING_TOKEN);
        verify(evidenceWriteRepository, never()).countByReportId(REPORT_ID);
    }

    private void assertError(
            ReportFailRequest request,
            ErrorCode expected
    ) {
        assertThatThrownBy(() -> service.fail(REPORT_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expected)
                );
    }
}
