package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.domain.ReportProgressStage;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.response.ReportStatusResponse;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReportStatusService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String PENDING_MESSAGE =
            "임장 기록을 수집할 준비를 하고 있습니다.";
    private static final String FAILED_MESSAGE =
            "리포트 생성에 실패했습니다.";
    private static final String GENERIC_FAIL_REASON =
            "리포트 생성 중 일시적인 오류가 발생했습니다.";
    private static final Set<String> SAFE_FAIL_REASONS = Set.of(
            GENERIC_FAIL_REASON,
            "AI 분석 결과를 처리하는 중 일시적인 오류가 발생했습니다."
    );

    private final ReportRepository reportRepository;
    private final MemberRepository memberRepository;
    private final StudyRepository studyRepository;
    private final StudyMemberRepository studyMemberRepository;

    @Transactional(readOnly = true)
    public ReportStatusResponse getStatus(Long memberId, Long reportId) {
        validateActiveMember(memberId);

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REPORT_NOT_FOUND
                ));
        validateAccess(memberId, report);

        ReportProgressStage stage = normalizedStage(report);
        ReportStatus status = report.getStatus();
        return new ReportStatusResponse(
                report.getId(),
                status,
                progressRate(status, stage),
                stage.name(),
                progressMessage(status, stage),
                status == ReportStatus.DONE,
                status == ReportStatus.FAILED && report.isRetryable(),
                safeFailReason(report),
                toSeoul(report.getCreatedAt()),
                status == ReportStatus.DONE
                        ? toSeoul(report.getCompletedAt())
                        : null,
                toSeoul(report.getUpdatedAt())
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

    private void validateAccess(Long memberId, Report report) {
        if (report.getStatus() == ReportStatus.DONE) {
            return;
        }

        Study study = studyRepository
                .findByIdAndDeletedAtIsNull(report.getStudyId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REPORT_ACCESS_DENIED
                ));
        if (memberId.equals(study.getLeaderId())) {
            return;
        }

        boolean activeMember = studyMemberRepository
                .findByStudyIdAndMemberId(study.getId(), memberId)
                .filter(member ->
                        member.getStatus() == StudyMemberStatus.ACTIVE)
                .isPresent();

        if (!activeMember) {
            throw new BusinessException(ErrorCode.REPORT_ACCESS_DENIED);
        }
    }

    private ReportProgressStage normalizedStage(Report report) {
        if (report.getStatus() == ReportStatus.DONE) {
            return ReportProgressStage.COMPLETED;
        }
        if (report.getStatus() == ReportStatus.PENDING) {
            return ReportProgressStage.RECORD_COLLECTION;
        }

        ReportProgressStage stage = ReportProgressStage.from(
                report.getProgressStage()
        );
        return stage == ReportProgressStage.COMPLETED
                ? ReportProgressStage.RECORD_COLLECTION
                : stage;
    }

    private int progressRate(
            ReportStatus status,
            ReportProgressStage stage
    ) {
        return switch (status) {
            case PENDING -> 0;
            case DONE -> 100;
            case IN_PROGRESS, FAILED -> stage.progressRate();
        };
    }

    private String progressMessage(
            ReportStatus status,
            ReportProgressStage stage
    ) {
        return switch (status) {
            case PENDING -> PENDING_MESSAGE;
            case IN_PROGRESS -> stage.progressMessage();
            case DONE -> ReportProgressStage.COMPLETED.progressMessage();
            case FAILED -> FAILED_MESSAGE;
        };
    }

    private String safeFailReason(Report report) {
        if (report.getStatus() != ReportStatus.FAILED) {
            return null;
        }

        String failReason = report.getFailReason();
        if (failReason == null || failReason.isBlank()) {
            return GENERIC_FAIL_REASON;
        }

        String normalized = failReason.strip().replaceAll("\\s+", " ");
        return SAFE_FAIL_REASONS.contains(normalized)
                ? normalized
                : GENERIC_FAIL_REASON;
    }

    private OffsetDateTime toSeoul(Instant instant) {
        return instant == null
                ? null
                : instant.atZone(SEOUL).toOffsetDateTime();
    }

}
