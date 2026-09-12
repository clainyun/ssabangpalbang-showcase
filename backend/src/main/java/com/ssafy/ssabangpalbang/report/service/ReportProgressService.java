package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.report.config.ReportInternalProperties;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.domain.ReportProgressStage;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportProgressRequest;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReportProgressService {

    private final ReportRepository reportRepository;
    private final ReportProcessingTokenIssuer tokenIssuer;
    private final ReportInternalProperties properties;
    private final Clock clock;

    @Transactional
    public void updateProgress(
            Long reportId,
            ReportProgressRequest request
    ) {
        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REPORT_NOT_FOUND
                ));
        Instant now = clock.instant();

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

        ReportProgressStage targetStage = request.stage().toDomain();
        if (!report.canUpdateProgressTo(targetStage)) {
            throw new BusinessException(ErrorCode.STALE_PROCESSING_TOKEN);
        }

        report.updateProgress(targetStage, now.plus(properties.leaseDuration()));
    }
}
