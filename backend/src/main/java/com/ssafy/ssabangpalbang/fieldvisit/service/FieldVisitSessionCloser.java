package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.integration.ReportRequestedEvent;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class FieldVisitSessionCloser {

    private final ApplicationEventPublisher eventPublisher;
    private final ReportRepository reportRepository;

    public boolean endByAllParticipants(
            FieldSession session,
            Study study,
            Instant endedAt
    ) {
        if (session.getStatus() == FieldSessionStatus.ENDED) {
            return false;
        }
        session.endByAllParticipants(endedAt);
        ensureReport(session, study);
        publish(session, study, endedAt);
        return true;
    }

    public boolean endByLeader(
            FieldSession session,
            Study study,
            Instant endedAt,
            Long leaderMemberId
    ) {
        if (session.getStatus() == FieldSessionStatus.ENDED) {
            return false;
        }
        session.endByLeader(endedAt, leaderMemberId);
        ensureReport(session, study);
        publish(session, study, endedAt);
        return true;
    }

    public boolean endByMajority(
            FieldSession session,
            Study study,
            Instant endedAt
    ) {
        if (session.getStatus() == FieldSessionStatus.ENDED) {
            return false;
        }
        session.endByMajority(endedAt);
        ensureReport(session, study);
        publish(session, study, endedAt);
        return true;
    }

    private Report ensureReport(FieldSession session, Study study) {
        Report existing = reportRepository.findByStudyIdForUpdate(study.getId())
                .orElse(null);
        if (existing != null) {
            if (!session.getId().equals(existing.getFieldSessionId())
                    || !study.getApartmentId().equals(
                    existing.getApartmentId())) {
                throw new IllegalStateException(
                        "Existing report does not match the ended field session"
                );
            }
            return existing;
        }
        return reportRepository.saveAndFlush(Report.create(
                study.getId(),
                session.getId(),
                study.getApartmentId()
        ));
    }

    private void publish(FieldSession session, Study study, Instant occurredAt) {
        eventPublisher.publishEvent(new ReportRequestedEvent(
                study.getId(),
                session.getId(),
                study.getApartmentId(),
                occurredAt
        ));
    }
}
