package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecordSourceType;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportCompleteRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportEvidenceResultRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest;
import com.ssafy.ssabangpalbang.report.repository.ReportEvidenceWriteRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class ReportCompleteValidator {

    public List<ReportEvidenceWriteRepository.EvidenceRow> validate(
            ReportCompleteRequest request,
            ReportInputQueryRepository.Snapshot snapshot
    ) {
        ParticipantIndex participants = participantIndex(snapshot);
        ChecklistIndex checklists = checklistIndex(snapshot, participants);
        Map<Long, ReportInputQueryRepository.FieldRecordRow> usableRecords =
                usableRecords(snapshot, participants, checklists);

        validateMetrics(
                request.generationResult().metrics(),
                checklists.rows(),
                usableRecords.size()
        );
        List<ExpectedClaim> expectedClaims = validateGeneration(
                request.generationResult(),
                participants,
                checklists.categories()
        );
        return validateEvidence(
                request.evidenceResult(),
                expectedClaims,
                participants,
                checklists,
                usableRecords
        );
    }

    private ParticipantIndex participantIndex(
            ReportInputQueryRepository.Snapshot snapshot
    ) {
        Map<Long, String> memberToRef = new LinkedHashMap<>();
        Set<String> refs = new LinkedHashSet<>();
        int index = 1;
        for (ReportInputQueryRepository.ParticipantRow participant
                : snapshot.participants()) {
            String ref = "P" + index++;
            if (memberToRef.putIfAbsent(participant.memberId(), ref) != null) {
                invalid();
            }
            refs.add(ref);
        }
        return new ParticipantIndex(memberToRef, refs);
    }

    private ChecklistIndex checklistIndex(
            ReportInputQueryRepository.Snapshot snapshot,
            ParticipantIndex participants
    ) {
        Map<Long, ReportInputQueryRepository.ChecklistItemRow> byId =
                new LinkedHashMap<>();
        List<ReportInputQueryRepository.ChecklistItemRow> rows =
                new ArrayList<>();
        LinkedHashSet<String> categories = new LinkedHashSet<>();

        for (ReportInputQueryRepository.ChecklistItemRow row
                : snapshot.checklistItems()) {
            if (byId.putIfAbsent(row.checklistItemId(), row) != null) {
                invalid();
            }
            if (participants.memberToRef().containsKey(row.memberId())) {
                rows.add(row);
                categories.add(row.category());
            }
        }
        return new ChecklistIndex(byId, rows, List.copyOf(categories));
    }

    private Map<Long, ReportInputQueryRepository.FieldRecordRow> usableRecords(
            ReportInputQueryRepository.Snapshot snapshot,
            ParticipantIndex participants,
            ChecklistIndex checklists
    ) {
        Map<Long, ReportInputQueryRepository.FieldRecordRow> usable =
                new LinkedHashMap<>();
        Set<Long> seen = new HashSet<>();
        for (ReportInputQueryRepository.FieldRecordRow row
                : snapshot.fieldRecords()) {
            if (!seen.add(row.sourceId())) {
                invalid();
            }
            if (isUsable(row, participants, checklists)) {
                usable.put(row.sourceId(), row);
            }
        }
        return usable;
    }

    private boolean isUsable(
            ReportInputQueryRepository.FieldRecordRow row,
            ParticipantIndex participants,
            ChecklistIndex checklists
    ) {
        ReportInputQueryRepository.ChecklistItemRow checklist =
                checklists.byId().get(row.checklistItemId());
        if (row.deletedAt() != null
                || checklist == null
                || !participants.memberToRef().containsKey(row.authorId())
                || !Objects.equals(checklist.memberId(), row.authorId())) {
            return false;
        }

        return switch (row.sourceType()) {
            case TEXT -> hasText(row.textContent());
            case STT -> row.sttStatus() == SttStatus.DONE
                    && hasText(row.textContent());
            case PHOTO -> row.photoFile() != null
                    && row.photoFile().uploadStatus() == UploadStatus.COMPLETED
                    && row.photoFile().deletedAt() == null
                    && row.photoFile().contentType() != null
                    && row.photoFile().contentType()
                    .toLowerCase(java.util.Locale.ROOT)
                    .startsWith("image/");
        };
    }

    private void validateMetrics(
            ReportGenerationResultRequest.Metrics metrics,
            List<ReportInputQueryRepository.ChecklistItemRow> checklistRows,
            int fieldRecordCount
    ) {
        int total = checklistRows.size();
        int completed = (int) checklistRows.stream()
                .filter(ReportInputQueryRepository.ChecklistItemRow::completed)
                .count();
        double rate = total == 0
                ? 0.0
                : Math.round((completed * 1000.0) / total) / 10.0;

        if (metrics.completedChecklistItemCount()
                > metrics.totalChecklistItemCount()
                || metrics.totalChecklistItemCount() != total
                || metrics.completedChecklistItemCount() != completed
                || Double.compare(metrics.averageCompletionRate(), rate) != 0
                || metrics.fieldRecordCount() != fieldRecordCount) {
            invalid();
        }
    }

    private List<ExpectedClaim> validateGeneration(
            ReportGenerationResultRequest result,
            ParticipantIndex participants,
            List<String> expectedCategories
    ) {
        List<ExpectedClaim> expectedClaims = new ArrayList<>();
        validateFeatures(
                result.topPositiveFeatures(),
                ReportEvidenceResultRequest.ClaimType.FEATURE_POSITIVE,
                ReportGenerationResultRequest.OpinionType.POSITIVE,
                participants,
                expectedClaims
        );
        validateFeatures(
                result.topCautionFeatures(),
                ReportEvidenceResultRequest.ClaimType.FEATURE_CAUTION,
                ReportGenerationResultRequest.OpinionType.CAUTION,
                participants,
                expectedClaims
        );

        Set<String> categorySet = Set.copyOf(expectedCategories);
        for (ReportGenerationResultRequest.CommonOpinion opinion
                : result.commonOpinions()) {
            requireCategory(opinion.category(), categorySet);
            requireUniqueAllowed(opinion.participantRefs(), participants.refs());
            if (opinion.participantCount() != opinion.participantRefs().size()) {
                invalid();
            }
            expectedClaims.add(new ExpectedClaim(
                    ReportEvidenceResultRequest.ClaimType.COMMON,
                    opinion.category(),
                    opinion.label(),
                    opinion.opinionType(),
                    opinion.participantRefs(),
                    Set.of(),
                    Set.of()
            ));
        }

        for (ReportGenerationResultRequest.ConflictingOpinion opinion
                : result.conflictingOpinions()) {
            requireCategory(opinion.category(), categorySet);
            requireUniqueAllowed(
                    opinion.positiveParticipantRefs(),
                    participants.refs()
            );
            requireUniqueAllowed(
                    opinion.cautionParticipantRefs(),
                    participants.refs()
            );
            Set<String> positive = Set.copyOf(
                    opinion.positiveParticipantRefs()
            );
            Set<String> caution = Set.copyOf(
                    opinion.cautionParticipantRefs()
            );
            Set<String> combinedSet = new LinkedHashSet<>(positive);
            combinedSet.addAll(caution);
            if (opinion.positiveParticipantCount() != positive.size()
                    || opinion.cautionParticipantCount() != caution.size()
                    || combinedSet.size() < 2) {
                invalid();
            }
            List<String> combined = new ArrayList<>(
                    opinion.positiveParticipantRefs()
            );
            for (String ref : opinion.cautionParticipantRefs()) {
                if (!combined.contains(ref)) {
                    combined.add(ref);
                }
            }
            expectedClaims.add(new ExpectedClaim(
                    ReportEvidenceResultRequest.ClaimType.CONFLICT,
                    opinion.category(),
                    opinion.label(),
                    null,
                    List.copyOf(combined),
                    positive,
                    caution
            ));
        }

        if (result.categories().size() != expectedCategories.size()) {
            invalid();
        }
        for (int index = 0; index < result.categories().size(); index++) {
            ReportGenerationResultRequest.Category category =
                    result.categories().get(index);
            if (!Objects.equals(
                    category.category(),
                    expectedCategories.get(index)
            )) {
                invalid();
            }
            validateCategory(category, participants, expectedClaims);
        }
        return expectedClaims;
    }

    private void validateFeatures(
            List<ReportGenerationResultRequest.Feature> features,
            ReportEvidenceResultRequest.ClaimType claimType,
            ReportGenerationResultRequest.OpinionType opinionType,
            ParticipantIndex participants,
            List<ExpectedClaim> expectedClaims
    ) {
        for (int index = 0; index < features.size(); index++) {
            ReportGenerationResultRequest.Feature feature = features.get(index);
            requireUniqueAllowed(feature.participantRefs(), participants.refs());
            if (feature.rank() != index + 1
                    || feature.mentionCount()
                    != feature.participantRefs().size()) {
                invalid();
            }
            expectedClaims.add(new ExpectedClaim(
                    claimType,
                    null,
                    feature.label(),
                    opinionType,
                    feature.participantRefs(),
                    Set.of(),
                    Set.of()
            ));
        }
    }

    private void validateCategory(
            ReportGenerationResultRequest.Category category,
            ParticipantIndex participants,
            List<ExpectedClaim> expectedClaims
    ) {
        Set<String> positive = new HashSet<>();
        Set<String> caution = new HashSet<>();
        Set<String> uniqueOpinions = new HashSet<>();

        for (ReportGenerationResultRequest.ParticipantOpinion opinion
                : category.participantOpinions()) {
            if (!participants.refs().contains(opinion.participantRef())
                    || !Objects.equals(
                    opinion.participantLabel(),
                    participantLabel(opinion.participantRef())
            )) {
                invalid();
            }
            String uniqueKey = opinion.participantRef()
                    + ':' + opinion.opinionType();
            if (!uniqueOpinions.add(uniqueKey)) {
                invalid();
            }
            if (opinion.opinionType()
                    == ReportGenerationResultRequest.OpinionType.POSITIVE) {
                positive.add(opinion.participantRef());
            } else {
                caution.add(opinion.participantRef());
            }
            if (category.dataSufficient()) {
                expectedClaims.add(new ExpectedClaim(
                        ReportEvidenceResultRequest.ClaimType
                                .PARTICIPANT_OPINION,
                        category.category(),
                        null,
                        opinion.opinionType(),
                        List.of(opinion.participantRef()),
                        Set.of(),
                        Set.of()
                ));
            }
        }
        if (category.positiveOpinionCount() != positive.size()
                || category.cautionOpinionCount() != caution.size()) {
            invalid();
        }
    }

    private List<ReportEvidenceWriteRepository.EvidenceRow> validateEvidence(
            ReportEvidenceResultRequest result,
            List<ExpectedClaim> expectedClaims,
            ParticipantIndex participants,
            ChecklistIndex checklists,
            Map<Long, ReportInputQueryRepository.FieldRecordRow> usableRecords
    ) {
        if (result.claims().size() != expectedClaims.size()) {
            invalid();
        }
        Set<String> claimKeys = new HashSet<>();
        Map<String, ReportEvidenceWriteRepository.EvidenceRow> uniqueRows =
                new LinkedHashMap<>();

        for (int index = 0; index < result.claims().size(); index++) {
            ReportEvidenceResultRequest.Claim claim = result.claims().get(index);
            if (claim.displayOrder() != index + 1
                    || !claimKeys.add(claim.claimKey())
                    || isBlankOptional(claim.category())
                    || isBlankOptional(claim.label())) {
                invalid();
            }
            requireUniqueAllowed(claim.participantRefs(), participants.refs());
            ExpectedClaim expected = expectedClaims.get(index);
            if (!expected.matches(claim)) {
                invalid();
            }
            validateRoles(claim, expected);

            for (ReportEvidenceResultRequest.Evidence evidence
                    : claim.evidences()) {
                ReportInputQueryRepository.FieldRecordRow record =
                        usableRecords.get(evidence.sourceId());
                if (!isPersistableTextEvidence(record, evidence)) {
                    continue;
                }
                validateEvidenceMetadata(
                        evidence,
                        record,
                        claim,
                        expected,
                        participants,
                        checklists
                );
                String key = claim.claimKey() + ':' + evidence.sourceId();
                uniqueRows.putIfAbsent(
                        key,
                        new ReportEvidenceWriteRepository.EvidenceRow(
                                evidence.sourceId(),
                                claim.claimKey(),
                                claim.displayOrder()
                        )
                );
            }
        }
        return List.copyOf(uniqueRows.values());
    }

    private void validateRoles(
            ReportEvidenceResultRequest.Claim claim,
            ExpectedClaim expected
    ) {
        Set<ReportEvidenceResultRequest.EvidenceRole> roles = new HashSet<>();
        claim.evidences().forEach(item -> roles.add(item.evidenceRole()));
        if (claim.claimType() == ReportEvidenceResultRequest.ClaimType.CONFLICT) {
            if (roles.contains(ReportEvidenceResultRequest.EvidenceRole.SUPPORT)
                    || !roles.contains(
                    ReportEvidenceResultRequest.EvidenceRole.SUPPORT_POSITIVE
            )
                    || !roles.contains(
                    ReportEvidenceResultRequest.EvidenceRole.SUPPORT_CAUTION
            )) {
                invalid();
            }
            return;
        }
        if (!roles.equals(Set.of(
                ReportEvidenceResultRequest.EvidenceRole.SUPPORT
        ))) {
            invalid();
        }
    }

    private boolean isPersistableTextEvidence(
            ReportInputQueryRepository.FieldRecordRow record,
            ReportEvidenceResultRequest.Evidence evidence
    ) {
        if (record == null || record.deletedAt() != null) {
            return false;
        }
        if (record.sourceType() == FieldRecordSourceType.TEXT) {
            return evidence.sourceType()
                    == ReportEvidenceResultRequest.SourceType.TEXT
                    && hasText(record.textContent());
        }
        if (record.sourceType() == FieldRecordSourceType.STT) {
            return evidence.sourceType()
                    == ReportEvidenceResultRequest.SourceType.STT
                    && record.sttStatus() == SttStatus.DONE
                    && hasText(record.textContent());
        }
        return false;
    }

    private void validateEvidenceMetadata(
            ReportEvidenceResultRequest.Evidence evidence,
            ReportInputQueryRepository.FieldRecordRow record,
            ReportEvidenceResultRequest.Claim claim,
            ExpectedClaim expected,
            ParticipantIndex participants,
            ChecklistIndex checklists
    ) {
        ReportInputQueryRepository.ChecklistItemRow checklist =
                checklists.byId().get(record.checklistItemId());
        String participantRef = participants.memberToRef().get(record.authorId());
        if (checklist == null
                || participantRef == null
                || !Objects.equals(evidence.participantRef(), participantRef)
                || !Objects.equals(
                evidence.checklistItemId(),
                record.checklistItemId()
        )
                || !Objects.equals(evidence.category(), checklist.category())
                || !sameInstant(evidence.recordedAt(), record.recordedAt())
                || !claim.participantRefs().contains(participantRef)) {
            invalid();
        }

        if (claim.claimType() == ReportEvidenceResultRequest.ClaimType.CONFLICT) {
            boolean validSide = switch (evidence.evidenceRole()) {
                case SUPPORT_POSITIVE -> expected.positiveRefs()
                        .contains(participantRef);
                case SUPPORT_CAUTION -> expected.cautionRefs()
                        .contains(participantRef);
                case SUPPORT -> false;
            };
            if (!validSide) {
                invalid();
            }
        }
    }

    private void requireUniqueAllowed(
            List<String> refs,
            Set<String> allowed
    ) {
        if (refs.size() != new HashSet<>(refs).size()
                || !allowed.containsAll(refs)) {
            invalid();
        }
    }

    private void requireCategory(String category, Set<String> allowed) {
        if (!allowed.contains(category)) {
            invalid();
        }
    }

    private boolean sameInstant(OffsetDateTime first, OffsetDateTime second) {
        return first != null
                && second != null
                && first.toInstant().equals(second.toInstant());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isBlankOptional(String value) {
        return value != null && value.isBlank();
    }

    private String participantLabel(String participantRef) {
        return "참여자 " + participantRef.substring(1);
    }

    private void invalid() {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }

    private record ParticipantIndex(
            Map<Long, String> memberToRef,
            Set<String> refs
    ) {
    }

    private record ChecklistIndex(
            Map<Long, ReportInputQueryRepository.ChecklistItemRow> byId,
            List<ReportInputQueryRepository.ChecklistItemRow> rows,
            List<String> categories
    ) {
    }

    private record ExpectedClaim(
            ReportEvidenceResultRequest.ClaimType claimType,
            String category,
            String label,
            ReportGenerationResultRequest.OpinionType opinionType,
            List<String> participantRefs,
            Set<String> positiveRefs,
            Set<String> cautionRefs
    ) {

        boolean matches(ReportEvidenceResultRequest.Claim claim) {
            return claim.claimType() == claimType
                    && Objects.equals(claim.category(), category)
                    && Objects.equals(claim.label(), label)
                    && claim.opinionType() == opinionType
                    && Objects.equals(claim.participantRefs(), participantRefs);
        }
    }
}
