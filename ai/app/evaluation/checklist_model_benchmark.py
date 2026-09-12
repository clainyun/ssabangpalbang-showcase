"""Pinned GPT/Gemini benchmark for the production checklist selection path."""

from __future__ import annotations

import argparse
import asyncio
import csv
import hashlib
import io
import json
import math
import shutil
import sys
import time
from collections import Counter
from collections.abc import Awaitable, Callable, Mapping, Sequence
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal, Protocol

from dotenv import dotenv_values
from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator

from app.prompts.checklist_select_prompt import SYSTEM_PROMPT, build_select_user_prompt
from app.providers.gms_model_suite import (
    CHECKLIST_BENCHMARK_MODELS,
    GmsChecklistBenchmarkSettings,
    create_gms_checklist_benchmark_suite,
)
from app.providers.llm_provider import LlmProvider, LlmProviderError
from app.schemas.checklist_select import (
    ChecklistAiSelectRequest,
    ChecklistAiSelectResponse,
    ChecklistSelectShortlistItem,
    select_response_json_schema,
)

AI_ROOT = Path(__file__).resolve().parents[2]
REPO_ROOT = AI_ROOT.parent
DATASET_PATH = Path(__file__).with_name("datasets") / "checklist_selection_v1.json"
POLICY_PATH = Path(__file__).with_name("checklist_evaluation_policy_v1.json")
CATALOG_PATH = (
    REPO_ROOT
    / "backend"
    / "src"
    / "main"
    / "resources"
    / "data"
    / "checklist"
    / "ssabang_field_visit_checklist_raw_v3_300.json"
)

ALLOWED_ACCESS_LEVELS = {"PUBLIC_OUTDOOR", "COMPLEX_OUTDOOR"}
SUMMARY_CATEGORY = "SUM"
MAX_PER_CATEGORY = 6
MAX_ITEMS_PER_PRIORITY = 3


class ChecklistBenchmarkCase(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: str = Field(min_length=1)
    mappedPurpose: Literal["LIVE", "INVEST", "LEARN"]
    selectedPriorities: list[str] = Field(min_length=1, max_length=4)
    hasVehicle: bool
    hasChildren: bool

    @field_validator("selectedPriorities")
    @classmethod
    def priorities_are_unique(cls, value: list[str]) -> list[str]:
        if len(value) != len(set(value)):
            raise ValueError("selectedPriorities must be unique")
        return value


class ChecklistBenchmarkDataset(BaseModel):
    model_config = ConfigDict(extra="forbid")

    datasetId: Literal["checklist-selection-v1"]
    cases: list[ChecklistBenchmarkCase] = Field(min_length=1)

    @field_validator("cases")
    @classmethod
    def case_ids_are_unique(
        cls, value: list[ChecklistBenchmarkCase]
    ) -> list[ChecklistBenchmarkCase]:
        ids = [case.id for case in value]
        if len(ids) != len(set(ids)):
            raise ValueError("case ids must be unique")
        return value


class ScoreWeights(BaseModel):
    model_config = ConfigDict(extra="forbid")

    operationalPass: float = Field(ge=0, le=1)
    priorityAlignment: float = Field(ge=0, le=1)
    purposeAlignment: float = Field(ge=0, le=1)
    serverScoreNdcg: float = Field(ge=0, le=1)
    categoryDiversity: float = Field(ge=0, le=1)
    targetCount: float = Field(ge=0, le=1)

    def total(self) -> float:
        return sum(self.model_dump().values())


class ChecklistEvaluationPolicy(BaseModel):
    model_config = ConfigDict(extra="forbid")

    policyId: Literal["checklist-selection-v1-official"]
    models: list[str]
    caseCount: int = Field(gt=0)
    repetitions: int = Field(gt=0)
    warmupPerModel: int = Field(ge=0)
    totalCalls: int = Field(gt=0)
    seed: int
    minRequestIntervalMs: int = Field(ge=0)
    cooldownAfterWarmupSeconds: int = Field(ge=0)
    shortlistSize: int = Field(ge=30, le=50)
    targetItemCount: int = Field(ge=20, le=30)
    categoryDiversityTarget: int = Field(gt=0)
    weights: ScoreWeights
    catalogSha256: str
    datasetSha256: str
    promptManifestSha256: str
    responseSchemaSha256: str
    evaluatorSha256: str

    @field_validator("models")
    @classmethod
    def models_are_pinned(cls, value: list[str]) -> list[str]:
        if tuple(value) != CHECKLIST_BENCHMARK_MODELS:
            raise ValueError("checklist benchmark models do not match")
        return value

    @field_validator("weights")
    @classmethod
    def weights_sum_to_one(cls, value: ScoreWeights) -> ScoreWeights:
        if not math.isclose(value.total(), 1.0, abs_tol=1e-9):
            raise ValueError("score weights must sum to 1")
        return value


class PriorityMapping(BaseModel):
    model_config = ConfigDict(extra="ignore")

    priorityCode: str
    relevanceWeight: int | None = None


class CatalogItem(BaseModel):
    model_config = ConfigDict(extra="ignore")

    itemCode: str
    categoryCode: str
    title: str
    accessLevel: str
    visitConditions: list[str] = Field(default_factory=list)
    conditionTags: list[str] = Field(default_factory=list)
    baseWeight: int | None = None
    categoryOrder: int | None = None
    displayOrder: int | None = None
    active: bool = True
    priorityTags: list[str] = Field(default_factory=list)
    priorityMappings: list[PriorityMapping] = Field(default_factory=list)
    isCommonCore: bool = False


class ChecklistCatalog(BaseModel):
    model_config = ConfigDict(extra="ignore")

    version: str
    items: list[CatalogItem] = Field(min_length=1)


@dataclass(frozen=True)
class ScoredItem:
    item: CatalogItem
    score: int


@dataclass(frozen=True)
class SelectionMetrics:
    schema_valid: bool = False
    operational_pass: bool = False
    target_count_match: bool = False
    common_core_present: bool = False
    summary_present: bool = False
    priority_alignment: float = 0.0
    purpose_alignment: float = 0.0
    server_score_ndcg: float = 0.0
    category_diversity: float = 0.0
    composite_score: float = 0.0


@dataclass(frozen=True)
class WarmupSample:
    model: str
    outcome: str
    latency_ms: float


@dataclass(frozen=True)
class BenchmarkSample:
    case_id: str
    model: str
    repetition: int
    outcome: str
    latency_ms: float
    selected_codes: list[str]
    selected_count: int
    schema_valid: bool
    operational_pass: bool
    target_count_match: bool
    common_core_present: bool
    summary_present: bool
    priority_alignment: float
    purpose_alignment: float
    server_score_ndcg: float
    category_diversity: float
    composite_score: float


@dataclass(frozen=True)
class BenchmarkRun:
    warmups: list[WarmupSample]
    samples: list[BenchmarkSample]


class ProviderProtocol(Protocol):
    async def complete_json(self, system_prompt: str, user_prompt: str) -> dict[str, Any]:
        ...


class SelectionFailure(Exception):
    def __init__(
        self,
        outcome: Literal["schema", "policy"],
        metrics: SelectionMetrics | None = None,
        selected_codes: list[str] | None = None,
    ) -> None:
        super().__init__(outcome)
        self.outcome = outcome
        self.metrics = metrics or SelectionMetrics()
        self.selected_codes = selected_codes or []


def _sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _canonical_sha256(value: Any) -> str:
    encoded = json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return _sha256(encoded)


def load_dataset(
    path: Path = DATASET_PATH,
) -> tuple[ChecklistBenchmarkDataset, str]:
    raw = path.read_bytes()
    return ChecklistBenchmarkDataset.model_validate_json(raw), _sha256(raw)


def load_policy(
    path: Path = POLICY_PATH,
) -> tuple[ChecklistEvaluationPolicy, str]:
    raw = path.read_bytes()
    return ChecklistEvaluationPolicy.model_validate_json(raw), _sha256(raw)


def load_catalog(path: Path = CATALOG_PATH) -> tuple[ChecklistCatalog, str]:
    raw = path.read_bytes()
    return ChecklistCatalog.model_validate_json(raw), _sha256(raw)


def _normalized_relevance(weight: int) -> int:
    fraction = max(0.0, min(1.0, (weight - 45.0) / 55.0))
    return int(math.floor(fraction * 20.0 + 0.5))


def score_catalog_item(item: CatalogItem, case: ChecklistBenchmarkCase) -> int:
    score = item.baseWeight or 0
    selected = set(case.selectedPriorities)
    matching = [
        mapping.relevanceWeight or 0
        for mapping in item.priorityMappings
        if mapping.priorityCode in selected
    ]
    if matching:
        score += 35 + _normalized_relevance(max(matching))
    tags = set(item.conditionTags)
    if case.hasVehicle and tags.intersection({"CAR", "PARKING"}):
        score += 10
    if case.hasChildren and tags.intersection({"CHILD", "INFANT", "TEEN"}):
        score += 10
    if case.mappedPurpose in tags:
        score += 10
    if item.isCommonCore:
        score += 15
    return score


def _score_sort_key(scored: ScoredItem) -> tuple[Any, ...]:
    item = scored.item
    return (
        -scored.score,
        -(item.baseWeight or 0),
        item.categoryOrder if item.categoryOrder is not None else sys.maxsize,
        item.displayOrder if item.displayOrder is not None else sys.maxsize,
        item.itemCode,
    )


def build_shortlist(
    catalog: ChecklistCatalog,
    case: ChecklistBenchmarkCase,
    limit: int,
    minimum: int,
) -> list[ScoredItem]:
    candidates = [
        item
        for item in catalog.items
        if item.active
        and item.accessLevel in ALLOWED_ACCESS_LEVELS
        and "ANY" in item.visitConditions
    ]
    scored = sorted(
        (ScoredItem(item, score_catalog_item(item, case)) for item in candidates),
        key=_score_sort_key,
    )
    selected: list[ScoredItem] = []
    selected_codes: set[str] = set()
    category_counts: Counter[str] = Counter()
    priority_counts: Counter[str] = Counter()
    selected_priorities = set(case.selectedPriorities)

    def try_add(candidate: ScoredItem) -> None:
        if len(selected) >= limit or candidate.item.itemCode in selected_codes:
            return
        item = candidate.item
        if category_counts[item.categoryCode] >= MAX_PER_CATEGORY:
            return
        matched = selected_priorities.intersection(item.priorityTags)
        if any(priority_counts[priority] >= MAX_ITEMS_PER_PRIORITY for priority in matched):
            return
        selected.append(candidate)
        selected_codes.add(item.itemCode)
        category_counts[item.categoryCode] += 1
        priority_counts.update(matched)

    for candidate in scored:
        if candidate.item.isCommonCore:
            try_add(candidate)
    for candidate in scored:
        if candidate.item.categoryCode == SUMMARY_CATEGORY:
            try_add(candidate)
    for candidate in scored:
        try_add(candidate)
        if len(selected) >= limit:
            break
    selected.sort(key=_score_sort_key)
    if len(selected) < minimum or len(selected) > limit:
        raise ValueError(
            f"shortlist must contain {minimum}..{limit} items; got {len(selected)}"
        )
    return selected


def build_requests(
    catalog: ChecklistCatalog,
    dataset: ChecklistBenchmarkDataset,
    policy: ChecklistEvaluationPolicy,
) -> dict[str, ChecklistAiSelectRequest]:
    requests: dict[str, ChecklistAiSelectRequest] = {}
    for case in dataset.cases:
        shortlist = build_shortlist(
            catalog,
            case,
            policy.shortlistSize,
            policy.targetItemCount,
        )
        requests[case.id] = ChecklistAiSelectRequest(
            selectionVersion="v3-select-1",
            targetItemCount=policy.targetItemCount,
            mappedPurpose=case.mappedPurpose,
            selectedPriorities=case.selectedPriorities,
            shortlist=[
                ChecklistSelectShortlistItem(
                    itemCode=scored.item.itemCode,
                    categoryCode=scored.item.categoryCode,
                    title=scored.item.title,
                    priorityTags=scored.item.priorityTags,
                    conditionTags=scored.item.conditionTags,
                    serverScore=scored.score,
                    isCommonCore=scored.item.isCommonCore,
                )
                for scored in shortlist
            ],
        )
    return requests


def prompt_manifest(
    requests: Mapping[str, ChecklistAiSelectRequest],
) -> dict[str, str]:
    return {
        case_id: _sha256(
            (SYSTEM_PROMPT + "\0" + build_select_user_prompt(request)).encode("utf-8")
        )
        for case_id, request in sorted(requests.items())
    }


def _dcg(values: Sequence[int]) -> float:
    return sum(value / math.log2(index + 2) for index, value in enumerate(values))


def evaluate_payload(
    payload: Mapping[str, Any],
    *,
    request: ChecklistAiSelectRequest,
    case: ChecklistBenchmarkCase,
    policy: ChecklistEvaluationPolicy,
) -> tuple[list[str], SelectionMetrics]:
    try:
        response = ChecklistAiSelectResponse.model_validate(payload)
    except ValidationError as exc:
        raise SelectionFailure("schema") from exc
    lookup = {item.itemCode: item for item in request.shortlist}
    if not set(response.itemCodes).issubset(lookup):
        raise SelectionFailure(
            "schema",
            SelectionMetrics(schema_valid=True),
            response.itemCodes,
        )

    items = [lookup[code] for code in response.itemCodes]
    count = len(items)
    target_match = count == request.targetItemCount
    common = any(item.isCommonCore for item in items)
    summary = any(item.categoryCode == SUMMARY_CATEGORY for item in items)
    max_category = max(Counter(item.categoryCode for item in items).values())
    policy_valid = common and summary and max_category <= max(3, count // 2)

    priority_scores = []
    for priority in case.selectedPriorities:
        matched = sum(priority in item.priorityTags for item in items)
        priority_scores.append(min(matched, 2) / 2)
    priority_alignment = sum(priority_scores) / len(priority_scores)

    eligible_purpose = [
        item
        for item in request.shortlist
        if case.mappedPurpose in item.conditionTags or "ALL" in item.conditionTags
    ]
    selected_purpose = sum(
        case.mappedPurpose in item.conditionTags or "ALL" in item.conditionTags
        for item in items
    )
    purpose_denominator = min(count, len(eligible_purpose))
    purpose_alignment = (
        selected_purpose / purpose_denominator if purpose_denominator else 1.0
    )

    selected_scores = [max(0, item.serverScore) for item in items]
    ideal_scores = sorted(
        (max(0, item.serverScore) for item in request.shortlist), reverse=True
    )[:count]
    ideal_dcg = _dcg(ideal_scores)
    ndcg = _dcg(selected_scores) / ideal_dcg if ideal_dcg else 1.0

    available_categories = len({item.categoryCode for item in request.shortlist})
    diversity_denominator = min(
        policy.categoryDiversityTarget,
        available_categories,
        count,
    )
    diversity = (
        len({item.categoryCode for item in items}) / diversity_denominator
        if diversity_denominator
        else 1.0
    )
    diversity = min(diversity, 1.0)

    weights = policy.weights
    composite = 0.0
    if policy_valid:
        composite = (
            weights.operationalPass
            + weights.priorityAlignment * priority_alignment
            + weights.purposeAlignment * purpose_alignment
            + weights.serverScoreNdcg * ndcg
            + weights.categoryDiversity * diversity
            + weights.targetCount * float(target_match)
        )
    metrics = SelectionMetrics(
        schema_valid=True,
        operational_pass=policy_valid,
        target_count_match=target_match,
        common_core_present=common,
        summary_present=summary,
        priority_alignment=priority_alignment,
        purpose_alignment=purpose_alignment,
        server_score_ndcg=ndcg,
        category_diversity=diversity,
        composite_score=composite,
    )
    if not policy_valid:
        raise SelectionFailure("policy", metrics, response.itemCodes)
    return response.itemCodes, metrics


def validate_official_profile(
    *,
    dataset: ChecklistBenchmarkDataset,
    dataset_hash: str,
    catalog_hash: str,
    policy: ChecklistEvaluationPolicy,
    requests: Mapping[str, ChecklistAiSelectRequest],
    arguments: argparse.Namespace,
) -> None:
    if len(dataset.cases) != policy.caseCount:
        raise ValueError("official case count does not match")
    expected_calls = (
        policy.caseCount * policy.repetitions + policy.warmupPerModel
    ) * len(policy.models)
    if expected_calls != policy.totalCalls:
        raise ValueError("official total call count does not match")
    expected_arguments = {
        "repetitions": policy.repetitions,
        "warmup": policy.warmupPerModel,
        "seed": policy.seed,
        "min_request_interval_ms": policy.minRequestIntervalMs,
        "cooldown_seconds": policy.cooldownAfterWarmupSeconds,
    }
    if any(getattr(arguments, key) != value for key, value in expected_arguments.items()):
        raise ValueError("official runtime arguments do not match policy")
    checks = {
        "catalog": (catalog_hash, policy.catalogSha256),
        "dataset": (dataset_hash, policy.datasetSha256),
        "prompt manifest": (
            _canonical_sha256(prompt_manifest(requests)),
            policy.promptManifestSha256,
        ),
        "response schema": (
            _canonical_sha256(select_response_json_schema()),
            policy.responseSchemaSha256,
        ),
        "evaluator": (_sha256(Path(__file__).read_bytes()), policy.evaluatorSha256),
    }
    for name, (actual, expected) in checks.items():
        if actual != expected:
            raise ValueError(f"official {name} hash does not match")


class ChecklistBenchmarkRunner:
    def __init__(
        self,
        providers: Mapping[str, ProviderProtocol],
        policy: ChecklistEvaluationPolicy,
        *,
        sleep: Callable[[float], Awaitable[None]] = asyncio.sleep,
        timer: Callable[[], float] = time.perf_counter,
    ) -> None:
        if tuple(providers) != tuple(policy.models):
            raise ValueError("provider order does not match policy")
        self.providers = dict(providers)
        self.policy = policy
        self.sleep = sleep
        self.timer = timer
        self._last_start: float | None = None

    async def _pace(self) -> None:
        interval = self.policy.minRequestIntervalMs / 1000
        now = self.timer()
        if self._last_start is not None:
            remaining = interval - (now - self._last_start)
            if remaining > 0:
                await self.sleep(remaining)
        self._last_start = self.timer()

    async def _call(
        self,
        model: str,
        request: ChecklistAiSelectRequest,
    ) -> tuple[str, float, Mapping[str, Any] | None]:
        await self._pace()
        started = self.timer()
        try:
            payload = await self.providers[model].complete_json(
                SYSTEM_PROMPT,
                build_select_user_prompt(request),
            )
        except LlmProviderError as exc:
            outcome = "rate-limit" if exc.status_code == 429 else "transport"
            return outcome, (self.timer() - started) * 1000, None
        except Exception:
            return "provider", (self.timer() - started) * 1000, None
        return "response", (self.timer() - started) * 1000, payload

    async def run(
        self,
        dataset: ChecklistBenchmarkDataset,
        requests: Mapping[str, ChecklistAiSelectRequest],
    ) -> BenchmarkRun:
        warmups: list[WarmupSample] = []
        first_request = requests[dataset.cases[0].id]
        for _ in range(self.policy.warmupPerModel):
            for model in self.policy.models:
                outcome, latency, _ = await self._call(model, first_request)
                warmups.append(WarmupSample(model, outcome, latency))
        if self.policy.cooldownAfterWarmupSeconds:
            await self.sleep(self.policy.cooldownAfterWarmupSeconds)

        samples: list[BenchmarkSample] = []
        case_by_id = {case.id: case for case in dataset.cases}
        batch = 0
        for repetition in range(1, self.policy.repetitions + 1):
            for case in dataset.cases:
                order = list(self.policy.models)
                if (batch + self.policy.seed) % len(order):
                    order.reverse()
                batch += 1
                request = requests[case.id]
                for model in order:
                    outcome, latency, payload = await self._call(model, request)
                    codes: list[str] = []
                    metrics = SelectionMetrics()
                    if outcome == "response" and payload is not None:
                        try:
                            codes, metrics = evaluate_payload(
                                payload,
                                request=request,
                                case=case_by_id[case.id],
                                policy=self.policy,
                            )
                            outcome = "success"
                        except SelectionFailure as exc:
                            outcome = exc.outcome
                            metrics = exc.metrics
                            codes = exc.selected_codes
                    samples.append(
                        BenchmarkSample(
                            case_id=case.id,
                            model=model,
                            repetition=repetition,
                            outcome=outcome,
                            latency_ms=latency,
                            selected_codes=codes,
                            selected_count=len(codes),
                            **asdict(metrics),
                        )
                    )
        run = BenchmarkRun(warmups, samples)
        validate_result_matrix(dataset, self.policy, run)
        return run


def validate_result_matrix(
    dataset: ChecklistBenchmarkDataset,
    policy: ChecklistEvaluationPolicy,
    run: BenchmarkRun,
) -> None:
    expected = Counter(
        (case.id, model, repetition)
        for repetition in range(1, policy.repetitions + 1)
        for case in dataset.cases
        for model in policy.models
    )
    actual = Counter(
        (sample.case_id, sample.model, sample.repetition) for sample in run.samples
    )
    if actual != expected:
        raise ValueError("benchmark result matrix is incomplete or duplicated")
    warmup_counts = Counter(sample.model for sample in run.warmups)
    if warmup_counts != Counter({model: policy.warmupPerModel for model in policy.models}):
        raise ValueError("benchmark warmup matrix does not match")


def _rate(numerator: int, denominator: int) -> dict[str, Any]:
    return {
        "numerator": numerator,
        "denominator": denominator,
        "value": numerator / denominator if denominator else None,
    }


def _nearest_rank(values: Sequence[float], quantile: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(0, math.ceil(quantile * len(ordered)) - 1)]


def aggregate_results(
    run: BenchmarkRun,
    policy: ChecklistEvaluationPolicy,
) -> tuple[dict[str, Any], bool]:
    any_rate_limit = any(
        sample.outcome == "rate-limit" for sample in [*run.warmups, *run.samples]
    )
    models: dict[str, Any] = {}
    for model in policy.models:
        samples = [sample for sample in run.samples if sample.model == model]
        scheduled = len(samples)
        success = [sample for sample in samples if sample.outcome == "success"]
        latency = [sample.latency_ms for sample in success]
        models[model] = {
            "scheduled": scheduled,
            "outcome_counts": dict(sorted(Counter(s.outcome for s in samples).items())),
            "response_rate": _rate(
                sum(s.outcome in {"success", "policy", "schema"} for s in samples),
                scheduled,
            ),
            "schema_valid_rate": _rate(sum(s.schema_valid for s in samples), scheduled),
            "operational_pass_rate": _rate(
                sum(s.operational_pass for s in samples), scheduled
            ),
            "target_count_rate": _rate(
                sum(s.target_count_match for s in samples), scheduled
            ),
            "common_core_rate": _rate(
                sum(s.common_core_present for s in samples), scheduled
            ),
            "summary_rate": _rate(sum(s.summary_present for s in samples), scheduled),
            "mean_priority_alignment": sum(s.priority_alignment for s in samples)
            / scheduled,
            "mean_purpose_alignment": sum(s.purpose_alignment for s in samples)
            / scheduled,
            "mean_server_score_ndcg": sum(s.server_score_ndcg for s in samples)
            / scheduled,
            "mean_category_diversity": sum(s.category_diversity for s in samples)
            / scheduled,
            "project_fit_score": 100
            * sum(s.composite_score for s in samples)
            / scheduled,
            "latency_ms": {
                "p50": _nearest_rank(latency, 0.50),
                "p95": _nearest_rank(latency, 0.95),
                "successful_n": len(latency),
            },
        }
    return models, not any_rate_limit


def build_summary(
    *,
    run: BenchmarkRun,
    policy: ChecklistEvaluationPolicy,
    policy_hash: str,
    dataset_hash: str,
    catalog_hash: str,
    requests: Mapping[str, ChecklistAiSelectRequest],
    run_id: str,
    started_at: datetime,
    finished_at: datetime,
) -> dict[str, Any]:
    models, comparison_valid = aggregate_results(run, policy)
    leaderboard = []
    if comparison_valid:
        leaderboard = [
            {"model": model, "project_fit_score": metrics["project_fit_score"]}
            for model, metrics in sorted(
                models.items(),
                key=lambda item: (-item[1]["project_fit_score"], item[0]),
            )
        ]
    return {
        "run_id": run_id,
        "started_at_utc": started_at.isoformat(),
        "finished_at_utc": finished_at.isoformat(),
        "quality_comparison_valid": comparison_valid,
        "leaderboard": leaderboard,
        "models": models,
        "warmup_outcome_counts": dict(
            sorted(Counter(sample.outcome for sample in run.warmups).items())
        ),
        "inputs": {
            "catalog_sha256": catalog_hash,
            "dataset_sha256": dataset_hash,
            "policy_sha256": policy_hash,
            "prompt_manifest_sha256": _canonical_sha256(prompt_manifest(requests)),
            "response_schema_sha256": _canonical_sha256(select_response_json_schema()),
            "evaluator_sha256": _sha256(Path(__file__).read_bytes()),
        },
        "execution": {
            "models": policy.models,
            "case_count": policy.caseCount,
            "repetitions": policy.repetitions,
            "warmup_per_model": policy.warmupPerModel,
            "total_calls": policy.totalCalls,
            "shortlist_size": policy.shortlistSize,
            "target_item_count": policy.targetItemCount,
        },
    }


def _summary_csv(summary: Mapping[str, Any]) -> str:
    output = io.StringIO(newline="")
    writer = csv.writer(output)
    writer.writerow(
        [
            "model",
            "project_fit_score",
            "operational_pass_numerator",
            "operational_pass_denominator",
            "priority_alignment",
            "purpose_alignment",
            "server_score_ndcg",
            "category_diversity",
            "latency_p50_ms",
            "latency_p95_ms",
            "latency_n",
        ]
    )
    for model, metrics in summary["models"].items():
        writer.writerow(
            [
                model,
                metrics["project_fit_score"],
                metrics["operational_pass_rate"]["numerator"],
                metrics["operational_pass_rate"]["denominator"],
                metrics["mean_priority_alignment"],
                metrics["mean_purpose_alignment"],
                metrics["mean_server_score_ndcg"],
                metrics["mean_category_diversity"],
                metrics["latency_ms"]["p50"],
                metrics["latency_ms"]["p95"],
                metrics["latency_ms"]["successful_n"],
            ]
        )
    return output.getvalue()


def _summary_markdown(summary: Mapping[str, Any]) -> str:
    lines = [
        "# Checklist model benchmark",
        "",
        f"- Run: `{summary['run_id']}`",
        f"- Quality comparison valid: `{str(summary['quality_comparison_valid']).lower()}`",
        "",
        "| Model | ProjectFit | Operational pass | Priority | Purpose | NDCG | Diversity | p95 (n) |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for model, metrics in summary["models"].items():
        operational = metrics["operational_pass_rate"]
        latency = metrics["latency_ms"]
        lines.append(
            f"| {model} | {metrics['project_fit_score']:.2f} | "
            f"{operational['numerator']}/{operational['denominator']} | "
            f"{metrics['mean_priority_alignment']:.3f} | "
            f"{metrics['mean_purpose_alignment']:.3f} | "
            f"{metrics['mean_server_score_ndcg']:.3f} | "
            f"{metrics['mean_category_diversity']:.3f} | "
            f"{latency['p95']} ({latency['successful_n']}) |"
        )
    if not summary["quality_comparison_valid"]:
        lines.extend(["", "Leaderboard withheld because HTTP 429 was observed."])
    return "\n".join(lines) + "\n"


def write_results(
    output_root: Path,
    *,
    summary: Mapping[str, Any],
    run: BenchmarkRun,
) -> Path:
    final_dir = output_root / str(summary["run_id"])
    staging = output_root / f".{summary['run_id']}.staging"
    output_root.mkdir(parents=True, exist_ok=True)
    if final_dir.exists() or staging.exists():
        raise FileExistsError("run output already exists")
    staging.mkdir()
    try:
        payloads = {
            "samples.jsonl": "".join(
                json.dumps(asdict(sample), ensure_ascii=False, sort_keys=True) + "\n"
                for sample in run.samples
            ),
            "summary.json": json.dumps(
                summary, ensure_ascii=False, indent=2, sort_keys=True
            )
            + "\n",
            "summary.csv": _summary_csv(summary),
            "summary.md": _summary_markdown(summary),
        }
        if payloads["samples.jsonl"].count("\n") != len(run.samples):
            raise ValueError("sample serialization count does not match")
        checksums = []
        for name, content in payloads.items():
            data = content.encode("utf-8")
            (staging / name).write_bytes(data)
            checksums.append(f"{_sha256(data)}  {name}")
        manifest = ("\n".join(checksums) + "\n").encode("utf-8")
        (staging / "checksums.sha256").write_bytes(manifest)
        (staging / "COMPLETE").write_text(
            f"{_sha256(manifest)}  checksums.sha256\n",
            encoding="utf-8",
            newline="\n",
        )
        staging.replace(final_dir)
    except Exception:
        if staging.exists():
            shutil.rmtree(staging)
        raise
    return final_dir


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run checklist model benchmark v1")
    parser.add_argument("--official", action="store_true", required=True)
    parser.add_argument("--env-file", type=Path, required=True)
    parser.add_argument("--dataset", type=Path, default=DATASET_PATH)
    parser.add_argument("--policy", type=Path, default=POLICY_PATH)
    parser.add_argument("--catalog", type=Path, default=CATALOG_PATH)
    parser.add_argument("--repetitions", type=int, default=3)
    parser.add_argument("--warmup", type=int, default=1)
    parser.add_argument("--seed", type=int, default=20260811)
    parser.add_argument("--min-request-interval-ms", type=int, default=6500)
    parser.add_argument("--cooldown-seconds", type=int, default=60)
    parser.add_argument(
        "--output-dir", type=Path, default=Path("../output/checklist-model-eval")
    )
    return parser


async def _run_cli(arguments: argparse.Namespace) -> Path:
    dataset, dataset_hash = load_dataset(arguments.dataset)
    catalog, catalog_hash = load_catalog(arguments.catalog)
    policy, policy_hash = load_policy(arguments.policy)
    requests = build_requests(catalog, dataset, policy)
    validate_official_profile(
        dataset=dataset,
        dataset_hash=dataset_hash,
        catalog_hash=catalog_hash,
        policy=policy,
        requests=requests,
        arguments=arguments,
    )
    if not arguments.env_file.is_file():
        raise ValueError("official benchmark requires an env file")
    settings = GmsChecklistBenchmarkSettings.from_mapping(
        dotenv_values(arguments.env_file)
    )
    providers: Mapping[str, LlmProvider] = create_gms_checklist_benchmark_suite(
        settings,
        response_schema=select_response_json_schema(),
    )
    started = datetime.now(timezone.utc)
    run_id = started.strftime("%Y%m%dT%H%M%S%fZ")
    run = await ChecklistBenchmarkRunner(providers, policy).run(dataset, requests)
    finished = datetime.now(timezone.utc)
    summary = build_summary(
        run=run,
        policy=policy,
        policy_hash=policy_hash,
        dataset_hash=dataset_hash,
        catalog_hash=catalog_hash,
        requests=requests,
        run_id=run_id,
        started_at=started,
        finished_at=finished,
    )
    return write_results(arguments.output_dir, summary=summary, run=run)


def main(argv: Sequence[str] | None = None) -> int:
    arguments = _parser().parse_args(argv)
    try:
        output = asyncio.run(_run_cli(arguments))
    except Exception as exc:
        print(f"evaluation failed: {type(exc).__name__}", file=sys.stderr)
        return 1
    print(f"evaluation complete: {output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
