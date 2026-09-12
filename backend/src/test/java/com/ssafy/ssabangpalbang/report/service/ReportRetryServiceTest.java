package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecordSourceType;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.integration.ReportRequestedEvent;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.domain.ReportProgressStage;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.response.ReportResponseCode;
import com.ssafy.ssabangpalbang.report.repository.ReportEvidenceWriteRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyPurpose;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportRetryServiceTest {

    private static final long MEMBER_ID = 7L;
    private static final long REPORT_ID = 48L;
    private static final long STUDY_ID = 3L;
    private static final long SESSION_ID = 9L;
    private static final long APARTMENT_ID = 100L;
    private static final Instant NOW =
            Instant.parse("2026-07-25T06:30:00Z");
    private static final OffsetDateTime OFFSET_NOW =
            OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private StudyRepository studyRepository;
    @Mock
    private ApartmentRepository apartmentRepository;
    @Mock
    private ChecklistRepository checklistRepository;
    @Mock
    private ReportInputQueryRepository reportInputQueryRepository;
    @Mock
    private ReportEvidenceWriteRepository evidenceWriteRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ReportRetryService service;

    @BeforeEach
    void setUp() {
        service = new ReportRetryService(
                reportRepository,
                memberRepository,
                studyRepository,
                apartmentRepository,
                checklistRepository,
                reportInputQueryRepository,
                evidenceWriteRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                eventPublisher
        );
    }

    @Test
    void 스터디장의_재생성_요청은_FAILED를_PENDING으로_초기화하고_이벤트를_한_번_발행한다() {
        Report report = failedReport(true);
        stubBase(report, leaderStudy());
        stubValidSource();

        ReportRetryService.RetryResult result = service.retry(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(result.httpStatus().value()).isEqualTo(202);
        assertThat(result.responseCode())
                .isEqualTo(ReportResponseCode.REPORT_RETRY_ACCEPTED);
        assertThat(result.response().reportId()).isEqualTo(REPORT_ID);
        assertThat(result.response().status()).isEqualTo(ReportStatus.PENDING);
        assertThat(result.response().progressRate()).isZero();
        assertThat(result.response().progressStage())
                .isEqualTo("RECORD_COLLECTION");
        assertThat(result.response().retryRequestedAt())
                .isEqualTo(OffsetDateTime.parse(
                        "2026-07-25T15:30:00+09:00"
                ));
        assertThat(result.response().statusApi())
                .isEqualTo("/api/v1/reports/48/status");

        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(report.getProgressStage())
                .isEqualTo("RECORD_COLLECTION");
        assertThat(report.getFailCode()).isNull();
        assertThat(report.getFailReason()).isNull();
        assertThat(report.isRetryable()).isFalse();
        assertThat(report.getProcessingAttempt()).isEqualTo(1);
        assertThat(report.getProcessingTokenHash()).isNull();
        assertThat(report.getProcessingLeaseExpiresAt()).isNull();
        assertThat(report.getFailPayloadHash()).isNull();
        assertThat(report.getFailedAt()).isNull();
        assertThat(report.getRetryRequestedAt()).isEqualTo(NOW);

        verify(evidenceWriteRepository).deleteByReportId(REPORT_ID);
        verify(eventPublisher).publishEvent(new ReportRequestedEvent(
                STUDY_ID,
                SESSION_ID,
                APARTMENT_ID,
                NOW
        ));
    }

    @Test
    void 같은_재생성_요청을_반복하면_200을_반환하고_이벤트와_삭제는_중복하지_않는다() {
        Report report = failedReport(true);
        stubBase(report, leaderStudy());
        stubValidSource();

        ReportRetryService.RetryResult first = service.retry(
                MEMBER_ID,
                REPORT_ID
        );
        ReportRetryService.RetryResult duplicate = service.retry(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(first.httpStatus().value()).isEqualTo(202);
        assertThat(duplicate.httpStatus().value()).isEqualTo(200);
        assertThat(duplicate.responseCode()).isEqualTo(
                ReportResponseCode.REPORT_RETRY_ALREADY_IN_PROGRESS
        );
        assertThat(duplicate.response().retryRequestedAt())
                .isEqualTo(first.response().retryRequestedAt());
        verify(evidenceWriteRepository, times(1))
                .deleteByReportId(REPORT_ID);
        verify(eventPublisher, times(1)).publishEvent(
                new ReportRequestedEvent(
                        STUDY_ID,
                        SESSION_ID,
                        APARTMENT_ID,
                        NOW
                )
        );
    }

    @Test
    void 스터디장이_아니면_403_예외이고_상태와_이벤트를_변경하지_않는다() {
        Report report = failedReport(true);
        stubBase(report, studyWithLeader(99L));

        assertError(ErrorCode.REPORT_RETRY_ACCESS_DENIED);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
        verifyNoInteractions(
                apartmentRepository,
                checklistRepository,
                reportInputQueryRepository,
                evidenceWriteRepository,
                eventPublisher
        );
    }

    @Test
    void FAILED가_아닌_리포트는_재생성을_허용하지_않는다() {
        Report report = report();
        stubBase(report, leaderStudy());

        assertError(ErrorCode.REPORT_RETRY_NOT_ALLOWED);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);
        verifyNoInteractions(
                apartmentRepository,
                checklistRepository,
                reportInputQueryRepository,
                evidenceWriteRepository,
                eventPublisher
        );
    }

    @Test
    void retryable이_false인_FAILED_리포트는_재생성을_허용하지_않는다() {
        Report report = failedReport(false);
        stubBase(report, leaderStudy());

        assertError(ErrorCode.REPORT_RETRY_NOT_RETRYABLE);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
        verifyNoInteractions(
                apartmentRepository,
                checklistRepository,
                reportInputQueryRepository,
                evidenceWriteRepository,
                eventPublisher
        );
    }

    @Test
    void 원본_기록이_없으면_409이고_이벤트를_발행하지_않는다() {
        Report report = failedReport(true);
        stubBase(report, leaderStudy());
        when(apartmentRepository.existsById(APARTMENT_ID)).thenReturn(true);
        when(reportInputQueryRepository.loadSnapshot(REPORT_ID))
                .thenReturn(Optional.of(snapshot(List.of())));
        when(checklistRepository.existsBySessionId(SESSION_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.retry(MEMBER_ID, REPORT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode()).isEqualTo(
                                    ErrorCode.REPORT_SOURCE_DATA_INSUFFICIENT
                            );
                            assertThat(exception.getData().get("missing"))
                                    .asList()
                                    .contains("FIELD_RECORD");
                        }
                );

        assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
        verify(evidenceWriteRepository, never()).deleteByReportId(REPORT_ID);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 사용_가능한_TEXT가_있으면_미완료_STT는_품질_정보로_남기고_재생성을_수락한다() {
        Report report = failedReport(true);
        stubBase(report, leaderStudy());
        when(apartmentRepository.existsById(APARTMENT_ID)).thenReturn(true);
        when(reportInputQueryRepository.loadSnapshot(REPORT_ID))
                .thenReturn(Optional.of(snapshot(
                        List.of(textRecord()),
                        List.of(incompleteSttJob())
                )));
        when(checklistRepository.existsBySessionId(SESSION_ID))
                .thenReturn(true);

        ReportRetryService.RetryResult result = service.retry(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(result.httpStatus().value()).isEqualTo(202);
        verify(evidenceWriteRepository).deleteByReportId(REPORT_ID);
        verify(eventPublisher).publishEvent(new ReportRequestedEvent(
                STUDY_ID,
                SESSION_ID,
                APARTMENT_ID,
                NOW
        ));
    }

    @Test
    void 미완료_STT만_있으면_FIELD_RECORD와_STT_TEXT가_부족하다고_반환한다() {
        Report report = failedReport(true);
        stubBase(report, leaderStudy());
        when(apartmentRepository.existsById(APARTMENT_ID)).thenReturn(true);
        when(reportInputQueryRepository.loadSnapshot(REPORT_ID))
                .thenReturn(Optional.of(snapshot(
                        List.of(),
                        List.of(incompleteSttJob())
                )));
        when(checklistRepository.existsBySessionId(SESSION_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.retry(MEMBER_ID, REPORT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getData().get("missing")
                        ).asList().containsExactly(
                                "FIELD_RECORD",
                                "STT_TEXT"
                        )
                );

        verify(evidenceWriteRepository, never()).deleteByReportId(REPORT_ID);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 존재하지_않는_리포트는_404이고_후속_의존성을_호출하지_않는다() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(activeMember()));
        when(reportRepository.findByIdForUpdate(REPORT_ID))
                .thenReturn(Optional.empty());

        assertError(ErrorCode.REPORT_NOT_FOUND);

        verifyNoInteractions(
                studyRepository,
                apartmentRepository,
                checklistRepository,
                reportInputQueryRepository,
                evidenceWriteRepository,
                eventPublisher
        );
    }

    private void assertError(ErrorCode expected) {
        assertThatThrownBy(() -> service.retry(MEMBER_ID, REPORT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expected)
                );
    }

    private void stubBase(Report report, Study study) {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(activeMember()));
        when(reportRepository.findByIdForUpdate(REPORT_ID))
                .thenReturn(Optional.of(report));
        when(studyRepository.findById(STUDY_ID))
                .thenReturn(Optional.of(study));
    }

    private void stubValidSource() {
        when(apartmentRepository.existsById(APARTMENT_ID)).thenReturn(true);
        when(reportInputQueryRepository.loadSnapshot(REPORT_ID))
                .thenReturn(Optional.of(snapshot(List.of(textRecord()))));
        when(checklistRepository.existsBySessionId(SESSION_ID))
                .thenReturn(true);
    }

    private ReportInputQueryRepository.Snapshot snapshot(
            List<ReportInputQueryRepository.FieldRecordRow> fieldRecords
    ) {
        return snapshot(fieldRecords, List.of());
    }

    private ReportInputQueryRepository.Snapshot snapshot(
            List<ReportInputQueryRepository.FieldRecordRow> fieldRecords,
            List<ReportInputQueryRepository.IncompleteSttJobRow>
                    incompleteSttJobs
    ) {
        return new ReportInputQueryRepository.Snapshot(
                new ReportInputQueryRepository.ContextRow(
                        REPORT_ID,
                        STUDY_ID,
                        APARTMENT_ID,
                        SESSION_ID,
                        FieldSessionStatus.ENDED,
                        OFFSET_NOW.minusHours(2),
                        OFFSET_NOW.minusHours(1),
                        OFFSET_NOW,
                        true
                ),
                List.of(new ReportInputQueryRepository.ParticipantRow(
                        31L,
                        MEMBER_ID,
                        FieldParticipantStatus.ENDED,
                        OFFSET_NOW.minusHours(2),
                        OFFSET_NOW.minusHours(1)
                )),
                List.of(new ReportInputQueryRepository.ChecklistItemRow(
                        21L,
                        22L,
                        MEMBER_ID,
                        false,
                        "TRANSPORT",
                        "교통",
                        null,
                        1,
                        true,
                        OFFSET_NOW.minusMinutes(80)
                )),
                fieldRecords,
                incompleteSttJobs
        );
    }

    private ReportInputQueryRepository.IncompleteSttJobRow
            incompleteSttJob() {
        return new ReportInputQueryRepository.IncompleteSttJobRow(
                "stt-pending",
                MEMBER_ID,
                22L,
                SttStatus.PROCESSING,
                true,
                null,
                OFFSET_NOW.minusMinutes(60)
        );
    }

    private ReportInputQueryRepository.FieldRecordRow textRecord() {
        return new ReportInputQueryRepository.FieldRecordRow(
                71L,
                SESSION_ID,
                22L,
                MEMBER_ID,
                FieldRecordSourceType.TEXT,
                "역과 가까움",
                null,
                null,
                null,
                OFFSET_NOW.minusMinutes(70),
                OFFSET_NOW.minusMinutes(70)
        );
    }

    private Report failedReport(boolean retryable) {
        Report report = report();
        report.acquire("a".repeat(64), NOW.minusSeconds(60));
        report.fail(
                ReportProgressStage.NORMALIZATION,
                "NORMALIZATION_FAILED",
                "입력 정규화에 실패했습니다.",
                retryable,
                "b".repeat(64),
                NOW.minusSeconds(30)
        );
        return report;
    }

    private Report report() {
        Report report = Report.create(
                STUDY_ID,
                SESSION_ID,
                APARTMENT_ID
        );
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        return report;
    }

    private Member activeMember() {
        return new Member(
                "leader@example.com",
                "hash",
                "스터디장"
        );
    }

    private Study leaderStudy() {
        return studyWithLeader(MEMBER_ID);
    }

    private Study studyWithLeader(Long leaderId) {
        return Study.create(
                APARTMENT_ID,
                leaderId,
                "임장 스터디",
                "소개",
                "목표",
                4,
                StudyPurpose.RESIDENCE
        );
    }
}
