package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class ReportAcquireService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final FieldSessionRepository fieldSessionRepository;
    private final StudyRepository studyRepository;
    private final ReportRepository reportRepository;
    private final ReportProcessingTokenIssuer tokenIssuer;
    private final ReportInternalProperties properties;
    private final Clock clock;

    @Transactional
    public ReportAcquireResponse acquire(ReportAcquireRequest request) {
        FieldSession session = fieldSessionRepository
                .findByStudyIdForUpdate(request.studyId())
                .orElse(null);
        Report existing = reportRepository
                .findByStudyIdForUpdate(request.studyId())
                .orElse(null);

        if (session == null
                || !request.sessionId().equals(session.getId())
                || session.getStatus() != FieldSessionStatus.ENDED) {
            return conflict(existing);
        }

        Study study = studyRepository
                .findByIdAndDeletedAtIsNull(request.studyId())
                .orElse(null);
        if (study == null
                || study.getStatus() == StudyStatus.CANCELED
                || !request.apartmentId().equals(study.getApartmentId())) {
            return conflict(existing);
        }

        if (existing != null && !sameSource(existing, request)) {
            return conflict(existing);
        }

        Instant now = clock.instant();
        if (existing == null) {
            Report created = Report.create(
                    request.studyId(),
                    request.sessionId(),
                    request.apartmentId()
            );
            return claimAndSave(created, now);
        }

        return switch (existing.getStatus()) {
            case DONE -> ReportAcquireResponse.terminal(
                    ReportAcquireStatus.ALREADY_COMPLETED,
                    existing.getId()
            );
            case FAILED -> ReportAcquireResponse.terminal(
                    ReportAcquireStatus.ALREADY_FAILED,
                    existing.getId()
            );
            case PENDING -> claimAndSave(existing, now);
            case IN_PROGRESS -> existing.hasActiveLease(now)
                    ? ReportAcquireResponse.alreadyProcessing(
                            existing.getId(),
                            retryAfterSeconds(
                                    now,
                                    existing.getProcessingLeaseExpiresAt()
                            )
                    )
                    : claimAndSave(existing, now);
        };
    }

    private ReportAcquireResponse claimAndSave(Report report, Instant now) {
        ReportProcessingToken token = tokenIssuer.issue();
        Instant leaseExpiresAt = now.plus(properties.leaseDuration());
        report.acquire(token.sha256Hash(), leaseExpiresAt);
        Report saved = reportRepository.saveAndFlush(report);
        return ReportAcquireResponse.acquired(
                saved.getId(),
                token.rawValue(),
                saved.getProcessingAttempt(),
                leaseExpiresAt.atZone(SEOUL).toOffsetDateTime()
        );
    }

    private boolean sameSource(
            Report report,
            ReportAcquireRequest request
    ) {
        return request.sessionId().equals(report.getFieldSessionId())
                && request.apartmentId().equals(report.getApartmentId());
    }

    private ReportAcquireResponse conflict(Report report) {
        return ReportAcquireResponse.terminal(
                ReportAcquireStatus.CONTRACT_CONFLICT,
                report == null ? null : report.getId()
        );
    }

    private long retryAfterSeconds(Instant now, Instant leaseExpiresAt) {
        long millis = Duration.between(now, leaseExpiresAt).toMillis();
        return Math.max(1L, Math.floorDiv(millis + 999L, 1_000L));
    }
}
