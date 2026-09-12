package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.report.config.ReportInternalProperties;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportAcquireRequest;
import com.ssafy.ssabangpalbang.report.dto.response.ReportAcquireResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportAcquireStatus;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportAcquireServiceTest {

    private static final long STUDY_ID = 7L;
    private static final long SESSION_ID = 3L;
    private static final long APARTMENT_ID = 100L;
    private static final long REPORT_ID = 48L;
    private static final Instant NOW = Instant.parse("2026-08-02T03:30:00Z");
    private static final String RAW_TOKEN = "opaque-token";
    private static final String TOKEN_HASH = "a".repeat(64);

    @Mock
    private FieldSessionRepository fieldSessionRepository;
    @Mock
    private StudyRepository studyRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ReportProcessingTokenIssuer tokenIssuer;

    private ReportAcquireService service;

    @BeforeEach
    void setUp() {
        service = new ReportAcquireService(
                fieldSessionRepository,
                studyRepository,
                reportRepository,
                tokenIssuer,
                new ReportInternalProperties("internal", Duration.ofMinutes(30)),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void 신규_스터디는_Report를_생성하고_처리권을_발급한다() {
        stubValidSource();
        when(reportRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.empty());
        when(tokenIssuer.issue())
                .thenReturn(new ReportProcessingToken(RAW_TOKEN, TOKEN_HASH));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenAnswer(invocation -> withId(
                        invocation.getArgument(0),
                        REPORT_ID
                ));

        ReportAcquireResponse response = service.acquire(request());

        assertThat(response.status()).isEqualTo(ReportAcquireStatus.ACQUIRED);
        assertThat(response.reportId()).isEqualTo(REPORT_ID);
        assertThat(response.processingToken()).isEqualTo(RAW_TOKEN);
        assertThat(response.processingAttempt()).isEqualTo(1);
        assertThat(response.leaseExpiresAt().toInstant())
                .isEqualTo(NOW.plus(Duration.ofMinutes(30)));

        Report saved = captureSavedReport();
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.IN_PROGRESS);
        assertThat(saved.getProgressStage()).isEqualTo("RECORD_COLLECTION");
        assertThat(saved.getProcessingTokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(saved.getFieldSessionId()).isEqualTo(SESSION_ID);
    }

    @Test
    void 유효한_Lease가_있으면_새_Token을_발급하지_않는다() {
        stubValidSource();
        Report report = report(ReportStatus.IN_PROGRESS, 2);
        ReflectionTestUtils.setField(
                report,
                "processingLeaseExpiresAt",
                NOW.plusSeconds(120)
        );
        when(reportRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(report));

        ReportAcquireResponse response = service.acquire(request());

        assertThat(response.status())
                .isEqualTo(ReportAcquireStatus.ALREADY_PROCESSING);
        assertThat(response.reportId()).isEqualTo(REPORT_ID);
        assertThat(response.retryAfterSeconds()).isEqualTo(120L);
        assertThat(response.processingToken()).isNull();
        verify(tokenIssuer, never()).issue();
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void 만료된_Lease는_attempt를_증가시켜_재선점한다() {
        stubValidSource();
        Report report = report(ReportStatus.IN_PROGRESS, 2);
        ReflectionTestUtils.setField(
                report,
                "processingLeaseExpiresAt",
                NOW.minusSeconds(1)
        );
        when(reportRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(report));
        when(tokenIssuer.issue())
                .thenReturn(new ReportProcessingToken(RAW_TOKEN, TOKEN_HASH));
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        ReportAcquireResponse response = service.acquire(request());

        assertThat(response.status()).isEqualTo(ReportAcquireStatus.ACQUIRED);
        assertThat(response.processingAttempt()).isEqualTo(3);
        assertThat(report.getProcessingTokenHash()).isEqualTo(TOKEN_HASH);
    }

    @Test
    void 완료된_Report는_ALREADY_COMPLETED를_반환한다() {
        stubValidSource();
        Report report = report(ReportStatus.DONE, 1);
        when(reportRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(report));

        ReportAcquireResponse response = service.acquire(request());

        assertThat(response.status())
                .isEqualTo(ReportAcquireStatus.ALREADY_COMPLETED);
        assertThat(response.reportId()).isEqualTo(REPORT_ID);
        verify(tokenIssuer, never()).issue();
    }

    @Test
    void 실패한_Report는_자동_재시작하지_않는다() {
        stubValidSource();
        Report report = report(ReportStatus.FAILED, 1);
        when(reportRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(report));

        ReportAcquireResponse response = service.acquire(request());

        assertThat(response.status())
                .isEqualTo(ReportAcquireStatus.ALREADY_FAILED);
        verify(tokenIssuer, never()).issue();
    }

    @Test
    void 세션이나_아파트_관계가_다르면_CONTRACT_CONFLICT를_반환한다() {
        FieldSession session = endedSession();
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));
        Report report = report(ReportStatus.PENDING, 0);
        when(reportRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(report));
        Study study = study(999L, StudyStatus.IN_PROGRESS);
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));

        ReportAcquireResponse response = service.acquire(request());

        assertThat(response.status())
                .isEqualTo(ReportAcquireStatus.CONTRACT_CONFLICT);
        assertThat(response.reportId()).isEqualTo(REPORT_ID);
        verify(tokenIssuer, never()).issue();
    }

    @Test
    void 종료되지_않은_세션은_CONTRACT_CONFLICT를_반환한다() {
        FieldSession session = FieldSession.start(STUDY_ID, NOW.minusSeconds(60));
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));
        when(reportRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.empty());

        ReportAcquireResponse response = service.acquire(request());

        assertThat(response.status())
                .isEqualTo(ReportAcquireStatus.CONTRACT_CONFLICT);
        assertThat(response.reportId()).isNull();
        verify(studyRepository, never())
                .findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void 처리권_응답_toString은_Token_원문을_노출하지_않는다() {
        ReportAcquireResponse response = ReportAcquireResponse.acquired(
                REPORT_ID,
                RAW_TOKEN,
                1,
                OffsetDateTime.ofInstant(
                        NOW.plus(Duration.ofMinutes(30)),
                        ZoneOffset.ofHours(9)
                )
        );

        assertThat(response.toString())
                .doesNotContain(RAW_TOKEN)
                .contains("processingToken=[REDACTED]");
    }

    private void stubValidSource() {
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(endedSession()));
        Study sourceStudy = study(
                APARTMENT_ID,
                StudyStatus.IN_PROGRESS
        );
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(sourceStudy));
    }

    private FieldSession endedSession() {
        FieldSession session = FieldSession.start(STUDY_ID, NOW.minusSeconds(600));
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        session.endByAllParticipants(NOW.minusSeconds(10));
        return session;
    }

    private Study study(long apartmentId, StudyStatus status) {
        Study study = org.mockito.Mockito.mock(Study.class);
        when(study.getApartmentId()).thenReturn(apartmentId);
        when(study.getStatus()).thenReturn(status);
        return study;
    }

    private Report report(ReportStatus status, int attempt) {
        Report report = Report.create(STUDY_ID, SESSION_ID, APARTMENT_ID);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        ReflectionTestUtils.setField(report, "status", status);
        ReflectionTestUtils.setField(report, "processingAttempt", attempt);
        return report;
    }

    private ReportAcquireRequest request() {
        return new ReportAcquireRequest(
                STUDY_ID,
                SESSION_ID,
                APARTMENT_ID,
                OffsetDateTime.ofInstant(NOW, ZoneOffset.ofHours(9))
        );
    }

    private Report withId(Report report, Long id) {
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }

    private Report captureSavedReport() {
        org.mockito.ArgumentCaptor<Report> captor =
                org.mockito.ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }
}
