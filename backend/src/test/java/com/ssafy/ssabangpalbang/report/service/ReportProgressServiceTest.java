package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.report.config.ReportInternalProperties;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportProgressRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportWorkerProgressStage;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportProgressServiceTest {

    private static final Long REPORT_ID = 48L;
    private static final Instant NOW = Instant.parse("2026-08-02T03:30:00Z");
    private static final Duration LEASE_DURATION = Duration.ofMinutes(30);

    @Mock
    private ReportRepository reportRepository;

    private ReportProcessingTokenIssuer tokenIssuer;
    private ReportProgressService service;
    private Report report;
    private ReportProcessingToken token;

    @BeforeEach
    void setUp() {
        tokenIssuer = new ReportProcessingTokenIssuer();
        service = new ReportProgressService(
                reportRepository,
                tokenIssuer,
                new ReportInternalProperties("internal", LEASE_DURATION),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        token = tokenIssuer.issue();
        report = Report.create(7L, 3L, 100L);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        report.acquire(token.sha256Hash(), NOW.plusSeconds(120));
        when(reportRepository.findByIdForUpdate(REPORT_ID))
                .thenReturn(Optional.of(report));
    }

    @Test
    void 유효한_처리권이면_단계를_저장하고_Lease를_전체_TTL로_갱신한다() {
        service.updateProgress(
                REPORT_ID,
                request(1, ReportWorkerProgressStage.STT_VALIDATION)
        );

        assertThat(report.getProgressStage()).isEqualTo("STT_VALIDATION");
        assertThat(report.getProcessingLeaseExpiresAt())
                .isEqualTo(NOW.plus(LEASE_DURATION));
        assertThat(report.getProcessingAttempt()).isEqualTo(1);
        assertThat(report.getProcessingTokenHash())
                .isEqualTo(token.sha256Hash());
        assertThat(report.getStatus()).isEqualTo(ReportStatus.IN_PROGRESS);
    }

    @Test
    void 동일_단계_재전송도_허용하고_Lease를_갱신한다() {
        service.updateProgress(
                REPORT_ID,
                request(1, ReportWorkerProgressStage.RECORD_COLLECTION)
        );

        assertThat(report.getProgressStage()).isEqualTo("RECORD_COLLECTION");
        assertThat(report.getProcessingLeaseExpiresAt())
                .isEqualTo(NOW.plus(LEASE_DURATION));
    }

    @Test
    void EVIDENCE_MAPPING을_건너뛴_RESULT_SAVING도_저장한다() {
        service.updateProgress(
                REPORT_ID,
                request(1, ReportWorkerProgressStage.STT_VALIDATION)
        );
        service.updateProgress(
                REPORT_ID,
                request(1, ReportWorkerProgressStage.NORMALIZATION)
        );
        service.updateProgress(
                REPORT_ID,
                request(1, ReportWorkerProgressStage.REPORT_GENERATION)
        );

        service.updateProgress(
                REPORT_ID,
                request(1, ReportWorkerProgressStage.RESULT_SAVING)
        );

        assertThat(report.getProgressStage()).isEqualTo("RESULT_SAVING");
    }

    @Test
    void 진행_단계_역행은_처리권_충돌이다() {
        service.updateProgress(
                REPORT_ID,
                request(1, ReportWorkerProgressStage.STT_VALIDATION)
        );

        assertStale(request(1, ReportWorkerProgressStage.RECORD_COLLECTION));
    }

    @Test
    void 허용되지_않은_단계_건너뛰기는_처리권_충돌이다() {
        assertStale(request(1, ReportWorkerProgressStage.REPORT_GENERATION));
    }

    @Test
    void Report가_없으면_404_도메인_오류를_반환한다() {
        when(reportRepository.findByIdForUpdate(REPORT_ID))
                .thenReturn(Optional.empty());

        assertError(
                request(1, ReportWorkerProgressStage.NORMALIZATION),
                ErrorCode.REPORT_NOT_FOUND
        );
    }

    @ParameterizedTest
    @EnumSource(value = ReportStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
    void IN_PROGRESS가_아닌_Report는_처리권_충돌이다(ReportStatus status) {
        ReflectionTestUtils.setField(report, "status", status);

        assertStale(request(1, ReportWorkerProgressStage.NORMALIZATION));
    }

    @Test
    void processingAttempt가_다르면_처리권_충돌이다() {
        assertStale(request(2, ReportWorkerProgressStage.NORMALIZATION));
    }

    @Test
    void processingToken이_다르면_처리권_충돌이다() {
        ReportProgressRequest request = new ReportProgressRequest(
                "different-token",
                1,
                ReportWorkerProgressStage.NORMALIZATION
        );

        assertStale(request);
    }

    @Test
    void Lease가_현재_시각과_같으면_만료된_처리권이다() {
        ReflectionTestUtils.setField(
                report,
                "processingLeaseExpiresAt",
                NOW
        );

        assertStale(request(1, ReportWorkerProgressStage.NORMALIZATION));
    }

    private ReportProgressRequest request(
            int attempt,
            ReportWorkerProgressStage stage
    ) {
        return new ReportProgressRequest(token.rawValue(), attempt, stage);
    }

    private void assertStale(ReportProgressRequest request) {
        assertError(request, ErrorCode.STALE_PROCESSING_TOKEN);
    }

    private void assertError(
            ReportProgressRequest request,
            ErrorCode expected
    ) {
        assertThatThrownBy(() -> service.updateProgress(REPORT_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expected)
                );
    }
}
