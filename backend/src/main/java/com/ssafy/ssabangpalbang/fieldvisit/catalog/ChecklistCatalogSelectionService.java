package com.ssafy.ssabangpalbang.fieldvisit.catalog;

import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiConnectionException;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiException;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiInvalidResponseException;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiSelectRequest;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiSelectResponse;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiServerErrorException;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiTimeoutException;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistSelectAiClient;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitAiProperties;
import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistPersonalizationInput;
import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistFallbackReason;
import com.ssafy.ssabangpalbang.fieldvisit.service.FallbackChecklistProvider;
import com.ssafy.ssabangpalbang.fieldvisit.service.GeneratedChecklistContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * feature flag true일 때 카탈로그 필터·점수·select·검증·fallback을 수행한다.
 *
 * <p>카탈로그 경로의 기술 실패는 사용자 API 500으로 전파하지 않고
 * select → catalog fallback → hardcoded fallback 순으로 전환한다.
 * {@link Error}는 잡지 않는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChecklistCatalogSelectionService {

    private final FieldVisitAiProperties properties;
    private final ChecklistCatalogLoader catalogLoader;
    private final ChecklistCatalogFilter catalogFilter;
    private final ChecklistCandidateScorer candidateScorer;
    private final ChecklistShortlistSelector shortlistSelector;
    private final ChecklistSelectAiClient selectAiClient;
    private final ChecklistSelectResponseValidator selectResponseValidator;
    private final ChecklistCatalogSnapshotAssembler snapshotAssembler;
    private final FallbackChecklistProvider hardcodedFallbackProvider;

    public GeneratedChecklistContent resolve(ChecklistPersonalizationInput input) {
        try {
            return resolveCatalogPath(input);
        } catch (RuntimeException exception) {
            log.warn(
                    "Catalog selection failed unexpectedly; using hardcoded fallback. cause={}",
                    exception.toString()
            );
            return hardcodedFallback(input, ChecklistFallbackReason.CATALOG_FALLBACK_FAILED);
        }
    }

    private GeneratedChecklistContent resolveCatalogPath(
            ChecklistPersonalizationInput input
    ) {
        Optional<ChecklistCatalog> catalogOpt = catalogLoader.get();
        if (catalogOpt.isEmpty()) {
            return hardcodedFallback(input, ChecklistFallbackReason.CATALOG_UNAVAILABLE);
        }
        ChecklistCatalog catalog = catalogOpt.get();

        ChecklistSelectionContext context;
        List<ChecklistCandidateScorer.ScoredCandidate> scored;
        List<ChecklistCandidateScorer.ScoredCandidate> shortlist;
        try {
            context = buildContext(input);
            List<ChecklistCatalogItem> filtered = catalogFilter.filter(catalog);
            if (filtered.size() < ChecklistSelectResponseValidator.MIN_ITEMS) {
                return hardcodedFallback(
                        input,
                        ChecklistFallbackReason.CATALOG_INSUFFICIENT_CANDIDATES
                );
            }
            scored = candidateScorer.score(filtered, context);
            shortlist = shortlistSelector.selectShortlist(scored, context);
        } catch (RuntimeException exception) {
            log.warn(
                    "Catalog filter/score/shortlist failed; using hardcoded fallback. cause={}",
                    exception.toString()
            );
            return hardcodedFallback(input, ChecklistFallbackReason.CATALOG_FALLBACK_FAILED);
        }

        if (shortlist.size() < ChecklistSelectResponseValidator.MIN_ITEMS) {
            return hardcodedFallback(
                    input,
                    ChecklistFallbackReason.CATALOG_INSUFFICIENT_CANDIDATES
            );
        }
        if (shortlist.size() < context.targetItemCount()) {
            log.warn(
                    "Shortlist size {} < target {}; skipping FastAPI select",
                    shortlist.size(),
                    context.targetItemCount()
            );
            return catalogFallback(
                    input,
                    catalog,
                    scored,
                    context,
                    ChecklistFallbackReason.CATALOG_INSUFFICIENT_CANDIDATES
            );
        }

        try {
            ChecklistAiSelectResponse response = selectAiClient.select(
                    toSelectRequest(context, shortlist)
            );
            ChecklistSelectResponseValidator.ValidationResult validation =
                    selectResponseValidator.validate(
                            response,
                            shortlist,
                            catalog,
                            context.targetItemCount()
                    );
            if (!validation.valid()) {
                return catalogFallback(input, catalog, scored, context, validation.reason());
            }
            ChecklistAiGenerateResponse snapshot = snapshotAssembler.toGenerateResponse(
                    response.itemCodes(),
                    catalog
            );
            return GeneratedChecklistContent.ai(snapshot);
        } catch (ChecklistAiConnectionException exception) {
            return catalogFallback(
                    input, catalog, scored, context,
                    ChecklistFallbackReason.AI_CONNECTION_FAILED, exception
            );
        } catch (ChecklistAiTimeoutException exception) {
            return catalogFallback(
                    input, catalog, scored, context,
                    ChecklistFallbackReason.AI_TIMEOUT, exception
            );
        } catch (ChecklistAiServerErrorException exception) {
            return catalogFallback(
                    input, catalog, scored, context,
                    ChecklistFallbackReason.AI_SERVER_ERROR, exception
            );
        } catch (ChecklistAiInvalidResponseException exception) {
            ChecklistFallbackReason reason = exception.getReason() == null
                    ? ChecklistFallbackReason.AI_INVALID_JSON
                    : exception.getReason();
            return catalogFallback(input, catalog, scored, context, reason, exception);
        } catch (ChecklistAiException | ChecklistCatalogException exception) {
            return catalogFallback(
                    input, catalog, scored, context,
                    ChecklistFallbackReason.AI_SERVER_ERROR, exception
            );
        } catch (RuntimeException exception) {
            return catalogFallback(
                    input, catalog, scored, context,
                    ChecklistFallbackReason.AI_INVALID_JSON, exception
            );
        }
    }

    ChecklistSelectionContext buildContext(ChecklistPersonalizationInput input) {
        List<String> mappedPriorities = new ArrayList<>();
        if (input.member() != null && input.member().priorities() != null) {
            for (String priority : input.member().priorities()) {
                ChecklistCodeMapper.toCatalogPriority(priority).ifPresent(mappedPriorities::add);
            }
        }
        String mappedPurpose = null;
        if (input.member() != null) {
            mappedPurpose = ChecklistCodeMapper.toCatalogPurpose(input.member().memberPurpose())
                    .orElse(null);
        }
        int shortlistSize = clamp(properties.catalogShortlistSize(), 30, 50);
        int target = clamp(
                properties.catalogTargetItemCount(),
                ChecklistSelectResponseValidator.MIN_ITEMS,
                ChecklistSelectResponseValidator.MAX_ITEMS
        );
        return new ChecklistSelectionContext(
                input,
                mappedPriorities,
                mappedPurpose,
                shortlistSize,
                target
        );
    }

    private ChecklistAiSelectRequest toSelectRequest(
            ChecklistSelectionContext context,
            List<ChecklistCandidateScorer.ScoredCandidate> shortlist
    ) {
        List<ChecklistAiSelectRequest.ShortlistItem> items = shortlist.stream()
                .map(scored -> new ChecklistAiSelectRequest.ShortlistItem(
                        scored.item().itemCode(),
                        scored.item().categoryCode(),
                        scored.item().title(),
                        scored.item().priorityTagsOrEmpty(),
                        scored.item().conditionTagsOrEmpty(),
                        scored.score(),
                        scored.item().isCommonCoreFlag()
                ))
                .toList();
        return new ChecklistAiSelectRequest(
                "v3-select-1",
                context.targetItemCount(),
                context.mappedPurpose(),
                context.selectedCatalogPriorities(),
                items
        );
    }

    private GeneratedChecklistContent catalogFallback(
            ChecklistPersonalizationInput input,
            ChecklistCatalog catalog,
            List<ChecklistCandidateScorer.ScoredCandidate> scored,
            ChecklistSelectionContext context,
            ChecklistFallbackReason reason
    ) {
        return catalogFallback(input, catalog, scored, context, reason, null);
    }

    private GeneratedChecklistContent catalogFallback(
            ChecklistPersonalizationInput input,
            ChecklistCatalog catalog,
            List<ChecklistCandidateScorer.ScoredCandidate> scored,
            ChecklistSelectionContext context,
            ChecklistFallbackReason reason,
            Exception exception
    ) {
        if (exception == null) {
            log.warn("Using catalog fallback checklist. reason={}", reason);
        } else {
            log.warn(
                    "Using catalog fallback checklist. reason={}, cause={}",
                    reason,
                    exception.toString()
            );
        }
        try {
            List<ChecklistCandidateScorer.ScoredCandidate> selected =
                    shortlistSelector.selectFinalFallback(scored, context);
            if (selected.size() < context.targetItemCount()
                    || selected.size() < ChecklistSelectResponseValidator.MIN_ITEMS) {
                return hardcodedFallback(
                        input,
                        ChecklistFallbackReason.CATALOG_INSUFFICIENT_CANDIDATES
                );
            }
            if (selected.size() > context.targetItemCount()) {
                selected = selected.subList(0, context.targetItemCount());
            }
            List<String> itemCodes = selected.stream()
                    .map(scoredCandidate -> scoredCandidate.item().itemCode())
                    .toList();
            ChecklistAiGenerateResponse snapshot =
                    snapshotAssembler.toGenerateResponse(itemCodes, catalog);
            return GeneratedChecklistContent.fallback(snapshot, reason);
        } catch (RuntimeException fallbackException) {
            log.warn(
                    "Catalog fallback failed; using hardcoded fallback. cause={}",
                    fallbackException.toString()
            );
            return hardcodedFallback(input, ChecklistFallbackReason.CATALOG_FALLBACK_FAILED);
        }
    }

    private GeneratedChecklistContent hardcodedFallback(
            ChecklistPersonalizationInput input,
            ChecklistFallbackReason reason
    ) {
        log.warn("Using hardcoded fallback checklist. reason={}", reason);
        return GeneratedChecklistContent.fallback(
                hardcodedFallbackProvider.create(input),
                reason
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
