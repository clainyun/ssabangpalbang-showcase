package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.domain.ReportFavorite;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.response.ReportFavoriteResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportFavoriteResult;
import com.ssafy.ssabangpalbang.report.dto.response.ReportResponseCode;
import com.ssafy.ssabangpalbang.report.dto.response.ReportUnfavoriteResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportUnfavoriteResult;
import com.ssafy.ssabangpalbang.report.repository.ReportFavoriteRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportFavoriteService {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final ReportRepository reportRepository;
    private final ReportFavoriteRepository reportFavoriteRepository;
    private final StudyRepository studyRepository;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Transactional
    public ReportFavoriteResult addFavorite(Long memberId, Long reportId) {
        validateActiveMember(memberId);
        validateFavoriteTarget(reportId);

        return reportFavoriteRepository
                .findByMemberIdAndReportId(memberId, reportId)
                .map(favorite -> favoriteResult(
                        ReportResponseCode.REPORT_FAVORITE_ALREADY_EXISTS,
                        favorite
                ))
                .orElseGet(() -> createFavorite(memberId, reportId));
    }

    @Transactional
    public ReportUnfavoriteResult removeFavorite(
            Long memberId,
            Long reportId
    ) {
        validateActiveMember(memberId);
        validateReportExists(reportId);

        int deletedCount = reportFavoriteRepository
                .deleteByMemberIdAndReportId(memberId, reportId);
        ReportResponseCode responseCode = deletedCount > 0
                ? ReportResponseCode.REPORT_UNFAVORITE_SUCCESS
                : ReportResponseCode.REPORT_ALREADY_UNFAVORITED;

        return new ReportUnfavoriteResult(
                responseCode,
                ReportUnfavoriteResponse.of(
                        reportId,
                        reportFavoriteRepository.countByReportId(reportId),
                        OffsetDateTime.ofInstant(
                                clock.instant(),
                                SEOUL_ZONE_ID
                        )
                )
        );
    }

    private ReportFavoriteResult createFavorite(
            Long memberId,
            Long reportId
    ) {
        try {
            ReportFavorite favorite = saveFavoriteInNewTransaction(
                    memberId,
                    reportId
            );
            return favoriteResult(
                    ReportResponseCode.REPORT_FAVORITE_SUCCESS,
                    favorite
            );
        } catch (DataIntegrityViolationException exception) {
            ReportFavorite favorite = reportFavoriteRepository
                    .findByMemberIdAndReportId(memberId, reportId)
                    .orElseThrow(() -> exception);
            return favoriteResult(
                    ReportResponseCode.REPORT_FAVORITE_ALREADY_EXISTS,
                    favorite
            );
        }
    }

    private void validateActiveMember(Long memberId) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void validateFavoriteTarget(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        if (report.getStatus() != ReportStatus.DONE) {
            throw new BusinessException(
                    ErrorCode.REPORT_FAVORITE_NOT_ALLOWED,
                    Map.of("status", report.getStatus().name())
            );
        }

        studyRepository.findByIdAndDeletedAtIsNull(report.getStudyId())
                .filter(study -> study.getStatus() != StudyStatus.CANCELED)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REPORT_FAVORITE_ACCESS_DENIED
                ));
    }

    private void validateReportExists(Long reportId) {
        reportRepository.findById(reportId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    }

    private ReportFavorite saveFavoriteInNewTransaction(
            Long memberId,
            Long reportId
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        return transactionTemplate.execute(status ->
                reportFavoriteRepository.saveAndFlush(
                        ReportFavorite.of(memberId, reportId)
                )
        );
    }

    private ReportFavoriteResult favoriteResult(
            ReportResponseCode responseCode,
            ReportFavorite favorite
    ) {
        return new ReportFavoriteResult(
                responseCode,
                ReportFavoriteResponse.from(
                        favorite,
                        reportFavoriteRepository.countByReportId(
                                favorite.getReportId()
                        )
                )
        );
    }
}
