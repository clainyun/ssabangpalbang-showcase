package com.ssafy.ssabangpalbang.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.domain.ReportProgressStage;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest;
import com.ssafy.ssabangpalbang.report.dto.response.ReportDetailResponse;
import com.ssafy.ssabangpalbang.report.repository.ReportDetailQueryRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReportDetailService {

    private static final Logger log =
            LoggerFactory.getLogger(ReportDetailService.class);

    private final MemberRepository memberRepository;
    private final ReportDetailQueryRepository reportDetailQueryRepository;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Transactional(readOnly = true)
    public ReportDetailResponse getDetail(Long memberId, Long reportId) {
        validateActiveMember(memberId);

        ReportDetailQueryRepository.DetailRow row =
                reportDetailQueryRepository.findDetail(memberId, reportId)
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.REPORT_NOT_FOUND
                        ));
        if (!row.sourceValid()) {
            throw new BusinessException(ErrorCode.REPORT_ACCESS_DENIED);
        }
        validateStatus(row);

        ReportGenerationResultRequest result = readResult(row);
        EvidenceIndex evidenceIndex = new EvidenceIndex(
                reportDetailQueryRepository.findEvidenceRows(reportId),
                reportId
        );
        EvidenceCursor evidenceCursor = new EvidenceCursor(evidenceIndex);
        int participantCount = row.participantCount();

        List<ReportDetailResponse.DetailFeature> positiveFeatures = mapFeatures(
                result.topPositiveFeatures(),
                participantCount,
                evidenceCursor,
                reportId
        );
        List<ReportDetailResponse.DetailFeature> cautionFeatures = mapFeatures(
                result.topCautionFeatures(),
                participantCount,
                evidenceCursor,
                reportId
        );
        List<ReportDetailResponse.DetailCommonOpinion> commonOpinions =
                mapCommonOpinions(
                        result.commonOpinions(),
                        participantCount,
                        evidenceCursor,
                        reportId
                );
        List<ReportDetailResponse.DetailConflictingOpinion> conflictingOpinions =
                mapConflictingOpinions(
                        result.conflictingOpinions(),
                        participantCount,
                        evidenceCursor,
                        reportId
                );
        Map<String, Integer> checklistItemCounts =
                loadChecklistItemCounts(row.fieldSessionId());
        List<ReportDetailResponse.DetailCategory> categories = mapCategories(
                result.categories(),
                participantCount,
                evidenceCursor,
                reportId,
                checklistItemCounts
        );
        evidenceCursor.verifyAllMapped(reportId);

        long evidenceCount = evidenceIndex.uniqueSourceCount();
        ReportGenerationResultRequest.Metrics metrics = result.metrics();
        return new ReportDetailResponse(
                row.reportId(),
                row.status(),
                row.progressStage(),
                result.title(),
                result.summary(),
                new ReportDetailResponse.ReportApartmentSummary(
                        row.apartmentId(),
                        row.apartmentName(),
                        row.apartmentAddress(),
                        row.householdCount(),
                        row.completionYearMonth(),
                        row.parkingSpaceCount()
                ),
                new ReportDetailResponse.ReportStudySummary(
                        row.studyId(),
                        row.studyTitle(),
                        row.studyGoal(),
                        row.visitedAt(),
                        participantCount
                ),
                new ReportDetailResponse.DetailMetrics(
                        metrics.totalChecklistItemCount(),
                        metrics.completedChecklistItemCount(),
                        metrics.averageCompletionRate(),
                        metrics.fieldRecordCount(),
                        evidenceCount,
                        evidenceCount > 0
                ),
                positiveFeatures,
                cautionFeatures,
                commonOpinions,
                conflictingOpinions,
                categories,
                new ReportDetailResponse.ViewerPermissions(
                        row.participant(),
                        row.participant(),
                        row.participant(),
                        true
                ),
                row.favoritedByMe(),
                row.favoriteCount(),
                row.postId(),
                row.completedAt(),
                row.updatedAt()
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

    private void validateStatus(ReportDetailQueryRepository.DetailRow row) {
        if (row.status() != ReportStatus.DONE
                && !row.canViewProcessingState()) {
            throw new BusinessException(ErrorCode.REPORT_ACCESS_DENIED);
        }
        if (row.status() == ReportStatus.PENDING
                || row.status() == ReportStatus.IN_PROGRESS) {
            throw new BusinessException(
                    ErrorCode.REPORT_NOT_DONE,
                    Map.of(
                            "reportId", row.reportId(),
                            "status", row.status().name(),
                            "statusApi", "/api/v1/reports/"
                                    + row.reportId() + "/status"
                    )
            );
        }
        if (row.status() == ReportStatus.FAILED) {
            throw new BusinessException(
                    ErrorCode.REPORT_GENERATION_FAILED,
                    Map.of(
                            "reportId", row.reportId(),
                            "retryAvailable", row.retryable()
                    )
            );
        }
        if (row.status() != ReportStatus.DONE
                || !ReportProgressStage.COMPLETED.name()
                .equals(row.progressStage())
                || row.completedAt() == null) {
            throw integrityFailure(row.reportId());
        }
    }

    private ReportGenerationResultRequest readResult(
            ReportDetailQueryRepository.DetailRow row
    ) {
        if (row.resultJson() == null || row.resultJson().isBlank()) {
            throw integrityFailure(row.reportId());
        }
        try {
            ReportGenerationResultRequest result = objectMapper.readValue(
                    row.resultJson(),
                    ReportGenerationResultRequest.class
            );
            if (!validator.validate(result).isEmpty()) {
                throw integrityFailure(row.reportId());
            }
            return result;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw integrityFailure(row.reportId());
        }
    }

    private List<ReportDetailResponse.DetailFeature> mapFeatures(
            List<ReportGenerationResultRequest.Feature> features,
            int participantCount,
            EvidenceCursor evidenceCursor,
            Long reportId
    ) {
        List<ReportDetailResponse.DetailFeature> mapped = new ArrayList<>();
        for (ReportGenerationResultRequest.Feature feature : features) {
            int mentionCount = uniqueCount(
                    feature.participantRefs(),
                    participantCount,
                    reportId
            );
            mapped.add(new ReportDetailResponse.DetailFeature(
                    feature.rank(),
                    feature.label(),
                    feature.summary(),
                    mentionCount,
                    rate(mentionCount, participantCount),
                    evidenceCursor.next()
            ));
        }
        return List.copyOf(mapped);
    }

    private List<ReportDetailResponse.DetailCommonOpinion> mapCommonOpinions(
            List<ReportGenerationResultRequest.CommonOpinion> opinions,
            int participantCount,
            EvidenceCursor evidenceCursor,
            Long reportId
    ) {
        List<ReportDetailResponse.DetailCommonOpinion> mapped = new ArrayList<>();
        for (ReportGenerationResultRequest.CommonOpinion opinion : opinions) {
            int count = uniqueCount(
                    opinion.participantRefs(),
                    participantCount,
                    reportId
            );
            mapped.add(new ReportDetailResponse.DetailCommonOpinion(
                    opinion.category(),
                    opinion.label(),
                    opinion.opinionType(),
                    opinion.summary(),
                    count,
                    rate(count, participantCount),
                    evidenceCursor.next()
            ));
        }
        return List.copyOf(mapped);
    }

    private List<ReportDetailResponse.DetailConflictingOpinion>
    mapConflictingOpinions(
            List<ReportGenerationResultRequest.ConflictingOpinion> opinions,
            int participantCount,
            EvidenceCursor evidenceCursor,
            Long reportId
    ) {
        List<ReportDetailResponse.DetailConflictingOpinion> mapped =
                new ArrayList<>();
        for (ReportGenerationResultRequest.ConflictingOpinion opinion
                : opinions) {
            int positiveCount = uniqueCount(
                    opinion.positiveParticipantRefs(),
                    participantCount,
                    reportId
            );
            int cautionCount = uniqueCount(
                    opinion.cautionParticipantRefs(),
                    participantCount,
                    reportId
            );
            mapped.add(new ReportDetailResponse.DetailConflictingOpinion(
                    opinion.category(),
                    opinion.label(),
                    opinion.summary(),
                    positiveCount,
                    rate(positiveCount, participantCount),
                    cautionCount,
                    rate(cautionCount, participantCount),
                    evidenceCursor.next()
            ));
        }
        return List.copyOf(mapped);
    }

    private Map<String, Integer> loadChecklistItemCounts(Long sessionId) {
        if (sessionId == null) {
            return Map.of();
        }
        Map<String, Integer> counts = new HashMap<>();
        for (ReportDetailQueryRepository.CategoryChecklistCount count
                : reportDetailQueryRepository
                .countChecklistItemsByCategory(sessionId)) {
            if (count.category() != null) {
                counts.merge(
                        count.category(),
                        count.itemCount(),
                        Integer::sum
                );
            }
        }
        return counts;
    }

    private List<ReportDetailResponse.DetailCategory> mapCategories(
            List<ReportGenerationResultRequest.Category> categories,
            int participantCount,
            EvidenceCursor evidenceCursor,
            Long reportId,
            Map<String, Integer> checklistItemCounts
    ) {
        List<ReportDetailResponse.DetailCategory> mapped = new ArrayList<>();
        for (ReportGenerationResultRequest.Category category : categories) {
            Set<String> positiveRefs = new LinkedHashSet<>();
            Set<String> cautionRefs = new LinkedHashSet<>();
            Set<String> uniqueOpinionKeys = new HashSet<>();
            List<ReportDetailResponse.DetailParticipantOpinion> participantOpinions =
                    new ArrayList<>();

            for (ReportGenerationResultRequest.ParticipantOpinion opinion
                    : category.participantOpinions()) {
                String uniqueKey = opinion.participantRef()
                        + ':' + opinion.opinionType();
                if (!uniqueOpinionKeys.add(uniqueKey)) {
                    throw integrityFailure(reportId);
                }
                if (opinion.opinionType()
                        == ReportGenerationResultRequest.OpinionType.POSITIVE) {
                    positiveRefs.add(opinion.participantRef());
                } else {
                    cautionRefs.add(opinion.participantRef());
                }
                participantOpinions.add(
                        new ReportDetailResponse.DetailParticipantOpinion(
                                opinion.participantLabel(),
                                opinion.opinionType(),
                                opinion.summary(),
                                category.dataSufficient()
                                        ? evidenceCursor.next()
                                        : List.of()
                        )
                );
            }

            Set<String> recordedRefs = new HashSet<>(positiveRefs);
            recordedRefs.addAll(cautionRefs);
            validateCount(positiveRefs.size(), participantCount, reportId);
            validateCount(cautionRefs.size(), participantCount, reportId);
            validateCount(recordedRefs.size(), participantCount, reportId);
            int unrecordedCount = participantCount - recordedRefs.size();

            mapped.add(new ReportDetailResponse.DetailCategory(
                    category.category(),
                    category.summary(),
                    checklistItemCounts.getOrDefault(category.category(), 0),
                    positiveRefs.size(),
                    rate(positiveRefs.size(), participantCount),
                    cautionRefs.size(),
                    rate(cautionRefs.size(), participantCount),
                    unrecordedCount,
                    rate(unrecordedCount, participantCount),
                    category.dataSufficient(),
                    List.copyOf(participantOpinions)
            ));
        }
        return List.copyOf(mapped);
    }

    private int uniqueCount(
            List<String> participantRefs,
            int participantCount,
            Long reportId
    ) {
        int uniqueCount = new HashSet<>(participantRefs).size();
        if (uniqueCount != participantRefs.size()) {
            throw integrityFailure(reportId);
        }
        validateCount(uniqueCount, participantCount, reportId);
        return uniqueCount;
    }

    private void validateCount(int count, int participantCount, Long reportId) {
        if (count < 0 || count > participantCount) {
            throw integrityFailure(reportId);
        }
    }

    private double rate(int count, int participantCount) {
        if (participantCount == 0) {
            return 0.0;
        }
        return Math.round(count * 1000.0 / participantCount) / 10.0;
    }

    private IllegalStateException integrityFailure(Long reportId) {
        log.error("완료 리포트 상세 데이터 무결성 오류 reportId={}", reportId);
        return new IllegalStateException("Invalid completed report detail");
    }

    private static final class EvidenceIndex {

        private final Map<Integer, EvidenceGroup> groups;
        private final long uniqueSourceCount;

        private EvidenceIndex(
                List<ReportDetailQueryRepository.EvidenceRow> rows,
                Long reportId
        ) {
            Map<Integer, MutableEvidenceGroup> mutableGroups =
                    new LinkedHashMap<>();
            Set<Long> allSources = new HashSet<>();
            for (ReportDetailQueryRepository.EvidenceRow row : rows) {
                if (row.displayOrder() < 1
                        || row.claimKey() == null
                        || row.claimKey().isBlank()
                        || row.sourceId() == null
                        || row.sourceId() < 1) {
                    throw invalidEvidence(reportId);
                }
                MutableEvidenceGroup group = mutableGroups.computeIfAbsent(
                        row.displayOrder(),
                        ignored -> new MutableEvidenceGroup(row.claimKey())
                );
                if (!group.claimKey.equals(row.claimKey())) {
                    throw invalidEvidence(reportId);
                }
                group.sourceIds.add(row.sourceId());
                allSources.add(row.sourceId());
            }

            Map<Integer, EvidenceGroup> immutableGroups = new HashMap<>();
            mutableGroups.forEach((order, group) -> immutableGroups.put(
                    order,
                    new EvidenceGroup(
                            group.claimKey,
                            List.copyOf(group.sourceIds)
                    )
            ));
            this.groups = Map.copyOf(immutableGroups);
            this.uniqueSourceCount = allSources.size();
        }

        private List<Long> sourceIds(int displayOrder) {
            EvidenceGroup group = groups.get(displayOrder);
            return group == null ? List.of() : group.sourceIds();
        }

        private int maxDisplayOrder() {
            return groups.keySet().stream().mapToInt(Integer::intValue)
                    .max().orElse(0);
        }

        private long uniqueSourceCount() {
            return uniqueSourceCount;
        }

        private static IllegalStateException invalidEvidence(Long reportId) {
            log.error("완료 리포트 근거 데이터 무결성 오류 reportId={}", reportId);
            return new IllegalStateException("Invalid report evidence detail");
        }

        private static final class MutableEvidenceGroup {
            private final String claimKey;
            private final Set<Long> sourceIds = new LinkedHashSet<>();

            private MutableEvidenceGroup(String claimKey) {
                this.claimKey = claimKey;
            }
        }

        private record EvidenceGroup(
                String claimKey,
                List<Long> sourceIds
        ) {
        }
    }

    private static final class EvidenceCursor {

        private final EvidenceIndex evidenceIndex;
        private int displayOrder;

        private EvidenceCursor(EvidenceIndex evidenceIndex) {
            this.evidenceIndex = evidenceIndex;
        }

        private List<Long> next() {
            displayOrder++;
            return evidenceIndex.sourceIds(displayOrder);
        }

        private void verifyAllMapped(Long reportId) {
            if (evidenceIndex.maxDisplayOrder() > displayOrder) {
                throw EvidenceIndex.invalidEvidence(reportId);
            }
        }
    }
}
