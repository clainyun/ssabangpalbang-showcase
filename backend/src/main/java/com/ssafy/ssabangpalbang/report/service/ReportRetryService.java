package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecordSourceType;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.integration.ReportRequestedEvent;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.domain.ReportProgressStage;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.response.ReportResponseCode;
import com.ssafy.ssabangpalbang.report.dto.response.ReportRetryResponse;
import com.ssafy.ssabangpalbang.report.repository.ReportEvidenceWriteRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportRetryService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ReportRepository reportRepository;
    private final MemberRepository memberRepository;
    private final StudyRepository studyRepository;
    private final ApartmentRepository apartmentRepository;
    private final ChecklistRepository checklistRepository;
    private final ReportInputQueryRepository reportInputQueryRepository;
    private final ReportEvidenceWriteRepository evidenceWriteRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RetryResult retry(Long memberId, Long reportId) {
        validateActiveMember(memberId);

        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REPORT_NOT_FOUND
                ));
        Study study = studyRepository.findById(report.getStudyId())
                .orElseThrow(() -> insufficient("STUDY"));
        requireLeader(memberId, study);

        if (isRetryInProgress(report)) {
            return alreadyInProgress(report);
        }
        if (report.getStatus() != ReportStatus.FAILED) {
            throw new BusinessException(
                    ErrorCode.REPORT_RETRY_NOT_ALLOWED,
                    Map.of(
                            "reportId", report.getId(),
                            "status", report.getStatus().name()
                    )
            );
        }
        if (!report.isRetryable()) {
            throw new BusinessException(
                    ErrorCode.REPORT_RETRY_NOT_RETRYABLE,
                    Map.of(
                            "reportId", report.getId(),
                            "isRetryable", false
                    )
            );
        }

        Instant now = clock.instant();
        validateSourceData(report, study, now);
        evidenceWriteRepository.deleteByReportId(report.getId());
        report.prepareRetry(now);
        eventPublisher.publishEvent(new ReportRequestedEvent(
                report.getStudyId(),
                report.getFieldSessionId(),
                report.getApartmentId(),
                now
        ));

        return new RetryResult(
                HttpStatus.ACCEPTED,
                ReportResponseCode.REPORT_RETRY_ACCEPTED,
                toResponse(report)
        );
    }

    private void validateActiveMember(Long memberId) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
    }

    private void requireLeader(Long memberId, Study study) {
        if (!memberId.equals(study.getLeaderId())) {
            throw new BusinessException(
                    ErrorCode.REPORT_RETRY_ACCESS_DENIED
            );
        }
    }

    private boolean isRetryInProgress(Report report) {
        return report.getRetryRequestedAt() != null
                && (report.getStatus() == ReportStatus.PENDING
                || report.getStatus() == ReportStatus.IN_PROGRESS);
    }

    private RetryResult alreadyInProgress(Report report) {
        return new RetryResult(
                HttpStatus.OK,
                ReportResponseCode.REPORT_RETRY_ALREADY_IN_PROGRESS,
                toResponse(report)
        );
    }

    private void validateSourceData(
            Report report,
            Study study,
            Instant now
    ) {
        List<String> missing = new ArrayList<>();
        if (study.getDeletedAt() != null
                || study.getCanceledAt() != null
                || study.getStatus() == StudyStatus.CANCELED) {
            missing.add("STUDY");
        }
        if (!report.getApartmentId().equals(study.getApartmentId())
                || !apartmentRepository.existsById(report.getApartmentId())) {
            missing.add("APARTMENT");
        }

        ReportInputQueryRepository.Snapshot snapshot =
                reportInputQueryRepository.loadSnapshot(report.getId())
                        .orElse(null);
        if (snapshot == null
                || !snapshot.context().sourceValid()
                || snapshot.context().sessionStatus()
                != FieldSessionStatus.ENDED
                || !report.getFieldSessionId().equals(
                snapshot.context().fieldSessionId()
        )) {
            missing.add("FIELD_SESSION");
        } else {
            validateSnapshot(snapshot, now, missing);
        }

        if (!missing.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.REPORT_SOURCE_DATA_INSUFFICIENT,
                    Map.of("missing", List.copyOf(missing))
            );
        }
    }

    private void validateSnapshot(
            ReportInputQueryRepository.Snapshot snapshot,
            Instant now,
            List<String> missing
    ) {
        if (snapshot.participants().isEmpty()) {
            missing.add("FIELD_PARTICIPANT");
        }

        Long sessionId = snapshot.context().fieldSessionId();
        boolean checklistExists = checklistRepository.existsBySessionId(
                sessionId
        );
        if (!checklistExists) {
            missing.add("CHECKLIST");
        }
        if (snapshot.checklistItems().isEmpty()) {
            missing.add("CHECKLIST_ITEM");
        }

        List<ReportInputQueryRepository.FieldRecordRow> activeRecords =
                snapshot.fieldRecords().stream()
                        .filter(record -> record.deletedAt() == null)
                        .toList();
        boolean hasUsableRecord = activeRecords.stream().anyMatch(record ->
                isUsableRecord(record, now));
        if (!hasUsableRecord) {
            missing.add("FIELD_RECORD");
        }
        boolean incompleteStt = activeRecords.stream()
                .filter(record -> record.sourceType()
                        == FieldRecordSourceType.STT)
                .anyMatch(record -> record.sttStatus() != SttStatus.DONE
                        || isBlank(record.textContent()));
        if (!hasUsableRecord
                && (incompleteStt
                || !snapshot.incompleteSttJobs().isEmpty())) {
            missing.add("STT_TEXT");
        }
    }

    private boolean isUsableRecord(
            ReportInputQueryRepository.FieldRecordRow record,
            Instant now
    ) {
        return switch (record.sourceType()) {
            case TEXT -> !isBlank(record.textContent());
            case STT -> record.sttStatus() == SttStatus.DONE
                    && !isBlank(record.textContent());
            case PHOTO -> record.photoFile() != null
                    && record.photoFile().uploadStatus()
                    == UploadStatus.COMPLETED
                    && record.photoFile().deletedAt() == null
                    && (record.photoFile().expiresAt() == null
                    || record.photoFile().expiresAt().toInstant().isAfter(now));
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException insufficient(String missing) {
        return new BusinessException(
                ErrorCode.REPORT_SOURCE_DATA_INSUFFICIENT,
                Map.of("missing", List.of(missing))
        );
    }

    private ReportRetryResponse toResponse(Report report) {
        ReportStatus status = report.getStatus();
        ReportProgressStage stage = status == ReportStatus.PENDING
                ? ReportProgressStage.RECORD_COLLECTION
                : ReportProgressStage.from(report.getProgressStage());
        int progressRate = status == ReportStatus.PENDING
                ? 0
                : stage.progressRate();
        return new ReportRetryResponse(
                report.getId(),
                status,
                progressRate,
                stage.name(),
                toSeoul(report.getRetryRequestedAt()),
                "/api/v1/reports/" + report.getId() + "/status"
        );
    }

    private OffsetDateTime toSeoul(Instant value) {
        return value == null
                ? null
                : value.atZone(SEOUL).toOffsetDateTime();
    }

    public record RetryResult(
            HttpStatus httpStatus,
            ReportResponseCode responseCode,
            ReportRetryResponse response
    ) {
    }
}
