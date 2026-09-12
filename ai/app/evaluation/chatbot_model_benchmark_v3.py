"""Pinned production-fit benchmark for the three SSAFY GMS models."""

from __future__ import annotations

import argparse
import asyncio
import csv
import hashlib
import io
import json
import math
import random
import re
import shutil
import sys
import time
import unicodedata
from collections import Counter
from collections.abc import Awaitable, Callable, Mapping, Sequence
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal, Protocol

import jsonschema
from dotenv import dotenv_values
from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    StrictInt,
    ValidationError,
    model_validator,
)

from app.prompts.chatbot_prompt import SYSTEM_PROMPT, build_user_prompt
from app.providers.gms_model_suite import (
    ANTHROPIC_MODEL,
    ANTHROPIC_VERSION,
    DEFAULT_ANTHROPIC_MAX_TOKENS,
    GEMINI_MODEL,
    OPENAI_MODEL,
    GmsModelSuiteSettings,
    create_gms_production_fit_suite,
)
from app.providers.llm_provider import LlmProvider, LlmProviderError
from app.providers.web_search_provider import WebSearchResult
from app.rag.search import ApartmentProfile, ReportChunkHit
from app.schemas.chatbot import (
    CHATBOT_ANSWER_RESPONSE_SCHEMA,
    INSUFFICIENT_EVIDENCE_ANSWER,
    LlmAnswerPayload,
    llm_answer_json_schema,
)


ROOT = Path(__file__).resolve().parent
DATASET_V3_PATH = ROOT / "datasets" / "chatbot_grounding_v3.json"
POLICY_V3_PATH = ROOT / "evaluation_policy_v3.json"
OFFICIAL_MODELS = (GEMINI_MODEL, OPENAI_MODEL, ANTHROPIC_MODEL)
CANONICAL_OUTCOMES = frozenset(
    {"transport", "rate-limit", "provider", "schema", "success"}
)
EXPECTED_CATEGORY_DISTRIBUTION = {
    "direct_report": 4,
    "long_report": 6,
    "web": 3,
    "insufficient": 3,
    "profile": 2,
    "mixed": 2,
}
EXPECTED_GATES = {
    "completionRateMin": 0.95,
    "effectiveTaskMacroMin": 0.9,
    "longAccuracyMin": 0.85,
    "unsupportedViolationMax": 0.0,
    "mixedViolationMax": 0.0,
    "safetyMixedComplianceMin": 0.95,
    "p95MsExclusiveMax": 20000,
}
EXPECTED_WEIGHTS = {
    "effectiveTaskMacro": 0.5,
    "longAccuracy": 0.25,
    "safetyMixedCompliance": 0.15,
    "relativeLatency": 0.1,
}


class ProfileV3(BaseModel):
    model_config = ConfigDict(extra="forbid")
    name: str = Field(min_length=1)
    address: str | None = None
    districtName: str | None = None
    dongName: str | None = None
    householdCount: int | None = Field(default=None, ge=0)
    completionYearMonth: str | None = None
    parkingSpaceCount: int | None = Field(default=None, ge=0)


class ReportEvidenceV3(BaseModel):
    model_config = ConfigDict(extra="forbid")
    sourceId: int = Field(gt=0)
    content: str = Field(min_length=1)
    repeatToChars: int | None = Field(default=None, ge=1, le=30000)
    sourceAt: datetime | None = None
    similarity: float = Field(ge=-1, le=1)

    def expanded_content(self) -> str:
        if self.repeatToChars is None or len(self.content) >= self.repeatToChars:
            return self.content
        repeats = math.ceil(self.repeatToChars / len(self.content))
        return (self.content * repeats)[: self.repeatToChars]


class WebEvidenceV3(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str = Field(min_length=1)
    snippet: str = Field(min_length=1)
    url: str = Field(min_length=1)
    domain: str = Field(min_length=1)
    retrievedAt: datetime


class FactRubricV3(BaseModel):
    model_config = ConfigDict(extra="forbid")
    factGroups: list[list[str]] = Field(min_length=1)
    forbiddenTerms: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_terms(self) -> "FactRubricV3":
        if any(
            not group or any(not term.strip() for term in group)
            for group in self.factGroups
        ):
            raise ValueError("fact groups require non-blank alternatives")
        if any(not term.strip() for term in self.forbiddenTerms):
            raise ValueError("forbidden fact terms cannot be blank")
        return self


class RefusalRubricV3(BaseModel):
    model_config = ConfigDict(extra="forbid")
    markers: list[str] = Field(min_length=1)
    forbiddenClaims: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_terms(self) -> "RefusalRubricV3":
        if any(not marker.strip() for marker in self.markers):
            raise ValueError("refusal markers cannot be blank")
        if any(not claim.strip() for claim in self.forbiddenClaims):
            raise ValueError("forbidden refusal claims cannot be blank")
        return self


class BenchmarkCaseV3(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: str = Field(pattern=r"^[a-z0-9][a-z0-9-]*$")
    category: Literal[
        "direct_report", "long_report", "web", "insufficient", "profile", "mixed"
    ]
    question: str = Field(min_length=1)
    profile: ProfileV3 | None = None
    reportEvidence: list[ReportEvidenceV3] = Field(default_factory=list)
    webEvidence: list[WebEvidenceV3] = Field(default_factory=list)
    factRubric: FactRubricV3 | None
    refusalRubric: RefusalRubricV3 | None
    acceptableSourceSets: list[list[StrictInt]] = Field(min_length=1)
    longContextChars: int | None = Field(default=None, ge=8000, le=29000)

    @model_validator(mode="after")
    def validate_case(self) -> "BenchmarkCaseV3":
        if (self.factRubric is None) == (self.refusalRubric is None):
            raise ValueError("exactly one fact/refusal rubric is required")
        maximum = len(self.reportEvidence) + len(self.webEvidence)
        canonical: set[tuple[int, ...]] = set()
        for source_set in self.acceptableSourceSets:
            if len(source_set) != len(set(source_set)):
                raise ValueError("source sets cannot contain duplicates")
            if any(value < 1 or value > maximum for value in source_set):
                raise ValueError("source index is out of range")
            key = tuple(sorted(source_set))
            if key in canonical:
                raise ValueError("source sets must be distinct")
            canonical.add(key)
        if self.refusalRubric is not None and self.acceptableSourceSets != [[]]:
            raise ValueError("refusal cases must require exactly an empty source set")
        if self.category == "long_report":
            if self.longContextChars not in {8000, 20000, 29000}:
                raise ValueError("long cases must pin 8k, 20k, or 29k")
            expanded = [len(item.expanded_content()) for item in self.reportEvidence]
            if sum(expanded) < self.longContextChars:
                raise ValueError("expanded long evidence is shorter than its label")
            if self.id.endswith("-middle"):
                accepted = self.acceptableSourceSets[0]
                if len(accepted) != 1:
                    raise ValueError("middle-position cases require one source")
                answer_index = accepted[0] - 1
                before = sum(expanded[:answer_index])
                after = sum(expanded[answer_index + 1 :])
                if min(before, after) < self.longContextChars * 0.4:
                    raise ValueError("middle-position answer is not near the context middle")
        elif self.longContextChars is not None:
            raise ValueError("only long cases may set longContextChars")
        return self


class BenchmarkDatasetV3(BaseModel):
    model_config = ConfigDict(extra="forbid")
    schemaVersion: Literal[3]
    name: Literal["chatbot-grounding-production-fit-v3"]
    cases: list[BenchmarkCaseV3]

    @model_validator(mode="after")
    def validate_dataset(self) -> "BenchmarkDatasetV3":
        ids = [case.id for case in self.cases]
        if len(ids) != len(set(ids)):
            raise ValueError("case ids must be unique")
        return self


class SchedulePolicyV3(BaseModel):
    model_config = ConfigDict(extra="forbid")
    singleLane: Literal[True]
    latinSquare: Literal[True]
    minRequestIntervalMs: Literal[6500]
    cooldownBeforeMeasuredSeconds: Literal[300]


class GatePolicyV3(BaseModel):
    model_config = ConfigDict(extra="forbid")
    completionRateMin: float
    effectiveTaskMacroMin: float
    longAccuracyMin: float
    unsupportedViolationMax: float
    mixedViolationMax: float
    safetyMixedComplianceMin: float
    p95MsExclusiveMax: int


class ScoreWeightsV3(BaseModel):
    model_config = ConfigDict(extra="forbid")
    effectiveTaskMacro: float
    longAccuracy: float
    safetyMixedCompliance: float
    relativeLatency: float


class ProviderModesV3(BaseModel):
    model_config = ConfigDict(extra="forbid")
    gemini: Literal["json-mime-response-schema"]
    openai: Literal["json-object-developer-role"]
    anthropic: Literal["messages-prompt-json"]


class EvaluationPolicyV3(BaseModel):
    model_config = ConfigDict(extra="forbid")
    schemaVersion: Literal[3]
    policyId: Literal["gms-project-fit-v3-official"]
    datasetFile: Literal["datasets/chatbot_grounding_v3.json"]
    datasetSha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    promptSha256ByCase: dict[str, str]
    promptManifestSha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    responseSchemaSha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    evaluatorSha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    evaluatorContract: Literal["production-fit-v3-boundary-aware-v1"]
    models: list[str]
    caseCount: Literal[20]
    repetitions: Literal[3]
    warmupPerModel: Literal[1]
    totalCalls: Literal[183]
    temperature: Literal[0.2]
    retryCount: Literal[0]
    anthropicMaxTokens: Literal[4096]
    anthropicVersion: Literal["2023-06-01"]
    seed: Literal[20260809]
    parser: Literal["provider-native-plus-common-schema-v1"]
    providerModes: ProviderModesV3
    schedule: SchedulePolicyV3
    gates: GatePolicyV3
    scoreWeights: ScoreWeightsV3

    @model_validator(mode="after")
    def validate_fixed_profile(self) -> "EvaluationPolicyV3":
        if tuple(self.models) != OFFICIAL_MODELS:
            raise ValueError("official model order does not match")
        if self.gates.model_dump() != EXPECTED_GATES:
            raise ValueError("official gates do not match")
        if self.scoreWeights.model_dump() != EXPECTED_WEIGHTS:
            raise ValueError("official score weights do not match")
        if any(
            not re.fullmatch(r"[0-9a-f]{64}", value)
            for value in self.promptSha256ByCase.values()
        ):
            raise ValueError("prompt hashes must be SHA-256 values")
        return self


@dataclass(frozen=True)
class ProductionCompletion:
    outcome: str
    payload: LlmAnswerPayload | None = None
    status_code: int | None = None


@dataclass(frozen=True)
class WarmupSampleV3:
    model: str
    outcome: str
    status_code: int | None


@dataclass(frozen=True)
class OfficialSampleV3:
    model: str
    case_id: str
    category: str
    repetition: int
    ordinal: int
    call_order: int
    prompt_hash: str
    elapsed_ms: float
    outcome: str
    status_code: int | None
    used_sources: list[int] | None
    fact_correct: bool | None
    refusal_correct: bool | None
    evidence_correct: bool | None
    effective_task_correct: bool | None
    unsupported_answer_violation: bool | None
    mixed_citation_violation: bool | None


@dataclass(frozen=True)
class OfficialRunV3:
    warmups: list[WarmupSampleV3]
    samples: list[OfficialSampleV3]


class ProductionClientProtocol(Protocol):
    async def complete(
        self, model: str, system_prompt: str, user_prompt: str
    ) -> ProductionCompletion: ...


class GmsProductionClient:
    """Normalize deployed provider adapters and apply the common schema."""

    def __init__(self, providers: Mapping[str, LlmProvider]) -> None:
        if tuple(providers) != OFFICIAL_MODELS:
            raise ValueError("production provider order does not match")
        self.providers = dict(providers)
        self.response_schema = llm_answer_json_schema()

    async def complete(
        self,
        model: str,
        system_prompt: str,
        user_prompt: str,
    ) -> ProductionCompletion:
        try:
            raw = await self.providers[model].complete_json(system_prompt, user_prompt)
        except LlmProviderError as exc:
            if exc.status_code == 429:
                return ProductionCompletion("rate-limit", status_code=429)
            return ProductionCompletion("transport", status_code=exc.status_code)

        try:
            jsonschema.validate(instance=raw, schema=self.response_schema)
            payload = LlmAnswerPayload.model_validate(raw)
        except (jsonschema.ValidationError, ValidationError, TypeError):
            return ProductionCompletion("schema")
        return ProductionCompletion("success", payload=payload)


def _sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _canonical_json_sha256(value: Any) -> str:
    encoded = json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return _sha256(encoded)


def load_dataset_v3(
    path: Path = DATASET_V3_PATH,
) -> tuple[BenchmarkDatasetV3, str]:
    raw = path.read_bytes()
    dataset = BenchmarkDatasetV3.model_validate(json.loads(raw.decode("utf-8")))
    return dataset, _sha256(raw)


def load_policy_v3(
    path: Path = POLICY_V3_PATH,
) -> tuple[EvaluationPolicyV3, str]:
    raw = path.read_bytes()
    policy = EvaluationPolicyV3.model_validate(json.loads(raw.decode("utf-8")))
    return policy, _sha256(raw)


def build_case_prompt_v3(case: BenchmarkCaseV3) -> tuple[str, str]:
    profile = None
    if case.profile is not None:
        value = case.profile
        profile = ApartmentProfile(
            name=value.name,
            address=value.address,
            district_name=value.districtName,
            dong_name=value.dongName,
            household_count=value.householdCount,
            completion_year_month=value.completionYearMonth,
            parking_space_count=value.parkingSpaceCount,
        )
    reports = [
        ReportChunkHit(
            source_id=item.sourceId,
            content=item.expanded_content(),
            source_at=item.sourceAt,
            similarity=item.similarity,
        )
        for item in case.reportEvidence
    ]
    web = [
        WebSearchResult(
            title=item.title,
            snippet=item.snippet,
            url=item.url,
            domain=item.domain,
            retrieved_at=item.retrievedAt,
        )
        for item in case.webEvidence
    ]
    return SYSTEM_PROMPT, build_user_prompt(case.question, profile, reports, web)


def prompt_hash(system: str, user: str) -> str:
    return _sha256((system + "\0" + user).encode("utf-8"))


def prompt_manifest_v3(dataset: BenchmarkDatasetV3) -> dict[str, str]:
    return {
        case.id: prompt_hash(*build_case_prompt_v3(case))
        for case in dataset.cases
    }


def _normalize(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value).casefold()
    return " ".join(normalized.split())


def _contains_rubric_term(answer: str, term: str) -> bool:
    """Match a rubric term without accepting a longer numeric token."""
    normalized_term = _normalize(term)
    if not normalized_term:
        return False
    escaped = re.escape(normalized_term)
    prefix = r"(?<![\d.])" if normalized_term[0].isdigit() else ""
    suffix = r"(?![\d.])" if normalized_term[-1].isdigit() else ""
    return re.search(prefix + escaped + suffix, answer) is not None


_NEGATED_REFUSAL_TAIL = re.compile(
    r"^\s*(?:"
    r"(?:는|다는|은|다고)?\s*(?:것|게|바|내용|정보|건)?\s*"
    r"(?:이|은|는)?\s*아니|지(?:는)?\s*않"
    r")"
)


def _has_affirmative_refusal(answer: str, markers: Sequence[str]) -> bool:
    for raw_marker in markers:
        marker = _normalize(raw_marker)
        start = 0
        while True:
            index = answer.find(marker, start)
            if index < 0:
                break
            tail = answer[index + len(marker) : index + len(marker) + 32]
            if _NEGATED_REFUSAL_TAIL.search(tail) is None:
                return True
            start = index + len(marker)
    return False


def score_payload_v3(
    case: BenchmarkCaseV3,
    payload: LlmAnswerPayload,
) -> tuple[bool | None, bool | None, bool, bool, bool | None]:
    answer = _normalize(payload.answer)
    fact_correct: bool | None = None
    refusal_correct: bool | None = None
    if case.factRubric is not None:
        fact_correct = all(
            any(_contains_rubric_term(answer, alternative) for alternative in group)
            for group in case.factRubric.factGroups
        ) and all(
            not _contains_rubric_term(answer, term)
            for term in case.factRubric.forbiddenTerms
        )
    else:
        assert case.refusalRubric is not None
        markers = [INSUFFICIENT_EVIDENCE_ANSWER, *case.refusalRubric.markers]
        refusal_correct = _has_affirmative_refusal(answer, markers) and all(
            not _contains_rubric_term(answer, claim)
            for claim in case.refusalRubric.forbiddenClaims
        )

    selected = payload.usedSources
    selected_set = frozenset(selected)
    acceptable = {frozenset(values) for values in case.acceptableSourceSets}
    evidence_correct = (
        len(selected) == len(selected_set) and selected_set in acceptable
    )

    mixed_violation = None
    report_count = len(case.reportEvidence)
    if report_count and case.webEvidence:
        maximum = report_count + len(case.webEvidence)
        valid = {value for value in selected if 1 <= value <= maximum}
        mixed_violation = (
            any(value <= report_count for value in valid)
            and any(value > report_count for value in valid)
        )
    effective = bool(
        (fact_correct if fact_correct is not None else refusal_correct)
        and evidence_correct
    )
    return fact_correct, refusal_correct, evidence_correct, effective, mixed_violation


class OfficialBenchmarkRunnerV3:
    def __init__(
        self,
        client: ProductionClientProtocol,
        models: Sequence[str],
        *,
        clock: Callable[[], float] = time.perf_counter,
        schedule_clock: Callable[[], float] = time.perf_counter,
        sleeper: Callable[[float], Awaitable[None]] = asyncio.sleep,
    ) -> None:
        if tuple(models) != OFFICIAL_MODELS:
            raise ValueError("official model order does not match")
        self.client = client
        self.models = tuple(models)
        self.clock = clock
        self.schedule_clock = schedule_clock
        self.sleeper = sleeper

    async def run(
        self,
        dataset: BenchmarkDatasetV3,
        policy: EvaluationPolicyV3,
    ) -> OfficialRunV3:
        minimum_interval = policy.schedule.minRequestIntervalMs / 1000
        previous_started: float | None = None

        async def pace() -> None:
            nonlocal previous_started
            if previous_started is not None:
                remaining = minimum_interval - (
                    self.schedule_clock() - previous_started
                )
                if remaining > 0:
                    await self.sleeper(remaining)
            previous_started = self.schedule_clock()

        prompts = {
            case.id: (*build_case_prompt_v3(case), case)
            for case in dataset.cases
        }
        warmups: list[WarmupSampleV3] = []
        warmup_case = dataset.cases[0]
        system, user, _ = prompts[warmup_case.id]
        for _ in range(policy.warmupPerModel):
            for model in self.models:
                await pace()
                completion = await self.client.complete(model, system, user)
                warmups.append(
                    WarmupSampleV3(
                        model=model,
                        outcome=completion.outcome,
                        status_code=completion.status_code,
                    )
                )

        await self.sleeper(float(policy.schedule.cooldownBeforeMeasuredSeconds))

        rng = random.Random(policy.seed)
        results: list[OfficialSampleV3] = []
        ordinal = 0
        call_order = 0
        for repetition in range(1, policy.repetitions + 1):
            ordered_cases = list(dataset.cases)
            rng.shuffle(ordered_cases)
            for case in ordered_cases:
                ordinal += 1
                system, user, _ = prompts[case.id]
                hashed = prompt_hash(system, user)
                rotation = (ordinal - 1) % len(self.models)
                ordered_models = self.models[rotation:] + self.models[:rotation]
                for model in ordered_models:
                    await pace()
                    call_order += 1
                    results.append(
                        await self._measure(
                            model,
                            case,
                            repetition,
                            ordinal,
                            call_order,
                            hashed,
                            system,
                            user,
                        )
                    )
        run = OfficialRunV3(warmups=warmups, samples=results)
        validate_result_matrix(dataset, policy, run)
        return run

    async def _measure(
        self,
        model: str,
        case: BenchmarkCaseV3,
        repetition: int,
        ordinal: int,
        call_order: int,
        hashed: str,
        system: str,
        user: str,
    ) -> OfficialSampleV3:
        started = self.clock()
        completion = await self.client.complete(model, system, user)
        elapsed = round(max(0.0, (self.clock() - started) * 1000), 3)
        if completion.outcome != "success":
            return self._failed_sample(
                model,
                case,
                repetition,
                ordinal,
                call_order,
                hashed,
                elapsed,
                completion.outcome,
                completion.status_code,
            )
        if completion.payload is None:
            raise RuntimeError("successful completion must include a payload")
        fact, refusal, evidence, effective, mixed = score_payload_v3(
            case, completion.payload
        )
        unsupported = None
        if case.refusalRubric is not None:
            unsupported = not bool(refusal) or bool(completion.payload.usedSources)
        return OfficialSampleV3(
            model=model,
            case_id=case.id,
            category=case.category,
            repetition=repetition,
            ordinal=ordinal,
            call_order=call_order,
            prompt_hash=hashed,
            elapsed_ms=elapsed,
            outcome="success",
            status_code=completion.status_code,
            used_sources=list(completion.payload.usedSources),
            fact_correct=fact,
            refusal_correct=refusal,
            evidence_correct=evidence,
            effective_task_correct=effective,
            unsupported_answer_violation=unsupported,
            mixed_citation_violation=mixed,
        )

    @staticmethod
    def _failed_sample(
        model: str,
        case: BenchmarkCaseV3,
        repetition: int,
        ordinal: int,
        call_order: int,
        hashed: str,
        elapsed: float,
        outcome: str,
        status_code: int | None,
    ) -> OfficialSampleV3:
        if outcome not in CANONICAL_OUTCOMES - {"success"}:
            raise ValueError("unknown canonical completion outcome")
        return OfficialSampleV3(
            model,
            case.id,
            case.category,
            repetition,
            ordinal,
            call_order,
            hashed,
            elapsed,
            outcome,
            status_code,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
        )


def _rate(numerator: int, denominator: int) -> dict[str, Any]:
    return {
        "numerator": numerator,
        "denominator": denominator,
        "rate": None if denominator == 0 else round(numerator / denominator, 6),
    }


def _nearest_rank(values: Sequence[float], quantile: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    return round(ordered[max(0, math.ceil(quantile * len(ordered)) - 1)], 3)


def validate_result_matrix(
    dataset: BenchmarkDatasetV3,
    policy: EvaluationPolicyV3,
    run: OfficialRunV3,
) -> None:
    expected_cells = {
        (model, case.id, repetition)
        for model in policy.models
        for case in dataset.cases
        for repetition in range(1, policy.repetitions + 1)
    }
    actual_cells = [
        (sample.model, sample.case_id, sample.repetition)
        for sample in run.samples
    ]
    if len(actual_cells) != 180 or len(set(actual_cells)) != len(actual_cells):
        raise ValueError("official result matrix has duplicate or wrong-sized cells")
    if set(actual_cells) != expected_cells:
        raise ValueError("official result matrix has missing or unknown cells")
    expected_warmups = Counter(
        {model: policy.warmupPerModel for model in policy.models}
    )
    if Counter(sample.model for sample in run.warmups) != expected_warmups:
        raise ValueError("official warmup matrix does not match")
    if len(run.warmups) != len(policy.models) * policy.warmupPerModel:
        raise ValueError("official warmup count does not match")
    if set(sample.call_order for sample in run.samples) != set(range(1, 181)):
        raise ValueError("official call order is not exact")
    if any(
        sample.outcome not in CANONICAL_OUTCOMES
        for sample in [*run.warmups, *run.samples]
    ):
        raise ValueError("official results contain an unknown outcome")

    cases = {case.id: case for case in dataset.cases}
    expected_prompts = prompt_manifest_v3(dataset)
    ordinal_cells: dict[tuple[int, str], set[int]] = {}
    for sample in run.samples:
        case = cases[sample.case_id]
        if sample.category != case.category:
            raise ValueError("sample category does not match dataset")
        if sample.prompt_hash != expected_prompts[sample.case_id]:
            raise ValueError("sample prompt hash does not match dataset")
        ordinal_cells.setdefault(
            (sample.repetition, sample.case_id), set()
        ).add(sample.ordinal)
        evaluation_fields = (
            sample.used_sources,
            sample.fact_correct,
            sample.refusal_correct,
            sample.evidence_correct,
            sample.effective_task_correct,
            sample.unsupported_answer_violation,
            sample.mixed_citation_violation,
        )
        if sample.outcome == "success":
            if sample.used_sources is None or sample.evidence_correct is None:
                raise ValueError("successful sample is missing evaluation fields")
        elif any(value is not None for value in evaluation_fields):
            raise ValueError("failed sample contains answer evaluation fields")
    if len(ordinal_cells) != policy.caseCount * policy.repetitions:
        raise ValueError("ordinal matrix does not match")
    if any(len(values) != 1 for values in ordinal_cells.values()):
        raise ValueError("models disagree on case ordinal")
    ordinal_counts = Counter(
        next(iter(values)) for values in ordinal_cells.values()
    )
    if set(ordinal_counts) != set(range(1, 61)) or any(
        count != 1 for count in ordinal_counts.values()
    ):
        raise ValueError("case ordinals are not exact")


def aggregate_official_v3(
    results: Sequence[OfficialSampleV3],
    policy: EvaluationPolicyV3,
) -> dict[str, dict[str, Any]]:
    aggregated: dict[str, dict[str, Any]] = {}
    for model in policy.models:
        samples = [sample for sample in results if sample.model == model]
        successful = [sample for sample in samples if sample.outcome == "success"]
        fact = [sample for sample in successful if sample.fact_correct is not None]
        refusal = [sample for sample in successful if sample.refusal_correct is not None]
        long_successful = [
            sample for sample in successful if sample.category == "long_report"
        ]
        long_scheduled = [
            sample for sample in samples if sample.category == "long_report"
        ]
        refusal_scheduled = [
            sample for sample in samples if sample.category == "insufficient"
        ]
        mixed_scheduled = [
            sample for sample in samples if sample.category == "mixed"
        ]
        mixed = [
            sample
            for sample in successful
            if sample.mixed_citation_violation is not None
        ]
        case_rates: list[float] = []
        for case_id in sorted({sample.case_id for sample in samples}):
            case_samples = [
                sample for sample in samples if sample.case_id == case_id
            ]
            case_rates.append(
                sum(
                    sample.effective_task_correct is True
                    for sample in case_samples
                )
                / len(case_samples)
            )

        safe_completion = _rate(
            sum(
                sample.outcome == "success"
                and sample.unsupported_answer_violation is False
                for sample in refusal_scheduled
            ),
            len(refusal_scheduled),
        )
        mixed_compliant = _rate(
            sum(
                sample.outcome == "success"
                and sample.mixed_citation_violation is False
                for sample in mixed_scheduled
            ),
            len(mixed_scheduled),
        )
        compliance_rates = (
            safe_completion["rate"],
            mixed_compliant["rate"],
        )
        if any(value is None for value in compliance_rates):
            safety_mixed = {"numerator": None, "denominator": 2, "rate": None}
        else:
            numerator = round(sum(compliance_rates), 6)
            safety_mixed = {
                "numerator": numerator,
                "denominator": 2,
                "rate": round(numerator / 2, 6),
            }

        latencies = [sample.elapsed_ms for sample in successful]
        failures = Counter(
            sample.outcome for sample in samples if sample.outcome != "success"
        )
        aggregated[model] = {
            "scheduled_samples": len(samples),
            "successful_responses": len(successful),
            "completion_rate": _rate(len(successful), len(samples)),
            "failure_rate": _rate(len(samples) - len(successful), len(samples)),
            "failure_kinds": dict(sorted(failures.items())),
            "fact_accuracy_conditional": _rate(
                sum(sample.fact_correct is True for sample in fact), len(fact)
            ),
            "refusal_accuracy_conditional": _rate(
                sum(sample.refusal_correct is True for sample in refusal),
                len(refusal),
            ),
            "evidence_accuracy_conditional": _rate(
                sum(sample.evidence_correct is True for sample in successful),
                len(successful),
            ),
            "effective_task_accuracy_conditional": _rate(
                sum(
                    sample.effective_task_correct is True
                    for sample in successful
                ),
                len(successful),
            ),
            "effective_task_case_macro": {
                "numerator": round(sum(case_rates), 6),
                "denominator": len(case_rates),
                "rate": (
                    None
                    if not case_rates
                    else round(sum(case_rates) / len(case_rates), 6)
                ),
            },
            "long_accuracy_conditional": _rate(
                sum(
                    sample.effective_task_correct is True
                    for sample in long_successful
                ),
                len(long_successful),
            ),
            "long_task_success": _rate(
                sum(
                    sample.effective_task_correct is True
                    for sample in long_scheduled
                ),
                len(long_scheduled),
            ),
            "unsupported_answer_violation_rate_conditional": _rate(
                sum(
                    sample.unsupported_answer_violation is True
                    for sample in refusal
                ),
                len(refusal),
            ),
            "mixed_citation_violation_rate_conditional": _rate(
                sum(
                    sample.mixed_citation_violation is True
                    for sample in mixed
                ),
                len(mixed),
            ),
            "safe_completion": safe_completion,
            "mixed_compliant_completion": mixed_compliant,
            "safety_mixed_compliance": safety_mixed,
            "latency_ms": {
                "successful_n": len(latencies),
                "p50": _nearest_rank(latencies, 0.50),
                "p95": _nearest_rank(latencies, 0.95),
                "method": "nearest-rank-successful-responses",
            },
        }
    return aggregated


def _passes(
    value: float | None,
    *,
    minimum: float | None = None,
    maximum: float | None = None,
    exclusive_maximum: float | None = None,
) -> bool:
    if value is None:
        return False
    if minimum is not None and value < minimum:
        return False
    if maximum is not None and value > maximum:
        return False
    if exclusive_maximum is not None and value >= exclusive_maximum:
        return False
    return True


def build_official_summary_v3(
    *,
    dataset: BenchmarkDatasetV3,
    dataset_hash: str,
    policy: EvaluationPolicyV3,
    policy_hash: str,
    run: OfficialRunV3,
    run_id: str,
    started_at: datetime,
    finished_at: datetime,
) -> dict[str, Any]:
    validate_result_matrix(dataset, policy, run)
    models = aggregate_official_v3(run.samples, policy)
    measured_rate_limits = sum(
        metrics["failure_kinds"].get("rate-limit", 0)
        for metrics in models.values()
    )
    warmup_failures = Counter(sample.outcome for sample in run.warmups)
    warmup_rate_limits = warmup_failures.get("rate-limit", 0)
    any_rate_limit = measured_rate_limits + warmup_rate_limits > 0

    for metrics in models.values():
        gates = policy.gates
        checks = {
            "completion_rate": _passes(
                metrics["completion_rate"]["rate"],
                minimum=gates.completionRateMin,
            ),
            "effective_task_case_macro": _passes(
                metrics["effective_task_case_macro"]["rate"],
                minimum=gates.effectiveTaskMacroMin,
            ),
            "long_accuracy": _passes(
                metrics["long_task_success"]["rate"],
                minimum=gates.longAccuracyMin,
            ),
            "unsupported_violation": _passes(
                metrics["unsupported_answer_violation_rate_conditional"]["rate"],
                maximum=gates.unsupportedViolationMax,
            ),
            "mixed_violation": _passes(
                metrics["mixed_citation_violation_rate_conditional"]["rate"],
                maximum=gates.mixedViolationMax,
            ),
            "safety_mixed_compliance": _passes(
                metrics["safety_mixed_compliance"]["rate"],
                minimum=gates.safetyMixedComplianceMin,
            ),
            "p95": _passes(
                metrics["latency_ms"]["p95"],
                exclusive_maximum=gates.p95MsExclusiveMax,
            ),
        }
        metrics["gate_checks"] = checks
        metrics["gate_pass"] = all(checks.values())
        metrics["project_fit_score"] = None

    eligible_p95 = [
        metrics["latency_ms"]["p95"]
        for metrics in models.values()
        if metrics["gate_pass"] and metrics["latency_ms"]["p95"] is not None
    ]
    fastest = min(eligible_p95) if eligible_p95 else None
    for metrics in models.values():
        p95 = metrics["latency_ms"]["p95"]
        if metrics["gate_pass"] and fastest is not None and p95:
            weights = policy.scoreWeights
            metrics["project_fit_score"] = round(
                100
                * (
                    weights.effectiveTaskMacro
                    * metrics["effective_task_case_macro"]["rate"]
                    + weights.longAccuracy
                    * metrics["long_task_success"]["rate"]
                    + weights.safetyMixedCompliance
                    * metrics["safety_mixed_compliance"]["rate"]
                    + weights.relativeLatency * (fastest / p95)
                ),
                3,
            )

    leaderboard: list[dict[str, Any]] = []
    if not any_rate_limit:
        eligible = [
            (model, metrics["project_fit_score"])
            for model, metrics in models.items()
            if metrics["project_fit_score"] is not None
        ]
        eligible.sort(key=lambda item: (-item[1], item[0]))
        leaderboard = [
            {"rank": rank, "model": model, "project_fit_score": score}
            for rank, (model, score) in enumerate(eligible, 1)
        ]

    prompt_hashes = prompt_manifest_v3(dataset)
    return {
        "schema_version": 3,
        "run_id": run_id,
        "started_at_utc": started_at.isoformat(),
        "finished_at_utc": finished_at.isoformat(),
        "dataset": {
            "name": dataset.name,
            "case_count": len(dataset.cases),
            "sha256": dataset_hash,
            "case_prompt_sha256": prompt_hashes,
            "prompt_manifest_sha256": _canonical_json_sha256(prompt_hashes),
        },
        "policy": {"id": policy.policyId, "sha256": policy_hash},
        "config": {
            "models": list(policy.models),
            "provider_modes": policy.providerModes.model_dump(),
            "repetitions": policy.repetitions,
            "warmup_per_model": policy.warmupPerModel,
            "measured_calls": len(run.samples),
            "total_calls": len(run.samples) + len(run.warmups),
            "temperature": policy.temperature,
            "retry_count": policy.retryCount,
            "anthropic_max_tokens": policy.anthropicMaxTokens,
            "seed": policy.seed,
            "parser": policy.parser,
            "single_lane": policy.schedule.singleLane,
            "latin_square": policy.schedule.latinSquare,
            "min_request_interval_ms": policy.schedule.minRequestIntervalMs,
            "cooldown_before_measured_seconds": (
                policy.schedule.cooldownBeforeMeasuredSeconds
            ),
        },
        "warmup": {
            "scheduled": len(run.warmups),
            "outcome_counts": dict(sorted(warmup_failures.items())),
            "rate_limit_count": warmup_rate_limits,
        },
        "metric_basis": {
            "completion_and_failures": "all-scheduled-samples",
            "conditional_quality": "common-schema-valid-successful-responses",
            "effective_macro": "equal-weight-mean-of-scheduled-case-success-rates",
            "long_task": "all-scheduled-long-report-samples",
            "safety_mixed": "mean-of-safe-and-mixed-compliant-scheduled-rates",
        },
        "quality_comparison_valid": not any_rate_limit,
        "quality_comparison_invalid_reason": (
            "rate-limit-observed" if any_rate_limit else None
        ),
        "models": models,
        "leaderboard": leaderboard,
    }


def _summary_csv(summary: Mapping[str, Any]) -> str:
    output = io.StringIO(newline="")
    writer = csv.writer(output)
    writer.writerow(
        [
            "model",
            "completion_numerator",
            "completion_denominator",
            "completion_rate",
            "fact_accuracy_numerator",
            "fact_accuracy_denominator",
            "fact_accuracy_rate",
            "refusal_accuracy_numerator",
            "refusal_accuracy_denominator",
            "refusal_accuracy_rate",
            "evidence_accuracy_numerator",
            "evidence_accuracy_denominator",
            "evidence_accuracy_rate",
            "effective_macro_numerator",
            "effective_macro_denominator",
            "effective_macro_rate",
            "long_task_numerator",
            "long_task_denominator",
            "long_task_rate",
            "safe_completion_numerator",
            "safe_completion_denominator",
            "safe_completion_rate",
            "mixed_compliant_numerator",
            "mixed_compliant_denominator",
            "mixed_compliant_rate",
            "safety_mixed_numerator",
            "safety_mixed_denominator",
            "safety_mixed_rate",
            "unsupported_numerator",
            "unsupported_denominator",
            "unsupported_rate",
            "mixed_numerator",
            "mixed_denominator",
            "mixed_rate",
            "p95_ms",
            "p95_n",
            "gate_pass",
            "project_fit_score",
        ]
    )
    metric_names = (
        "completion_rate",
        "fact_accuracy_conditional",
        "refusal_accuracy_conditional",
        "evidence_accuracy_conditional",
        "effective_task_case_macro",
        "long_task_success",
        "safe_completion",
        "mixed_compliant_completion",
        "safety_mixed_compliance",
        "unsupported_answer_violation_rate_conditional",
        "mixed_citation_violation_rate_conditional",
    )
    for model, metrics in summary["models"].items():
        row: list[Any] = [model]
        for name in metric_names:
            metric = metrics[name]
            row.extend(
                [metric["numerator"], metric["denominator"], metric["rate"]]
            )
        row.extend(
            [
                metrics["latency_ms"]["p95"],
                metrics["latency_ms"]["successful_n"],
                metrics["gate_pass"],
                metrics["project_fit_score"],
            ]
        )
        writer.writerow(row)
    return output.getvalue()


def _percent(metric: Mapping[str, Any]) -> str:
    value = metric.get("rate")
    return (
        "N/A"
        if value is None
        else f"{100 * value:.1f}% ({metric.get('numerator')} / {metric.get('denominator')})"
    )


def _summary_markdown(summary: Mapping[str, Any]) -> str:
    lines = [
        "# GMS three-model production-fit evaluation v3",
        "",
        f"- Run: `{summary['run_id']}`",
        f"- Quality comparison valid: `{str(summary['quality_comparison_valid']).lower()}`",
        "- Completion, effective macro, long task, safe completion and mixed compliance use scheduled denominators.",
        "- Fact, refusal, evidence and violation diagnostics are conditional on common-schema success.",
        "- Latency is the provider-adapter call; p95 is nearest-rank over successful responses.",
        "",
        "| Model | Completion | Fact accuracy | Evidence accuracy | Effective macro | Long task | Safe completion | Mixed-compliant | Unsupported violation | Mixed violation | p95 (n) | Gate | Score |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|:---:|---:|",
    ]
    for model, metrics in summary["models"].items():
        p95 = metrics["latency_ms"]["p95"]
        n = metrics["latency_ms"]["successful_n"]
        lines.append(
            f"| {model} | {_percent(metrics['completion_rate'])} | "
            f"{_percent(metrics['fact_accuracy_conditional'])} | "
            f"{_percent(metrics['evidence_accuracy_conditional'])} | "
            f"{_percent(metrics['effective_task_case_macro'])} | "
            f"{_percent(metrics['long_task_success'])} | "
            f"{_percent(metrics['safe_completion'])} | "
            f"{_percent(metrics['mixed_compliant_completion'])} | "
            f"{_percent(metrics['unsupported_answer_violation_rate_conditional'])} | "
            f"{_percent(metrics['mixed_citation_violation_rate_conditional'])} | "
            f"{p95} ({n}) | "
            f"{'PASS' if metrics['gate_pass'] else 'FAIL'} | "
            f"{metrics['project_fit_score']} |"
        )
    if summary["quality_comparison_valid"]:
        lines.extend(["", "## Leaderboard", ""])
        if summary["leaderboard"]:
            lines.extend(
                f"{row['rank']}. {row['model']} — {row['project_fit_score']}"
                for row in summary["leaderboard"]
            )
        else:
            lines.append("No model passed every pre-registered gate.")
    else:
        lines.extend(
            ["", "Leaderboard withheld because warmup or measurement observed HTTP 429."]
        )
    lines.extend(
        [
            "",
            "Synthetic 20-case production-adapter benchmark; this is not an end-to-end service SLA.",
            "",
        ]
    )
    return "\n".join(lines)


def validate_summary_consistency(
    summary: Mapping[str, Any],
    *,
    dataset: BenchmarkDatasetV3,
    dataset_hash: str,
    policy: EvaluationPolicyV3,
    policy_hash: str,
    run: OfficialRunV3,
) -> None:
    expected = build_official_summary_v3(
        dataset=dataset,
        dataset_hash=dataset_hash,
        policy=policy,
        policy_hash=policy_hash,
        run=run,
        run_id=str(summary["run_id"]),
        started_at=datetime.fromisoformat(str(summary["started_at_utc"])),
        finished_at=datetime.fromisoformat(str(summary["finished_at_utc"])),
    )
    if dict(summary) != expected:
        raise ValueError("summary does not match official samples")


def write_official_results_v3(
    output_root: Path,
    summary: Mapping[str, Any],
    *,
    dataset: BenchmarkDatasetV3,
    dataset_hash: str,
    policy: EvaluationPolicyV3,
    policy_hash: str,
    run: OfficialRunV3,
) -> Path:
    validate_result_matrix(dataset, policy, run)
    validate_summary_consistency(
        summary,
        dataset=dataset,
        dataset_hash=dataset_hash,
        policy=policy,
        policy_hash=policy_hash,
        run=run,
    )
    output_root.mkdir(parents=True, exist_ok=True)
    final_dir = output_root / str(summary["run_id"])
    staging = output_root / f".{summary['run_id']}.staging"
    if final_dir.exists() or staging.exists():
        raise FileExistsError("run output already exists")
    staging.mkdir()
    try:
        payloads = {
            "samples.jsonl": "".join(
                json.dumps(asdict(sample), ensure_ascii=False, sort_keys=True)
                + "\n"
                for sample in run.samples
            ),
            "summary.json": json.dumps(
                summary, ensure_ascii=False, indent=2, sort_keys=True
            )
            + "\n",
            "summary.csv": _summary_csv(summary),
            "summary.md": _summary_markdown(summary),
        }
        if payloads["samples.jsonl"].count("\n") != 180:
            raise ValueError("sample line count does not match")
        checksums: list[str] = []
        for name, content in payloads.items():
            data = content.encode("utf-8")
            (staging / name).write_bytes(data)
            checksums.append(f"{_sha256(data)}  {name}")
        checksum_bytes = ("\n".join(checksums) + "\n").encode("utf-8")
        (staging / "checksums.sha256").write_bytes(checksum_bytes)
        (staging / "COMPLETE").write_text(
            f"{_sha256(checksum_bytes)}  checksums.sha256\n",
            encoding="utf-8",
            newline="\n",
        )
        staging.replace(final_dir)
    except Exception:
        if staging.exists():
            shutil.rmtree(staging)
        raise
    return final_dir


def validate_official_profile_v3(
    *,
    dataset: BenchmarkDatasetV3,
    dataset_hash: str,
    policy: EvaluationPolicyV3,
    arguments: argparse.Namespace,
) -> None:
    if dataset_hash != policy.datasetSha256:
        raise ValueError("official dataset hash does not match policy")
    if len(dataset.cases) != policy.caseCount:
        raise ValueError("official dataset requires exactly 20 cases")
    if Counter(case.category for case in dataset.cases) != EXPECTED_CATEGORY_DISTRIBUTION:
        raise ValueError("official category distribution does not match")
    manifest = prompt_manifest_v3(dataset)
    if manifest != policy.promptSha256ByCase:
        raise ValueError("official prompt manifest does not match")
    if _canonical_json_sha256(manifest) != policy.promptManifestSha256:
        raise ValueError("official prompt manifest hash does not match")
    if (
        _canonical_json_sha256(llm_answer_json_schema())
        != policy.responseSchemaSha256
    ):
        raise ValueError("official response schema hash does not match")
    if _sha256(Path(__file__).read_bytes()) != policy.evaluatorSha256:
        raise ValueError("official evaluator hash does not match")
    expected = {
        "repetitions": policy.repetitions,
        "warmup": policy.warmupPerModel,
        "seed": policy.seed,
        "min_request_interval_ms": policy.schedule.minRequestIntervalMs,
        "cooldown_seconds": policy.schedule.cooldownBeforeMeasuredSeconds,
    }
    if any(getattr(arguments, name) != value for name, value in expected.items()):
        raise ValueError("official runtime arguments do not match pinned policy")
    calculated = (
        len(dataset.cases) * arguments.repetitions + arguments.warmup
    ) * len(policy.models)
    if calculated != policy.totalCalls or calculated != 183:
        raise ValueError("official benchmark must make exactly 183 calls")


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Run the pinned GMS production-fit evaluation v3"
    )
    parser.add_argument("--official", action="store_true", required=True)
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--dataset", type=Path, default=DATASET_V3_PATH)
    parser.add_argument("--policy", type=Path, default=POLICY_V3_PATH)
    parser.add_argument("--repetitions", type=int, default=3)
    parser.add_argument("--warmup", type=int, default=1)
    parser.add_argument("--seed", type=int, default=20260809)
    parser.add_argument("--min-request-interval-ms", type=int, default=6500)
    parser.add_argument("--cooldown-seconds", type=int, default=300)
    parser.add_argument(
        "--output-dir", type=Path, default=Path("../output/model-eval")
    )
    return parser


async def _run_cli(arguments: argparse.Namespace) -> Path:
    dataset, dataset_hash = load_dataset_v3(arguments.dataset)
    policy, policy_hash = load_policy_v3(arguments.policy)
    validate_official_profile_v3(
        dataset=dataset,
        dataset_hash=dataset_hash,
        policy=policy,
        arguments=arguments,
    )
    if arguments.env_file is None or not arguments.env_file.is_file():
        raise ValueError("official benchmark requires an env file")
    settings = GmsModelSuiteSettings.from_mapping(
        dotenv_values(arguments.env_file)
    )
    if (
        settings.gemini_model,
        settings.openai_model,
        settings.anthropic_model,
    ) != tuple(policy.models):
        raise ValueError("configured model ids do not match official policy")
    if settings.anthropic_max_tokens != policy.anthropicMaxTokens:
        raise ValueError("configured Anthropic max tokens do not match policy")
    if settings.anthropic_version != policy.anthropicVersion:
        raise ValueError("configured Anthropic version does not match policy")

    providers = create_gms_production_fit_suite(
        settings,
        response_schema=CHATBOT_ANSWER_RESPONSE_SCHEMA,
    )
    client = GmsProductionClient(providers)
    started = datetime.now(timezone.utc)
    run_id = started.strftime("%Y%m%dT%H%M%S%fZ")
    run = await OfficialBenchmarkRunnerV3(client, policy.models).run(
        dataset, policy
    )
    finished = datetime.now(timezone.utc)
    summary = build_official_summary_v3(
        dataset=dataset,
        dataset_hash=dataset_hash,
        policy=policy,
        policy_hash=policy_hash,
        run=run,
        run_id=run_id,
        started_at=started,
        finished_at=finished,
    )
    return write_official_results_v3(
        arguments.output_dir,
        summary,
        dataset=dataset,
        dataset_hash=dataset_hash,
        policy=policy,
        policy_hash=policy_hash,
        run=run,
    )


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
