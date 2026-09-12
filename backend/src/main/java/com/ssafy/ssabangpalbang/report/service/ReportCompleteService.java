package com.ssafy.ssabangpalbang.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportCompleteRequest;
import com.ssafy.ssabangpalbang.report.integration.ReportCompletedEvent;
import com.ssafy.ssabangpalbang.report.repository.ReportEvidenceWriteRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportCompleteService {

    private final ReportRepository reportRepository;
    private final ReportInputQueryRepository reportInputQueryRepository;
    private final ReportEvidenceWriteRepository evidenceWriteRepository;
    private final ReportProcessingTokenIssuer tokenIssuer;
    private final ReportCompletionPayloadHasher payloadHasher;
    private final ReportCompleteValidator validator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void complete(Long reportId, ReportCompleteRequest request) {
        String payloadHash = payloadHasher.hash(request);
        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REPORT_NOT_FOUND
                ));

        if (report.getStatus() == ReportStatus.DONE) {
            completeIdempotently(report, request, payloadHash);
            return;
        }

        Instant now = clock.instant();
        requireActiveProcessingRight(report, request, now);
        if (report.getResultJson() != null
                || report.getCompletePayloadHash() != null
                || evidenceWriteRepository.countByReportId(reportId) > 0) {
            throw new BusinessException(
                    ErrorCode.COMPLETE_PAYLOAD_CONFLICT
            );
        }

        ReportInputQueryRepository.Snapshot snapshot =
                reportInputQueryRepository.loadSnapshot(reportId)
                        .filter(value -> value.context().sourceValid())
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.REPORT_NOT_FOUND
                        ));
        List<ReportEvidenceWriteRepository.EvidenceRow> evidenceRows =
                validator.validate(request, snapshot);
        JsonNode generationResult = objectMapper.valueToTree(
                request.generationResult()
        );

        evidenceWriteRepository.insertAll(reportId, evidenceRows);
        report.complete(generationResult, payloadHash, now);
        eventPublisher.publishEvent(new ReportCompletedEvent(
                reportId,
                report.getStudyId()
        ));
    }

    private void completeIdempotently(
            Report report,
            ReportCompleteRequest request,
            String payloadHash
    ) {
        if (report.getProcessingAttempt() == request.processingAttempt()
                && tokenIssuer.matches(
                request.processingToken(),
                report.getProcessingTokenHash()
        )
                && payloadHasher.matches(
                payloadHash,
                report.getCompletePayloadHash()
        )) {
            return;
        }
        throw new BusinessException(ErrorCode.COMPLETE_PAYLOAD_CONFLICT);
    }

    private void requireActiveProcessingRight(
            Report report,
            ReportCompleteRequest request,
            Instant now
    ) {
        if (report.getStatus() != ReportStatus.IN_PROGRESS
                || !report.hasActiveLease(now)
                || report.getProcessingAttempt()
                != request.processingAttempt()
                || !tokenIssuer.matches(
                request.processingToken(),
                report.getProcessingTokenHash()
        )) {
            throw new BusinessException(ErrorCode.STALE_PROCESSING_TOKEN);
        }
    }
}
