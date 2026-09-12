package com.ssafy.ssabangpalbang.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrl;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.report.domain.ReportProgressStage;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest;
import com.ssafy.ssabangpalbang.report.dto.response.ReportEvidenceDetailResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportEvidenceListResponse;
import com.ssafy.ssabangpalbang.report.repository.ReportDetailQueryRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportEvidenceQueryRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReportEvidenceListService {

    private static final Logger log = LoggerFactory.getLogger(
            ReportEvidenceListService.class
    );
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MAX_SOURCE_IDS = 100;
    private static final int PREVIEW_MAX_CODE_POINTS = 200;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final ReportDetailQueryRepository reportDetailQueryRepository;
    private final ReportEvidenceQueryRepository reportEvidenceQueryRepository;
    private final MediaAccessUrlProvider mediaAccessUrlProvider;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Transactional(readOnly = true)
    public ReportEvidenceListResponse getEvidenceList(
            Long memberId,
            Long reportId,
            String sourceTypeRaw,
            String categoryRaw,
            String sourceIdsRaw,
            String cursorRaw,
            String sizeRaw
    ) {
        Query query = parseQuery(
                sourceTypeRaw,
                categoryRaw,
                sourceIdsRaw,
                cursorRaw,
                sizeRaw
        );
        ReportDetailQueryRepository.DetailRow report =
                loadAccessibleCompletedReport(memberId, reportId);
        validateRequestedSources(reportId, query.sourceIds());

        List<ReportEvidenceQueryRepository.EvidenceRow> fetched =
                reportEvidenceQueryRepository.findPage(
                        reportId,
                        query.sourceType(),
                        query.category(),
                        query.sourceIds(),
                        query.cursor(),
                        query.size() + 1
                );
        boolean hasNext = fetched.size() > query.size();
        List<ReportEvidenceQueryRepository.EvidenceRow> page = hasNext
                ? fetched.subList(0, query.size())
                : fetched;

        List<Long> pageSourceIds = page.stream()
                .map(ReportEvidenceQueryRepository.EvidenceRow::sourceId)
                .toList();
        Map<Long, List<ReportEvidenceListResponse.UsedIn>> usedIn =
                mapUsedIn(report, pageSourceIds);

        List<ReportEvidenceListResponse.EvidenceItem> content = page.stream()
                .map(row -> toEvidenceItem(
                        row,
                        usedIn.getOrDefault(row.sourceId(), List.of())
                ))
                .toList();
        ReportEvidenceQueryRepository.EvidenceSummaryRow summary =
                reportEvidenceQueryRepository.findSummary(reportId);
        Long nextCursor = hasNext && !page.isEmpty()
                ? page.get(page.size() - 1).sourceId()
                : null;

        return new ReportEvidenceListResponse(
                reportId,
                new ReportEvidenceListResponse.AppliedFilters(
                        query.sourceType(),
                        query.category(),
                        query.sourceIds()
                ),
                content,
                new ReportEvidenceListResponse.EvidenceSummary(
                        summary.totalEvidenceCount(),
                        summary.textCount(),
                        0,
                        summary.sttCount()
                ),
                nextCursor,
                hasNext
        );
    }

    @Transactional(readOnly = true)
    public ReportEvidenceDetailResponse getEvidenceDetail(
            Long memberId,
            Long reportId,
            Long sourceId
    ) {
        ReportDetailQueryRepository.DetailRow report =
                loadAccessibleCompletedReport(memberId, reportId);
        ReportEvidenceQueryRepository.EvidenceDetailRow evidence =
                reportEvidenceQueryRepository.findDetail(reportId, sourceId)
                        .orElseGet(() -> resolveMissingEvidence(
                                reportId,
                                sourceId
                        ));
        List<ReportEvidenceListResponse.UsedIn> usedIn =
                "PHOTO".equals(evidence.sourceType())
                        ? List.of()
                        : mapUsedIn(report, List.of(sourceId))
                                .getOrDefault(sourceId, List.of());
        return toDetailResponse(reportId, evidence, usedIn);
    }

    private ReportDetailQueryRepository.DetailRow
            loadAccessibleCompletedReport(Long memberId, Long reportId) {
        validateActiveMember(memberId);
        ReportDetailQueryRepository.DetailRow report =
                reportDetailQueryRepository.findDetail(memberId, reportId)
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.REPORT_NOT_FOUND
                        ));
        if (!report.sourceValid()) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }
        if (!report.participant()) {
            throw new BusinessException(
                    ErrorCode.REPORT_EVIDENCE_ACCESS_DENIED
            );
        }
        validateCompleted(report);
        return report;
    }

    private ReportEvidenceQueryRepository.EvidenceDetailRow
            resolveMissingEvidence(Long reportId, Long sourceId) {
        ReportEvidenceQueryRepository.SourceRelationRow relation =
                reportEvidenceQueryRepository.findSourceRelation(
                        reportId,
                        sourceId
                );
        if (!relation.sourceExists()
                || relation.sourceInReportSession()) {
            throw new BusinessException(
                    ErrorCode.REPORT_EVIDENCE_NOT_FOUND
            );
        }
        throw new BusinessException(
                ErrorCode.REPORT_EVIDENCE_REPORT_MISMATCH
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

    private void validateCompleted(
            ReportDetailQueryRepository.DetailRow report
    ) {
        if (report.status() == ReportStatus.FAILED) {
            throw new BusinessException(
                    ErrorCode.REPORT_GENERATION_FAILED,
                    Map.of(
                            "reportId", report.reportId(),
                            "retryAvailable", report.retryable()
                    )
            );
        }
        if (report.status() != ReportStatus.DONE) {
            throw new BusinessException(
                    ErrorCode.REPORT_NOT_DONE,
                    Map.of(
                            "reportId", report.reportId(),
                            "status", report.status().name(),
                            "statusApi", "/api/v1/reports/"
                                    + report.reportId() + "/status"
                    )
            );
        }
        if (!ReportProgressStage.COMPLETED.name().equals(
                report.progressStage()
        ) || report.completedAt() == null) {
            throw integrityFailure(report.reportId());
        }
    }

    private void validateRequestedSources(
            Long reportId,
            List<Long> sourceIds
    ) {
        if (sourceIds.isEmpty()) {
            return;
        }
        long validCount = reportEvidenceQueryRepository
                .countRequestedSources(reportId, sourceIds);
        if (validCount != sourceIds.size()) {
            throw new BusinessException(
                    ErrorCode.REPORT_EVIDENCE_REPORT_MISMATCH
            );
        }
    }

    private Map<Long, List<ReportEvidenceListResponse.UsedIn>> mapUsedIn(
            ReportDetailQueryRepository.DetailRow report,
            List<Long> sourceIds
    ) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, ClaimDescriptor> claims = claimDescriptors(report);
        Map<Long, LinkedHashSet<ReportEvidenceListResponse.UsedIn>> mapped =
                new LinkedHashMap<>();
        for (ReportEvidenceQueryRepository.EvidenceLinkRow link
                : reportEvidenceQueryRepository.findLinks(
                        report.reportId(),
                        sourceIds
                )) {
            ClaimDescriptor descriptor = claims.get(link.displayOrder());
            if (descriptor == null
                    || link.claimKey() == null
                    || link.claimKey().isBlank()) {
                throw integrityFailure(report.reportId());
            }
            mapped.computeIfAbsent(
                            link.sourceId(),
                            ignored -> new LinkedHashSet<>()
                    )
                    .add(new ReportEvidenceListResponse.UsedIn(
                            link.claimKey(),
                            descriptor.resultSection(),
                            descriptor.resultKey()
                    ));
        }

        Map<Long, List<ReportEvidenceListResponse.UsedIn>> result =
                new LinkedHashMap<>();
        mapped.forEach((sourceId, values) -> result.put(
                sourceId,
                List.copyOf(values)
        ));
        return Map.copyOf(result);
    }

    private Map<Integer, ClaimDescriptor> claimDescriptors(
            ReportDetailQueryRepository.DetailRow report
    ) {
        ReportGenerationResultRequest result = readResult(report);
        Map<Integer, ClaimDescriptor> claims = new LinkedHashMap<>();
        int order = 0;
        for (ReportGenerationResultRequest.Feature feature
                : result.topPositiveFeatures()) {
            claims.put(++order, new ClaimDescriptor(
                    "TOP_POSITIVE_FEATURE",
                    feature.label()
            ));
        }
        for (ReportGenerationResultRequest.Feature feature
                : result.topCautionFeatures()) {
            claims.put(++order, new ClaimDescriptor(
                    "TOP_CAUTION_FEATURE",
                    feature.label()
            ));
        }
        for (ReportGenerationResultRequest.CommonOpinion opinion
                : result.commonOpinions()) {
            claims.put(++order, new ClaimDescriptor(
                    "COMMON_OPINION",
                    opinion.label()
            ));
        }
        for (ReportGenerationResultRequest.ConflictingOpinion opinion
                : result.conflictingOpinions()) {
            claims.put(++order, new ClaimDescriptor(
                    "CONFLICTING_OPINION",
                    opinion.label()
            ));
        }
        for (ReportGenerationResultRequest.Category category
                : result.categories()) {
            if (!category.dataSufficient()) {
                continue;
            }
            for (ReportGenerationResultRequest.ParticipantOpinion ignored
                    : category.participantOpinions()) {
                claims.put(++order, new ClaimDescriptor(
                        "CATEGORY_OPINION",
                        category.category()
                ));
            }
        }
        return Map.copyOf(claims);
    }

    private ReportGenerationResultRequest readResult(
            ReportDetailQueryRepository.DetailRow report
    ) {
        if (report.resultJson() == null || report.resultJson().isBlank()) {
            throw integrityFailure(report.reportId());
        }
        try {
            ReportGenerationResultRequest result = objectMapper.readValue(
                    report.resultJson(),
                    ReportGenerationResultRequest.class
            );
            if (!validator.validate(result).isEmpty()) {
                throw integrityFailure(report.reportId());
            }
            return result;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw integrityFailure(report.reportId());
        }
    }

    private ReportEvidenceListResponse.EvidenceItem toEvidenceItem(
            ReportEvidenceQueryRepository.EvidenceRow row,
            List<ReportEvidenceListResponse.UsedIn> usedIn
    ) {
        return new ReportEvidenceListResponse.EvidenceItem(
                row.sourceId(),
                row.sourceType(),
                row.category(),
                new ReportEvidenceListResponse.ChecklistItemSummary(
                        row.checklistItemId(),
                        row.checklistItemTitle(),
                        row.checklistItemSubtitle()
                ),
                "참여자 " + row.participantNumber(),
                preview(row.textContent()),
                null,
                false,
                true,
                usedIn,
                row.recordedAt()
        );
    }

    private ReportEvidenceDetailResponse toDetailResponse(
            Long reportId,
            ReportEvidenceQueryRepository.EvidenceDetailRow row,
            List<ReportEvidenceListResponse.UsedIn> usedIn
    ) {
        return new ReportEvidenceDetailResponse(
                reportId,
                row.sourceId(),
                row.sourceType(),
                row.category(),
                new ReportEvidenceListResponse.ChecklistItemSummary(
                        row.checklistItemId(),
                        row.checklistItemTitle(),
                        row.checklistItemSubtitle()
                ),
                "참여자 " + row.participantNumber(),
                row.textContent(),
                toDetailMedia(row),
                row.sttStatus(),
                usedIn,
                row.recordedAt()
        );
    }

    private ReportEvidenceDetailResponse.Media toDetailMedia(
            ReportEvidenceQueryRepository.EvidenceDetailRow row
    ) {
        if (!"PHOTO".equals(row.sourceType())) {
            return null;
        }
        if (!row.photoAvailable() || row.photoFileId() == null) {
            throw mediaUnavailable(row.sourceId());
        }

        MediaAccessUrl accessUrl;
        try {
            accessUrl = mediaAccessUrlProvider.issue(row.photoFileId());
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.MEDIA_FILE_NOT_FOUND
                    || exception.getErrorCode()
                    == ErrorCode.MEDIA_ACCESS_DENIED) {
                throw mediaUnavailable(row.sourceId());
            }
            throw exception;
        }
        if (accessUrl == null
                || accessUrl.url() == null
                || accessUrl.url().isBlank()
                || accessUrl.expiresAt() == null) {
            throw mediaUnavailable(row.sourceId());
        }
        return new ReportEvidenceDetailResponse.Media(
                true,
                row.photoFileId(),
                row.photoOriginalName(),
                row.photoContentType(),
                row.photoSizeBytes(),
                accessUrl.url(),
                accessUrl.expiresAt()
                        .atZone(SEOUL)
                        .toOffsetDateTime()
        );
    }

    private BusinessException mediaUnavailable(Long sourceId) {
        return new BusinessException(
                ErrorCode.REPORT_EVIDENCE_MEDIA_UNAVAILABLE,
                Map.of(
                        "sourceId", sourceId,
                        "sourceType", "PHOTO"
                )
        );
    }

    private String preview(String text) {
        String normalized = text.strip().replaceAll("\\s+", " ");
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= PREVIEW_MAX_CODE_POINTS) {
            return normalized;
        }
        int end = normalized.offsetByCodePoints(
                0,
                PREVIEW_MAX_CODE_POINTS - 1
        );
        return normalized.substring(0, end) + "…";
    }

    private Query parseQuery(
            String sourceTypeRaw,
            String categoryRaw,
            String sourceIdsRaw,
            String cursorRaw,
            String sizeRaw
    ) {
        return new Query(
                parseSourceType(sourceTypeRaw),
                parseCategory(categoryRaw),
                parseSourceIds(sourceIdsRaw),
                parsePositiveLong(cursorRaw, "cursor"),
                parseSize(sizeRaw)
        );
    }

    private String parseSourceType(String raw) {
        if (raw == null) {
            return null;
        }
        if ("TEXT".equals(raw)
                || "PHOTO".equals(raw)
                || "STT".equals(raw)) {
            return raw;
        }
        throw new BusinessException(
                ErrorCode.REPORT_EVIDENCE_SOURCE_TYPE_INVALID,
                Map.of(
                        "field", "sourceType",
                        "allowedValues", List.of("TEXT", "PHOTO", "STT")
                )
        );
    }

    private String parseCategory(String raw) {
        if (raw == null) {
            return null;
        }
        String category = raw.strip();
        if (category.isEmpty() || category.length() > 30) {
            throw invalidInput(
                    "category",
                    "category는 1자 이상 30자 이하이어야 합니다."
            );
        }
        return category;
    }

    private List<Long> parseSourceIds(String raw) {
        if (raw == null) {
            return List.of();
        }
        String[] tokens = raw.split(",", -1);
        Set<Long> sourceIds = new LinkedHashSet<>();
        try {
            for (String token : tokens) {
                if (token.isBlank()) {
                    throw new NumberFormatException();
                }
                long sourceId = Long.parseLong(token.strip());
                if (sourceId < 1) {
                    throw new NumberFormatException();
                }
                sourceIds.add(sourceId);
            }
        } catch (NumberFormatException exception) {
            throw invalidInput(
                    "sourceIds",
                    "sourceIds는 1 이상의 숫자를 쉼표로 구분해 전달해야 합니다."
            );
        }
        if (sourceIds.size() > MAX_SOURCE_IDS) {
            throw invalidInput(
                    "sourceIds",
                    "sourceIds는 한 번에 100개까지 전달할 수 있습니다."
            );
        }
        return List.copyOf(sourceIds);
    }

    private Long parsePositiveLong(String raw, String field) {
        if (raw == null) {
            return null;
        }
        try {
            long value = Long.parseLong(raw);
            if (value >= 1) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // The common validation response below is used for every bad value.
        }
        throw invalidInput(field, field + "는 1 이상의 숫자여야 합니다.");
    }

    private int parseSize(String raw) {
        if (raw == null) {
            return DEFAULT_SIZE;
        }
        try {
            int size = Integer.parseInt(raw);
            if (size >= 1 && size <= MAX_SIZE) {
                return size;
            }
        } catch (NumberFormatException ignored) {
            // The common validation response below is used for every bad value.
        }
        throw invalidInput(
                "size",
                "조회 개수는 1 이상 100 이하이어야 합니다."
        );
    }

    private BusinessException invalidInput(String field, String reason) {
        return new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                Map.of("field", field, "reason", reason)
        );
    }

    private IllegalStateException integrityFailure(Long reportId) {
        log.error("완료 리포트 근거 데이터 무결성 오류 reportId={}", reportId);
        return new IllegalStateException("Invalid report evidence list");
    }

    private record Query(
            String sourceType,
            String category,
            List<Long> sourceIds,
            Long cursor,
            int size
    ) {
    }

    private record ClaimDescriptor(
            String resultSection,
            String resultKey
    ) {
    }
}
