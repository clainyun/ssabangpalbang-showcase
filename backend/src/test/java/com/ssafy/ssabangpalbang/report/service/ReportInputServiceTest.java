package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecordSourceType;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.report.dto.response.ReportInputResponse;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository.ChecklistItemRow;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository.ContextRow;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository.FieldRecordRow;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository.IncompleteSttJobRow;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository.ParticipantRow;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository.PhotoFileRow;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportInputServiceTest {

    private static final long REPORT_ID = 48L;
    private static final OffsetDateTime UTC_TIME = OffsetDateTime.of(
            2026,
            8,
            2,
            1,
            30,
            0,
            0,
            ZoneOffset.UTC
    );

    @Mock
    private ReportInputQueryRepository reportInputQueryRepository;

    private ReportInputService reportInputService;

    @BeforeEach
    void setUp() {
        reportInputService = new ReportInputService(
                reportInputQueryRepository
        );
    }

    @Test
    void 권위_원본_스냅샷을_v1_응답으로_변환한다() {
        when(reportInputQueryRepository.loadSnapshot(REPORT_ID))
                .thenReturn(Optional.of(validSnapshot()));

        ReportInputResponse response = reportInputService.getInput(REPORT_ID);

        assertThat(response.schemaVersion()).isEqualTo(1);
        assertThat(response.reportId()).isEqualTo(REPORT_ID);
        assertThat(response.studyId()).isEqualTo(7L);
        assertThat(response.apartmentId()).isEqualTo(100L);
        assertThat(response.fieldSessionId()).isEqualTo(3L);
        assertThat(response.sessionStatus())
                .isEqualTo(FieldSessionStatus.ENDED);
        assertThat(response.sessionStartedAt().getOffset())
                .isEqualTo(ZoneOffset.ofHours(9));
        assertThat(response.sessionStartedAt().getHour()).isEqualTo(10);
        assertThat(response.sessionEndedAt()).isNull();
        assertThat(response.snapshotAt().getOffset())
                .isEqualTo(ZoneOffset.ofHours(9));

        assertThat(response.participants()).singleElement()
                .satisfies(participant -> {
                    assertThat(participant.fieldParticipantId()).isEqualTo(11L);
                    assertThat(participant.memberId()).isEqualTo(21L);
                    assertThat(participant.status())
                            .isEqualTo(FieldParticipantStatus.IN_PROGRESS);
                    assertThat(participant.startedAt().getOffset())
                            .isEqualTo(ZoneOffset.ofHours(9));
                    assertThat(participant.endedAt()).isNull();
                });
        assertThat(response.checklistItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.checklistId()).isEqualTo(31L);
                    assertThat(item.checklistItemId()).isEqualTo(32L);
                    assertThat(item.memberId()).isEqualTo(21L);
                    assertThat(item.fallback()).isFalse();
                    assertThat(item.completed()).isTrue();
                    assertThat(item.completedAt().getOffset())
                            .isEqualTo(ZoneOffset.ofHours(9));
                });

        assertThat(response.authoritativeSourceIds())
                .containsExactly(4L, 9L, 12L);
        assertThat(response.fieldRecords()).hasSize(3);
        assertThat(response.fieldRecords().get(0).deletedAt()).isNotNull();
        assertThat(response.fieldRecords().get(1).sttStatus())
                .isEqualTo(SttStatus.DONE);
        assertThat(response.fieldRecords().get(2).photoFile())
                .satisfies(photo -> {
                    assertThat(photo.fileId()).isEqualTo(77L);
                    assertThat(photo.uploadStatus())
                            .isEqualTo(UploadStatus.COMPLETED);
                    assertThat(photo.expiresAt().getOffset())
                            .isEqualTo(ZoneOffset.ofHours(9));
                });
        assertThat(response.incompleteSttJobs()).singleElement()
                .satisfies(job -> {
                    assertThat(job.sttId()).isEqualTo("stt-1");
                    assertThat(job.status()).isEqualTo(SttStatus.PROCESSING);
                    assertThat(job.retryable()).isTrue();
                    assertThat(job.failCode()).isNull();
                    assertThat(job.requestedAt().getOffset())
                            .isEqualTo(ZoneOffset.ofHours(9));
                });
    }

    @Test
    void Report가_없으면_REPORT_NOT_FOUND를_반환한다() {
        when(reportInputQueryRepository.loadSnapshot(REPORT_ID))
                .thenReturn(Optional.empty());

        assertReportNotFound();
    }

    @Test
    void 권위_원본_관계가_유효하지_않으면_REPORT_NOT_FOUND를_반환한다() {
        ContextRow invalidContext = new ContextRow(
                REPORT_ID,
                7L,
                100L,
                3L,
                FieldSessionStatus.ENDED,
                UTC_TIME,
                UTC_TIME,
                UTC_TIME,
                false
        );
        when(reportInputQueryRepository.loadSnapshot(REPORT_ID))
                .thenReturn(Optional.of(new Snapshot(
                        invalidContext,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )));

        assertReportNotFound();
    }

    private void assertReportNotFound() {
        assertThatThrownBy(() -> reportInputService.getInput(REPORT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.REPORT_NOT_FOUND));
    }

    private Snapshot validSnapshot() {
        ContextRow context = new ContextRow(
                REPORT_ID,
                7L,
                100L,
                3L,
                FieldSessionStatus.ENDED,
                UTC_TIME,
                null,
                UTC_TIME.plusMinutes(10),
                true
        );
        List<ParticipantRow> participants = List.of(new ParticipantRow(
                11L,
                21L,
                FieldParticipantStatus.IN_PROGRESS,
                UTC_TIME,
                null
        ));
        List<ChecklistItemRow> checklistItems = List.of(
                new ChecklistItemRow(
                        31L,
                        32L,
                        21L,
                        false,
                        "TRANSPORT",
                        "대중교통 접근성을 확인했나요?",
                        null,
                        1,
                        true,
                        UTC_TIME.plusMinutes(2)
                )
        );
        List<FieldRecordRow> fieldRecords = List.of(
                new FieldRecordRow(
                        12L,
                        3L,
                        32L,
                        21L,
                        FieldRecordSourceType.TEXT,
                        "삭제된 원본도 AI-004 판단을 위해 전달",
                        null,
                        null,
                        UTC_TIME.plusMinutes(4),
                        UTC_TIME.plusMinutes(1),
                        UTC_TIME.plusMinutes(4)
                ),
                new FieldRecordRow(
                        4L,
                        3L,
                        32L,
                        21L,
                        FieldRecordSourceType.STT,
                        "변환된 음성 기록",
                        SttStatus.DONE,
                        null,
                        null,
                        UTC_TIME.plusMinutes(2),
                        UTC_TIME.plusMinutes(3)
                ),
                new FieldRecordRow(
                        9L,
                        3L,
                        32L,
                        21L,
                        FieldRecordSourceType.PHOTO,
                        null,
                        null,
                        new PhotoFileRow(
                                77L,
                                "image/jpeg",
                                1024L,
                                UploadStatus.COMPLETED,
                                UTC_TIME.plusDays(1),
                                null
                        ),
                        null,
                        UTC_TIME.plusMinutes(4),
                        UTC_TIME.plusMinutes(4)
                )
        );
        List<IncompleteSttJobRow> incompleteSttJobs = List.of(
                new IncompleteSttJobRow(
                        "stt-1",
                        21L,
                        32L,
                        SttStatus.PROCESSING,
                        true,
                        null,
                        UTC_TIME.plusMinutes(5)
                )
        );
        return new Snapshot(
                context,
                participants,
                checklistItems,
                fieldRecords,
                incompleteSttJobs
        );
    }
}
