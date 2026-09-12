package com.ssafy.ssabangpalbang.fieldvisit.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiInvalidResponseException;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiSelectResponse;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiTimeoutException;
import com.ssafy.ssabangpalbang.fieldvisit.client.FakeChecklistSelectAiClient;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitAiProperties;
import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistPersonalizationInput;
import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistFallbackReason;
import com.ssafy.ssabangpalbang.fieldvisit.service.FallbackChecklistProvider;
import com.ssafy.ssabangpalbang.fieldvisit.service.GeneratedChecklistContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChecklistCatalogSelectionUnitTest {

    // AI-002: 카탈로그 300개 항목에 example 필드를 추가하면서 해시 갱신.
    private static final String EXPECTED_SHA256 =
            "29AAFFF27D8B2B8ED5E5480195CA143830BEE55ACFA2B0A0E3066BA365DB92D4";
    private static final String CATALOG_CLASSPATH =
            "classpath:data/checklist/ssabang_field_visit_checklist_raw_v3_300.json";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private ChecklistCatalog catalog;
    private ChecklistCatalogLoader loader;
    private ChecklistCatalogFilter filter;
    private ChecklistCandidateScorer scorer;
    private ChecklistShortlistSelector shortlistSelector;
    private ChecklistSelectResponseValidator selectValidator;
    private ChecklistCatalogSnapshotAssembler assembler;
    private FieldVisitAiProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FieldVisitAiProperties();
        properties.setCatalogSelectionEnabled(true);
        properties.setCatalogShortlistSize(50);
        properties.setCatalogTargetItemCount(25);
        loader = new ChecklistCatalogLoader(
                objectMapper,
                new DefaultResourceLoader(),
                new ChecklistCatalogValidator(),
                properties
        );
        catalog = loader.loadOrThrow();
        filter = new ChecklistCatalogFilter();
        scorer = new ChecklistCandidateScorer();
        shortlistSelector = new ChecklistShortlistSelector();
        selectValidator = new ChecklistSelectResponseValidator();
        assembler = new ChecklistCatalogSnapshotAssembler();
    }

    @Test
    void JSON_파싱과_기본_검증에_성공한다() throws Exception {
        assertThat(catalog.version()).isEqualTo("3.0.0");
        assertThat(catalog.size()).isEqualTo(300);
        assertThat(catalog.categoryCount()).isEqualTo(14);
        assertThat(catalog.byItemCode()).hasSize(300);

        byte[] bytes = new DefaultResourceLoader()
                .getResource(properties.catalogClasspath())
                .getInputStream()
                .readAllBytes();
        String sha = HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)
        );
        assertThat(sha).isEqualTo(EXPECTED_SHA256);
    }

    @Test
    void itemCode_중복을_거부한다() throws Exception {
        ChecklistCatalogDocument document = loadMutableDocument();
        ChecklistCatalogItem first = document.items().get(0);
        ChecklistCatalogItem second = document.items().get(1);
        List<ChecklistCatalogItem> mutated = new ArrayList<>(document.items());
        mutated.set(1, copyItemWithCode(second, first.itemCode()));
        ChecklistCatalogDocument invalid = copyDocument(document, mutated);

        assertThatThrownBy(() -> new ChecklistCatalogValidator().validate(invalid))
                .isInstanceOf(ChecklistCatalogException.class)
                .hasMessageContaining("duplicate itemCode");
    }

    @Test
    void 잘못된_answerType_enum을_거부한다() throws Exception {
        ChecklistCatalogDocument document = loadMutableDocument();
        ChecklistCatalogItem first = document.items().get(0);
        List<ChecklistCatalogItem> mutated = new ArrayList<>(document.items());
        mutated.set(0, copyItemWithAnswerType(first, "UNKNOWN_ENUM"));
        ChecklistCatalogDocument invalid = copyDocument(document, mutated);

        assertThatThrownBy(() -> new ChecklistCatalogValidator().validate(invalid))
                .isInstanceOf(ChecklistCatalogException.class)
                .hasMessageContaining("unknown answerType");
    }

    @Test
    void 알_수_없는_priorityTag를_거부한다() throws Exception {
        ChecklistCatalogDocument document = loadMutableDocument();
        ChecklistCatalogItem first = document.items().get(0);
        List<ChecklistCatalogItem> mutated = new ArrayList<>(document.items());
        mutated.set(0, copyItemWithPriorityTags(first, List.of("NOT_A_PRIORITY")));
        ChecklistCatalogDocument invalid = copyDocument(document, mutated);

        assertThatThrownBy(() -> new ChecklistCatalogValidator().validate(invalid))
                .isInstanceOf(ChecklistCatalogException.class)
                .hasMessageContaining("unknown priorityTag");
    }

    @Test
    void 우선순위와_목적_코드를_매핑한다() {
        assertThat(ChecklistCodeMapper.toCatalogPriority("TRANSPORT"))
                .contains("TRANSPORTATION");
        assertThat(ChecklistCodeMapper.toCatalogPriority("COMMERCIAL"))
                .contains("CONVENIENCE");
        assertThat(ChecklistCodeMapper.toCatalogPriority("UNKNOWN")).isEmpty();
        assertThat(ChecklistCodeMapper.toCatalogPurpose("RESIDENCE")).contains("LIVE");
        assertThat(ChecklistCodeMapper.toCatalogPurpose("INVESTMENT")).contains("INVEST");
        assertThat(ChecklistCodeMapper.toCatalogPurpose("STUDY")).contains("LEARN");
    }

    @Test
    void relevanceWeight_정규화가_고정된다() {
        assertThat(ChecklistCandidateScorer.normalizeRelevanceWeight(45)).isEqualTo(0);
        assertThat(ChecklistCandidateScorer.normalizeRelevanceWeight(100)).isEqualTo(20);
        assertThat(ChecklistCandidateScorer.normalizeRelevanceWeight(72)).isEqualTo(10);
        assertThat(ChecklistCandidateScorer.normalizeRelevanceWeight(0)).isEqualTo(0);
        assertThat(ChecklistCandidateScorer.normalizeRelevanceWeight(200)).isEqualTo(20);
    }

    @Test
    void 선택_우선순위_boost를_가산한다() {
        ChecklistCatalogItem item = catalog.items().stream()
                .filter(candidate -> candidate.priorityMappingsOrEmpty().stream()
                        .anyMatch(mapping -> "TRANSPORTATION".equals(mapping.priorityCode())))
                .findFirst()
                .orElseThrow();
        ChecklistSelectionContext empty = contextWith(
                List.of(),
                null,
                false,
                false
        );
        ChecklistSelectionContext selected = contextWith(
                List.of("TRANSPORTATION"),
                null,
                false,
                false
        );
        int baseOnly = scorer.scoreItem(item, Set.of(), empty);
        int boosted = scorer.scoreItem(item, Set.of("TRANSPORTATION"), selected);
        assertThat(boosted).isGreaterThan(baseOnly);
        assertThat(boosted - baseOnly)
                .isGreaterThanOrEqualTo(ChecklistCandidateScorer.SELECTED_PRIORITY_BOOST);
    }

    @Test
    void hasVehicle_boost를_가산한다() {
        ChecklistCatalogItem item = catalog.items().stream()
                .filter(candidate -> candidate.conditionTagsOrEmpty().contains("CAR"))
                .findFirst()
                .orElseThrow();
        int without = scorer.scoreItem(
                item,
                Set.of(),
                contextWith(List.of(), null, false, false)
        );
        int with = scorer.scoreItem(
                item,
                Set.of(),
                contextWith(List.of(), null, true, false)
        );
        assertThat(with - without).isEqualTo(ChecklistCandidateScorer.VEHICLE_BOOST);
    }

    @Test
    void hasChildren_boost를_가산한다() {
        ChecklistCatalogItem item = catalog.items().stream()
                .filter(candidate -> candidate.conditionTagsOrEmpty().contains("CHILD"))
                .findFirst()
                .orElseThrow();
        int without = scorer.scoreItem(
                item,
                Set.of(),
                contextWith(List.of(), null, false, false)
        );
        int with = scorer.scoreItem(
                item,
                Set.of(),
                contextWith(List.of(), null, false, true)
        );
        assertThat(with - without).isEqualTo(ChecklistCandidateScorer.CHILDREN_BOOST);
    }

    @Test
    void purpose_boost를_가산한다() {
        ChecklistCatalogItem item = catalog.items().stream()
                .filter(candidate -> candidate.conditionTagsOrEmpty().contains("LIVE"))
                .findFirst()
                .orElseThrow();
        int without = scorer.scoreItem(
                item,
                Set.of(),
                contextWith(List.of(), null, false, false)
        );
        int with = scorer.scoreItem(
                item,
                Set.of(),
                contextWith(List.of(), "LIVE", false, false)
        );
        assertThat(with - without).isEqualTo(ChecklistCandidateScorer.PURPOSE_BOOST);
    }

    @Test
    void commonCore_boost를_가산한다() {
        ChecklistCatalogItem item = catalog.items().stream()
                .filter(ChecklistCatalogItem::isCommonCoreFlag)
                .findFirst()
                .orElseThrow();
        int score = scorer.scoreItem(
                item,
                Set.of(),
                contextWith(List.of(), null, false, false)
        );
        int base = item.baseWeight() == null ? 0 : item.baseWeight();
        assertThat(score - base).isEqualTo(ChecklistCandidateScorer.COMMON_CORE_BOOST);
    }

    @Test
    void 조건이_없으면_baseWeight만_유지한다() {
        ChecklistCatalogItem item = catalog.items().stream()
                .filter(candidate -> !candidate.isCommonCoreFlag())
                .filter(candidate -> candidate.priorityMappingsOrEmpty().isEmpty()
                        || candidate.priorityMappingsOrEmpty().stream().noneMatch(
                        mapping -> "TRANSPORTATION".equals(mapping.priorityCode())))
                .filter(candidate -> candidate.conditionTagsOrEmpty().stream().noneMatch(
                        tag -> Set.of("CAR", "PARKING", "CHILD", "INFANT", "TEEN", "LIVE")
                                .contains(tag)))
                .findFirst()
                .orElseThrow();
        int score = scorer.scoreItem(
                item,
                Set.of(),
                contextWith(List.of(), null, false, false)
        );
        assertThat(score).isEqualTo(item.baseWeight() == null ? 0 : item.baseWeight());
    }

    @Test
    void 하드_필터가_active_access_ANY만_남긴다() {
        List<ChecklistCatalogItem> filtered = filter.filter(catalog);
        assertThat(filtered).isNotEmpty();
        assertThat(filtered).allSatisfy(item -> {
            assertThat(item.isActiveFlag()).isTrue();
            assertThat(ChecklistCatalogFilter.ALLOWED_ACCESS_LEVELS)
                    .contains(item.accessLevel());
            assertThat(item.visitConditionsOrEmpty()).contains("ANY");
        });
    }

    @Test
    void 점수와_shortlist가_결정적이고_범위를_지킨다() {
        ChecklistSelectionContext context = sampleContext();
        List<ChecklistCatalogItem> filtered = filter.filter(catalog);
        List<ChecklistCandidateScorer.ScoredCandidate> scored1 =
                scorer.score(filtered, context);
        List<ChecklistCandidateScorer.ScoredCandidate> scored2 =
                scorer.score(filtered, context);
        assertThat(scored1).isEqualTo(scored2);

        List<ChecklistCandidateScorer.ScoredCandidate> shortlist1 =
                shortlistSelector.selectShortlist(scored1, context);
        List<ChecklistCandidateScorer.ScoredCandidate> shortlist2 =
                shortlistSelector.selectShortlist(scored2, context);
        assertThat(shortlist1).hasSizeBetween(30, 50);
        assertThat(shortlist1).isEqualTo(shortlist2);
        assertThat(shortlist1.stream().map(s -> s.item().itemCode()).collect(Collectors.toSet()))
                .hasSize(shortlist1.size());
        assertThat(shortlist1.stream().anyMatch(s -> s.item().isCommonCoreFlag())).isTrue();
        assertThat(shortlist1.stream()
                .anyMatch(s -> "SUM".equals(s.item().categoryCode()))).isTrue();
    }

    @Test
    void select_정상_응답은_commonCore와_SUM을_모두_요구한다() {
        List<ChecklistCandidateScorer.ScoredCandidate> shortlist = sampleShortlist();
        List<String> valid = validItemsIncludingCoreAndSum(shortlist);

        assertThat(selectValidator.validate(
                new ChecklistAiSelectResponse(valid),
                shortlist,
                catalog,
                25
        ).valid()).isTrue();
    }

    @Test
    void select_검증이_후보외_코드를_거부한다() {
        List<ChecklistCandidateScorer.ScoredCandidate> shortlist = sampleShortlist();
        List<String> codes = new ArrayList<>(validItemsIncludingCoreAndSum(shortlist));
        codes.set(codes.size() - 1, "NOT_IN_SHORTLIST");

        assertThat(selectValidator.validate(
                new ChecklistAiSelectResponse(codes),
                shortlist,
                catalog,
                25
        ).valid()).isFalse();
    }

    @Test
    void select_검증이_중복_코드를_거부한다() {
        List<ChecklistCandidateScorer.ScoredCandidate> shortlist = sampleShortlist();
        List<String> codes = new ArrayList<>(validItemsIncludingCoreAndSum(shortlist));
        codes.set(codes.size() - 1, codes.get(0));

        assertThat(selectValidator.validate(
                new ChecklistAiSelectResponse(codes),
                shortlist,
                catalog,
                25
        ).valid()).isFalse();
    }

    @Test
    void select_검증이_개수_위반을_거부한다() {
        List<ChecklistCandidateScorer.ScoredCandidate> shortlist = sampleShortlist();
        List<String> seven = shortlist.stream()
                .map(s -> s.item().itemCode())
                .limit(7)
                .toList();
        List<String> nineteen = shortlist.stream()
                .map(s -> s.item().itemCode())
                .limit(19)
                .toList();

        assertThat(selectValidator.validate(
                new ChecklistAiSelectResponse(seven),
                shortlist,
                catalog,
                7
        ).valid()).isFalse();
        assertThat(selectValidator.validate(
                new ChecklistAiSelectResponse(nineteen),
                shortlist,
                catalog,
                19
        ).valid()).isFalse();
    }

    @Test
    void select_검증이_commonCore_누락을_거부한다() {
        List<ChecklistCandidateScorer.ScoredCandidate> shortlist = sampleShortlist();
        // SUM 중 일부는 isCommonCore=true이므로, 비공통 SUM만 사용한다.
        String sumCode = shortlist.stream()
                .filter(s -> "SUM".equals(s.item().categoryCode()))
                .filter(s -> !s.item().isCommonCoreFlag())
                .map(s -> s.item().itemCode())
                .findFirst()
                .orElseGet(() -> {
                    ChecklistCatalogItem sum003 = catalog.findByItemCode("SUM_003").orElseThrow();
                    assertThat(sum003.isCommonCoreFlag()).isFalse();
                    return sum003.itemCode();
                });
        List<ChecklistCandidateScorer.ScoredCandidate> effectiveShortlist =
                ensureCodeInShortlist(shortlist, sumCode);

        List<String> withoutCore = effectiveShortlist.stream()
                .filter(s -> !s.item().isCommonCoreFlag())
                .map(s -> s.item().itemCode())
                .filter(code -> !code.equals(sumCode))
                .limit(24)
                .collect(Collectors.toCollection(ArrayList::new));
        withoutCore.add(0, sumCode);
        assertThat(withoutCore).hasSize(25);
        assertThat(withoutCore).noneMatch(code ->
                catalog.findByItemCode(code).orElseThrow().isCommonCoreFlag());
        assertThat(withoutCore).anyMatch(code ->
                "SUM".equals(catalog.findByItemCode(code).orElseThrow().categoryCode()));

        assertThat(selectValidator.validate(
                new ChecklistAiSelectResponse(withoutCore),
                effectiveShortlist,
                catalog,
                25
        ).valid()).isFalse();
    }

    @Test
    void select_검증이_SUM_누락을_거부한다() {
        List<ChecklistCandidateScorer.ScoredCandidate> shortlist = sampleShortlist();
        List<String> withoutSum = shortlist.stream()
                .filter(s -> !"SUM".equals(s.item().categoryCode()))
                .map(s -> s.item().itemCode())
                .limit(24)
                .collect(Collectors.toCollection(ArrayList::new));
        String coreCode = shortlist.stream()
                .filter(s -> s.item().isCommonCoreFlag())
                .map(s -> s.item().itemCode())
                .findFirst()
                .orElseThrow();
        if (!withoutSum.contains(coreCode)) {
            withoutSum.set(0, coreCode);
        }
        while (withoutSum.size() < 25) {
            shortlist.stream()
                    .map(s -> s.item().itemCode())
                    .filter(code -> !withoutSum.contains(code))
                    .filter(code -> !"SUM".equals(
                            catalog.findByItemCode(code).orElseThrow().categoryCode()))
                    .findFirst()
                    .ifPresent(withoutSum::add);
        }

        assertThat(selectValidator.validate(
                new ChecklistAiSelectResponse(withoutSum.subList(0, 25)),
                shortlist,
                catalog,
                25
        ).valid()).isFalse();
    }

    @Test
    void 카탈로그_fallback이_20에서_30개를_결정적으로_고른다() {
        ChecklistSelectionContext context = sampleContext();
        List<ChecklistCandidateScorer.ScoredCandidate> scored =
                scorer.score(filter.filter(catalog), context);
        List<ChecklistCandidateScorer.ScoredCandidate> selected1 =
                shortlistSelector.selectFinalFallback(scored, context);
        List<ChecklistCandidateScorer.ScoredCandidate> selected2 =
                shortlistSelector.selectFinalFallback(scored, context);
        assertThat(selected1).hasSizeBetween(20, 30);
        assertThat(selected1).isEqualTo(selected2);
    }

    @Test
    void select_타임아웃이면_카탈로그_fallback을_사용한다() {
        FakeChecklistSelectAiClient selectClient = FakeChecklistSelectAiClient.throwing(
                new ChecklistAiTimeoutException("timeout")
        );
        ChecklistCatalogSelectionService service = newService(selectClient, assembler);

        GeneratedChecklistContent content = service.resolve(sampleInput());
        assertThat(content.fallback()).isTrue();
        assertThat(content.fallbackReason()).isEqualTo(ChecklistFallbackReason.AI_TIMEOUT);
        assertThat(content.response().items()).hasSizeBetween(20, 30);
        assertThat(selectClient.getCallCount()).isEqualTo(1);
    }

    @Test
    void 잘못된_FastAPI_JSON이면_카탈로그_fallback으로_전환한다() {
        FakeChecklistSelectAiClient selectClient = FakeChecklistSelectAiClient.throwing(
                new ChecklistAiInvalidResponseException(
                        ChecklistFallbackReason.AI_INVALID_JSON,
                        "bad json"
                )
        );
        ChecklistCatalogSelectionService service = newService(selectClient, assembler);

        GeneratedChecklistContent content = service.resolve(sampleInput());
        assertThat(content.fallback()).isTrue();
        assertThat(content.fallbackReason()).isEqualTo(ChecklistFallbackReason.AI_INVALID_JSON);
        assertThat(content.response().items()).hasSizeBetween(20, 30);
    }

    @Test
    void shortlist가_target보다_작으면_select를_호출하지_않는다() {
        FakeChecklistSelectAiClient selectClient = FakeChecklistSelectAiClient.returning(
                new ChecklistAiSelectResponse(List.of())
        );
        ChecklistShortlistSelector limitedSelector = new ChecklistShortlistSelector() {
            @Override
            public List<ChecklistCandidateScorer.ScoredCandidate> selectShortlist(
                    List<ChecklistCandidateScorer.ScoredCandidate> scored,
                    ChecklistSelectionContext context
            ) {
                return super.selectShortlist(scored, context).subList(0, 22);
            }
        };
        ChecklistCatalogSelectionService service = new ChecklistCatalogSelectionService(
                properties,
                loader,
                filter,
                scorer,
                limitedSelector,
                selectClient,
                selectValidator,
                assembler,
                new FallbackChecklistProvider()
        );

        GeneratedChecklistContent content = service.resolve(sampleInput());
        assertThat(selectClient.getCallCount()).isZero();
        assertThat(content.fallback()).isTrue();
        assertThat(content.response().items()).hasSizeBetween(20, 30);
    }

    @Test
    void 카탈로그_fallback_실패면_하드코딩_fallback으로_전환한다() {
        FakeChecklistSelectAiClient selectClient = FakeChecklistSelectAiClient.throwing(
                new ChecklistAiTimeoutException("timeout")
        );
        ChecklistCatalogSnapshotAssembler failingAssembler =
                new ChecklistCatalogSnapshotAssembler() {
                    @Override
                    public com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiGenerateResponse
                    toGenerateResponse(List<String> itemCodes, ChecklistCatalog source) {
                        throw new ChecklistCatalogException("snapshot failed");
                    }
                };
        ChecklistCatalogSelectionService service = newService(selectClient, failingAssembler);

        GeneratedChecklistContent content = service.resolve(sampleInput());
        assertThat(content.fallback()).isTrue();
        assertThat(content.fallbackReason())
                .isEqualTo(ChecklistFallbackReason.CATALOG_FALLBACK_FAILED);
        assertThat(content.response().items()).hasSize(25);
    }

    @Test
    void 카탈로그_로드_실패면_하드코딩_fallback으로_전환한다() {
        FieldVisitAiProperties bad = new FieldVisitAiProperties();
        bad.setCatalogClasspath("classpath:data/checklist/does-not-exist.json");
        ChecklistCatalogLoader badLoader = new ChecklistCatalogLoader(
                objectMapper,
                new DefaultResourceLoader(),
                new ChecklistCatalogValidator(),
                bad
        );
        FakeChecklistSelectAiClient selectClient = FakeChecklistSelectAiClient.returning(
                new ChecklistAiSelectResponse(List.of())
        );
        ChecklistCatalogSelectionService service = new ChecklistCatalogSelectionService(
                bad,
                badLoader,
                filter,
                scorer,
                shortlistSelector,
                selectClient,
                selectValidator,
                assembler,
                new FallbackChecklistProvider()
        );

        GeneratedChecklistContent content = service.resolve(sampleInput());
        assertThat(content.fallback()).isTrue();
        assertThat(content.fallbackReason())
                .isEqualTo(ChecklistFallbackReason.CATALOG_UNAVAILABLE);
        assertThat(content.response().items()).hasSize(25);
        assertThat(selectClient.getCallCount()).isZero();
    }

    private ChecklistCatalogSelectionService newService(
            FakeChecklistSelectAiClient selectClient,
            ChecklistCatalogSnapshotAssembler snapshotAssembler
    ) {
        return new ChecklistCatalogSelectionService(
                properties,
                loader,
                filter,
                scorer,
                shortlistSelector,
                selectClient,
                selectValidator,
                snapshotAssembler,
                new FallbackChecklistProvider()
        );
    }

    private List<ChecklistCandidateScorer.ScoredCandidate> sampleShortlist() {
        ChecklistSelectionContext context = sampleContext();
        return shortlistSelector.selectShortlist(
                scorer.score(filter.filter(catalog), context),
                context
        );
    }

    private List<ChecklistCandidateScorer.ScoredCandidate> ensureCodeInShortlist(
            List<ChecklistCandidateScorer.ScoredCandidate> shortlist,
            String itemCode
    ) {
        if (shortlist.stream().anyMatch(s -> itemCode.equals(s.item().itemCode()))) {
            return shortlist;
        }
        ChecklistCatalogItem item = catalog.findByItemCode(itemCode).orElseThrow();
        List<ChecklistCandidateScorer.ScoredCandidate> copy = new ArrayList<>(shortlist);
        copy.set(copy.size() - 1, new ChecklistCandidateScorer.ScoredCandidate(item, 1));
        return copy;
    }

    private static final int VALID_TARGET = 25;

    private List<String> validItemsIncludingCoreAndSum(
            List<ChecklistCandidateScorer.ScoredCandidate> shortlist
    ) {
        String core = shortlist.stream()
                .filter(s -> s.item().isCommonCoreFlag())
                .map(s -> s.item().itemCode())
                .findFirst()
                .orElseThrow();
        String sum = shortlist.stream()
                .filter(s -> "SUM".equals(s.item().categoryCode()))
                .map(s -> s.item().itemCode())
                .findFirst()
                .orElseThrow();
        List<String> codes = new ArrayList<>();
        codes.add(core);
        if (!sum.equals(core)) {
            codes.add(sum);
        }
        for (ChecklistCandidateScorer.ScoredCandidate candidate : shortlist) {
            if (codes.size() >= VALID_TARGET) {
                break;
            }
            String code = candidate.item().itemCode();
            if (!codes.contains(code)) {
                codes.add(code);
            }
        }
        assertThat(codes).hasSize(VALID_TARGET);
        return codes;
    }

    private ChecklistCatalogDocument loadMutableDocument() throws Exception {
        return objectMapper.readValue(
                new DefaultResourceLoader().getResource(CATALOG_CLASSPATH).getInputStream(),
                ChecklistCatalogDocument.class
        );
    }

    private ChecklistCatalogDocument copyDocument(
            ChecklistCatalogDocument source,
            List<ChecklistCatalogItem> items
    ) {
        return new ChecklistCatalogDocument(
                source.datasetName(),
                source.version(),
                source.fieldOnly(),
                source.description(),
                source.selectionPolicy(),
                source.enums(),
                source.categories(),
                items
        );
    }

    private ChecklistCatalogItem copyItemWithCode(ChecklistCatalogItem source, String itemCode) {
        return new ChecklistCatalogItem(
                itemCode,
                source.categoryCode(),
                source.categoryTitle(),
                source.categoryOrder(),
                source.title(),
                source.subtitle(),
                source.answerType(),
                source.answerUnit(),
                source.accessLevel(),
                source.visitConditions(),
                source.conditionTags(),
                source.evidenceTypes(),
                source.baseWeight(),
                source.displayOrder(),
                source.active(),
                source.options(),
                source.priorityTags(),
                source.priorityMappings(),
                source.isCommonCore(),
                source.introducedInVersion(),
                source.addedReason(),
                source.example()
        );
    }

    private ChecklistCatalogItem copyItemWithAnswerType(
            ChecklistCatalogItem source,
            String answerType
    ) {
        return new ChecklistCatalogItem(
                source.itemCode(),
                source.categoryCode(),
                source.categoryTitle(),
                source.categoryOrder(),
                source.title(),
                source.subtitle(),
                answerType,
                source.answerUnit(),
                source.accessLevel(),
                source.visitConditions(),
                source.conditionTags(),
                source.evidenceTypes(),
                source.baseWeight(),
                source.displayOrder(),
                source.active(),
                source.options(),
                source.priorityTags(),
                source.priorityMappings(),
                source.isCommonCore(),
                source.introducedInVersion(),
                source.addedReason(),
                source.example()
        );
    }

    private ChecklistCatalogItem copyItemWithPriorityTags(
            ChecklistCatalogItem source,
            List<String> priorityTags
    ) {
        return new ChecklistCatalogItem(
                source.itemCode(),
                source.categoryCode(),
                source.categoryTitle(),
                source.categoryOrder(),
                source.title(),
                source.subtitle(),
                source.answerType(),
                source.answerUnit(),
                source.accessLevel(),
                source.visitConditions(),
                source.conditionTags(),
                source.evidenceTypes(),
                source.baseWeight(),
                source.displayOrder(),
                source.active(),
                source.options(),
                priorityTags,
                source.priorityMappings(),
                source.isCommonCore(),
                source.introducedInVersion(),
                source.addedReason(),
                source.example()
        );
    }

    private ChecklistSelectionContext sampleContext() {
        return new ChecklistSelectionContext(
                sampleInput(),
                List.of("TRANSPORTATION", "SAFETY"),
                "LIVE",
                50,
                25
        );
    }

    private ChecklistSelectionContext contextWith(
            List<String> priorities,
            String purpose,
            boolean hasVehicle,
            boolean hasChildren
    ) {
        return new ChecklistSelectionContext(
                new ChecklistPersonalizationInput(
                        new ChecklistPersonalizationInput.MemberOnboardingSection(
                                "RESIDENCE",
                                "SINGLE",
                                hasVehicle,
                                hasChildren,
                                List.of("TRANSPORT"),
                                "THIRTIES"
                        ),
                        new ChecklistPersonalizationInput.ApartmentSection(
                                1L, "단지", "서울", "강남구", "역삼동", 500, "201501", 300
                        ),
                        new ChecklistPersonalizationInput.StudySection(7L, "RESIDENCE", "임장 목표")
                ),
                priorities,
                purpose,
                40,
                10
        );
    }

    private ChecklistPersonalizationInput sampleInput() {
        return new ChecklistPersonalizationInput(
                new ChecklistPersonalizationInput.MemberOnboardingSection(
                        "RESIDENCE",
                        "SINGLE",
                        true,
                        true,
                        List.of("TRANSPORT", "SAFETY"),
                        "THIRTIES"
                ),
                new ChecklistPersonalizationInput.ApartmentSection(
                        1L, "단지", "서울", "강남구", "역삼동", 500, "201501", 300
                ),
                new ChecklistPersonalizationInput.StudySection(7L, "RESIDENCE", "임장 목표")
        );
    }
}
