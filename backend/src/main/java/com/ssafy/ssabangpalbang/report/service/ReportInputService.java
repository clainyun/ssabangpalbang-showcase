package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.report.dto.response.ReportInputResponse;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository.ChecklistItemRow;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository.ContextRow;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository.FieldRecordRow;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository.IncompleteSttJobRow;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository.ParticipantRow;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository.PhotoFileRow;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository.Snapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportInputService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ReportInputQueryRepository reportInputQueryRepository;

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public ReportInputResponse getInput(Long reportId) {
        Snapshot snapshot = reportInputQueryRepository
                .loadSnapshot(reportId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REPORT_NOT_FOUND
                ));
        ContextRow context = snapshot.context();
        if (!context.sourceValid()) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }

        List<ReportInputResponse.FieldRecord> fieldRecords = snapshot
                .fieldRecords()
                .stream()
                .map(this::toFieldRecord)
                .toList();
        List<Long> authoritativeSourceIds = fieldRecords.stream()
                .map(ReportInputResponse.FieldRecord::sourceId)
                .distinct()
                .sorted()
                .toList();

        return new ReportInputResponse(
                ReportInputResponse.SCHEMA_VERSION,
                context.reportId(),
                context.studyId(),
                context.apartmentId(),
                context.fieldSessionId(),
                context.sessionStatus(),
                toSeoul(context.sessionStartedAt()),
                toSeoul(context.sessionEndedAt()),
                toSeoul(context.snapshotAt()),
                snapshot.participants().stream()
                        .map(this::toParticipant)
                        .toList(),
                snapshot.checklistItems().stream()
                        .map(this::toChecklistItem)
                        .toList(),
                authoritativeSourceIds,
                fieldRecords,
                snapshot.incompleteSttJobs().stream()
                        .map(this::toIncompleteSttJob)
                        .toList()
        );
    }

    private ReportInputResponse.Participant toParticipant(
            ParticipantRow row
    ) {
        return new ReportInputResponse.Participant(
                row.fieldParticipantId(),
                row.memberId(),
                row.status(),
                toSeoul(row.startedAt()),
                toSeoul(row.endedAt())
        );
    }

    private ReportInputResponse.ChecklistItem toChecklistItem(
            ChecklistItemRow row
    ) {
        return new ReportInputResponse.ChecklistItem(
                row.checklistId(),
                row.checklistItemId(),
                row.memberId(),
                row.fallback(),
                row.category(),
                row.title(),
                row.subtitle(),
                row.displayOrder(),
                row.completed(),
                toSeoul(row.completedAt())
        );
    }

    private ReportInputResponse.FieldRecord toFieldRecord(
            FieldRecordRow row
    ) {
        return new ReportInputResponse.FieldRecord(
                row.sourceId(),
                row.sessionId(),
                row.checklistItemId(),
                row.authorId(),
                row.sourceType(),
                row.textContent(),
                row.sttStatus(),
                toPhotoFile(row.photoFile()),
                toSeoul(row.deletedAt()),
                toSeoul(row.recordedAt()),
                toSeoul(row.updatedAt())
        );
    }

    private ReportInputResponse.PhotoFile toPhotoFile(PhotoFileRow row) {
        if (row == null) {
            return null;
        }
        return new ReportInputResponse.PhotoFile(
                row.fileId(),
                row.contentType(),
                row.sizeBytes(),
                row.uploadStatus(),
                toSeoul(row.expiresAt()),
                toSeoul(row.deletedAt())
        );
    }

    private ReportInputResponse.IncompleteSttJob toIncompleteSttJob(
            IncompleteSttJobRow row
    ) {
        return new ReportInputResponse.IncompleteSttJob(
                row.sttId(),
                row.memberId(),
                row.checklistItemId(),
                row.status(),
                row.retryable(),
                row.failCode(),
                toSeoul(row.requestedAt())
        );
    }

    private OffsetDateTime toSeoul(OffsetDateTime value) {
        return value == null
                ? null
                : value.atZoneSameInstant(SEOUL).toOffsetDateTime();
    }
}
