package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportFailRequest;
import com.ssafy.ssabangpalbang.report.repository.ReportEvidenceWriteRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReportFailService {

    private final ReportRepository reportRepository;
    private final ReportEvidenceWriteRepository evidenceWriteRepository;
    private final ReportProcessingTokenIssuer tokenIssuer;
    private final ReportFailurePayloadHasher payloadHasher;
    private final Clock clock;

    @Transactional
    public void fail(Long reportId, ReportFailRequest request) {
        requireSafeMessage(request);
        String payloadHash = payloadHasher.hash(request);
        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REPORT_NOT_FOUND
                ));

        if (report.getStatus() == ReportStatus.FAILED) {
            failIdempotently(report, request, payloadHash);
            return;
        }

        Instant now = clock.instant();
        requireActiveProcessingRight(report, request, now);
        if (report.getResultJson() != null
                || report.getCompletePayloadHash() != null
                || evidenceWriteRepository.countByReportId(reportId) > 0) {
            throw new BusinessException(ErrorCode.FAIL_PAYLOAD_CONFLICT);
        }

        report.fail(
                request.failedStage().toDomain(),
                request.errorCode().name(),
                request.errorCode().safeMessage(),
                request.retryable(),
                payloadHash,
                now
        );
    }

    private void requireSafeMessage(ReportFailRequest request) {
        if (!request.errorCode().matchesSafeMessage(request.message())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void failIdempotently(
            Report report,
            ReportFailRequest request,
            String payloadHash
    ) {
        if (report.getProcessingAttempt() == request.processingAttempt()
                && tokenIssuer.matches(
                request.processingToken(),
                report.getProcessingTokenHash()
        )
                && payloadHasher.matches(
                payloadHash,
                report.getFailPayloadHash()
        )) {
            return;
        }
        throw new BusinessException(ErrorCode.FAIL_PAYLOAD_CONFLICT);
    }

    private void requireActiveProcessingRight(
            Report report,
            ReportFailRequest request,
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
