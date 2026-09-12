"""Pre-registered project-fit benchmark for the three SSAFY GMS models."""

from __future__ import annotations

import argparse
import asyncio
import csv
import hashlib
import io
import json
import math
import os
import random
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

from dotenv import dotenv_values
from pydantic import BaseModel, ConfigDict, Field, StrictInt, ValidationError, model_validator

from app.evaluation.gms_raw_client import GmsRawClient, RawCompletion
from app.prompts.chatbot_prompt import SYSTEM_PROMPT, build_user_prompt
from app.providers.gms_model_suite import (
    ANTHROPIC_MODEL,
    GEMINI_MODEL,
    OPENAI_MODEL,
    GmsModelSuiteSettings,
)
from app.providers.web_search_provider import WebSearchResult
from app.rag.search import ApartmentProfile, ReportChunkHit
from app.schemas.chatbot import INSUFFICIENT_EVIDENCE_ANSWER, LlmAnswerPayload


ROOT = Path(__file__).resolve().parent
DATASET_V2_PATH = ROOT / "datasets" / "chatbot_grounding_v2.json"
POLICY_V2_PATH = ROOT / "evaluation_policy_v2.json"
OFFICIAL_MODELS = (GEMINI_MODEL, OPENAI_MODEL, ANTHROPIC_MODEL)
OFFICIAL_POLICY_SHA256 = "60296a3a9a1852d20a270a31e36cf9709e60c9c360b27808b9e9d0381ca90466"
CANONICAL_OUTCOMES = frozenset(
    {"transport", "rate-limit", "policy", "incomplete", "json", "schema", "success"}
)


class ProfileV2(BaseModel):
    model_config = ConfigDict(extra="forbid")
    name: str
    address: str | None = None
    districtName: str | None = None
    dongName: str | None = None
    householdCount: int | None = Field(default=None, ge=0)
    completionYearMonth: str | None = None
    parkingSpaceCount: int | None = Field(default=None, ge=0)


class ReportEvidenceV2(BaseModel):
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


class WebEvidenceV2(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str
    snippet: str
    url: str
    domain: str
    retrievedAt: datetime


class FactRubric(BaseModel):
    model_config = ConfigDict(extra="forbid")
    factGroups: list[list[str]] = Field(min_length=1)
    forbiddenTerms: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def non_blank_terms(self) -> "FactRubric":
        if any(not group or any(not term.strip() for term in group) for group in self.factGroups):
            raise ValueError("fact groups require non-blank alternatives")
        if any(not term.strip() for term in self.forbiddenTerms):
            raise ValueError("forbidden terms cannot be blank")
        return self


class RefusalRubric(BaseModel):
    model_config = ConfigDict(extra="forbid")
    markers: list[str] = Field(min_length=1)
    forbiddenClaims: list[str] = Field(default_factory=list)


class BenchmarkCaseV2(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: str = Field(pattern=r"^[a-z0-9][a-z0-9-]*$")
    category: Literal["direct_report", "long_report", "web", "insufficient", "profile", "mixed"]
    question: str = Field(min_length=1)
    profile: ProfileV2 | None = None
    reportEvidence: list[ReportEvidenceV2] = Field(default_factory=list)
    webEvidence: list[WebEvidenceV2] = Field(default_factory=list)
    factRubric: FactRubric | None
    refusalRubric: RefusalRubric | None
    acceptableSourceSets: list[list[StrictInt]] = Field(min_length=1)
    longContextChars: int | None = Field(default=None, ge=8000, le=29000)

    @model_validator(mode="after")
    def validate_case(self) -> "BenchmarkCaseV2":
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
        if self.refusalRubric is not None and [] not in self.acceptableSourceSets:
            raise ValueError("refusal cases must accept no sources")
        if self.category == "long_report":
            if self.longContextChars not in {8000, 20000, 29000}:
                raise ValueError("long cases must pin 8k, 20k, or 29k")
            evidence_chars = sum(len(item.expanded_content()) for item in self.reportEvidence)
            if evidence_chars < self.longContextChars:
                raise ValueError("expanded long evidence is shorter than its label")
        elif self.longContextChars is not None:
            raise ValueError("only long cases may set longContextChars")
        return self


class BenchmarkDatasetV2(BaseModel):
    model_config = ConfigDict(extra="forbid")
    schemaVersion: Literal[2]
    name: str
    cases: list[BenchmarkCaseV2]

    @model_validator(mode="after")
    def unique_ids(self) -> "BenchmarkDatasetV2":
        ids = [case.id for case in self.cases]
        if len(ids) != len(set(ids)):
            raise ValueError("case ids must be unique")
        return self


class SchedulePolicy(BaseModel):
    model_config = ConfigDict(extra="forbid")
    singleLane: Literal[True]
    latinSquare: Literal[True]
    minRequestIntervalMs: int = Field(ge=0)
    cooldownBeforeMeasuredSeconds: int = Field(ge=0)


class GatePolicy(BaseModel):
    model_config = ConfigDict(extra="forbid")
    completionRateMin: float
    effectiveTaskMacroMin: float
    longAccuracyMin: float
    unsupportedViolationMax: float
    mixedViolationMax: float
    safetyMixedComplianceMin: float
    p95MsExclusiveMax: int


class ScoreWeights(BaseModel):
    model_config = ConfigDict(extra="forbid")
    effectiveTaskMacro: float
    longAccuracy: float
    safetyMixedCompliance: float
    relativeLatency: float


class EvaluationPolicyV2(BaseModel):
    model_config = ConfigDict(extra="forbid")
    schemaVersion: Literal[2]
    policyId: Literal["gms-project-fit-v2-official"]
    datasetFile: Literal["datasets/chatbot_grounding_v2.json"]
    datasetSha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    models: list[str]
    caseCount: int
    repetitions: int
    warmupPerModel: int
    totalCalls: int
    temperature: float
    retryCount: Literal[0]
    seed: int
    parser: Literal["strict-json-object-v1"]
    schedule: SchedulePolicy
    gates: GatePolicy
    scoreWeights: ScoreWeights

    @model_validator(mode="after")
    def validate_fixed_profile(self) -> "EvaluationPolicyV2":
        if tuple(self.models) != OFFICIAL_MODELS:
            raise ValueError("official model order does not match")
        if self.caseCount != 20 or self.repetitions != 3 or self.warmupPerModel != 1:
            raise ValueError("official count profile does not match")
        if self.totalCalls != 183:
            raise ValueError("official call count must be 183")
        if self.temperature != 0.2 or self.seed != 20260809:
            raise ValueError("official generation profile does not match")
        if self.schedule.minRequestIntervalMs != 6500:
            raise ValueError("official pacing must be 6500ms")
        if self.schedule.cooldownBeforeMeasuredSeconds != 300:
            raise ValueError("official cooldown must be 300 seconds")
        weights = self.scoreWeights
        if not math.isclose(
            weights.effectiveTaskMacro + weights.longAccuracy
            + weights.safetyMixedCompliance + weights.relativeLatency,
            1.0,
        ):
            raise ValueError("score weights must sum to 1")
        return self


@dataclass(frozen=True)
class OfficialSample:
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


class RawClient(Protocol):
    async def complete_raw(self, model: str, system_prompt: str, user_prompt: str) -> RawCompletion: ...


def _sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def load_dataset_v2(path: Path = DATASET_V2_PATH) -> tuple[BenchmarkDatasetV2, str]:
    raw = path.read_bytes()
    return BenchmarkDatasetV2.model_validate(json.loads(raw.decode("utf-8"))), _sha256(raw)


def load_policy_v2(path: Path = POLICY_V2_PATH) -> tuple[EvaluationPolicyV2, str]:
    raw = path.read_bytes()
    return EvaluationPolicyV2.model_validate(json.loads(raw.decode("utf-8"))), _sha256(raw)


def build_case_prompt_v2(case: BenchmarkCaseV2) -> tuple[str, str]:
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


def strict_parse_payload(text: str | None) -> tuple[Literal["json", "schema", "success"], LlmAnswerPayload | None]:
    if text is None:
        return "json", None
    try:
        value = json.loads(text.strip())
    except (json.JSONDecodeError, ValueError):
        return "json", None
    if not isinstance(value, dict):
        return "schema", None
    try:
        return "success", LlmAnswerPayload.model_validate(value)
    except (ValidationError, TypeError):
        return "schema", None


def _normalize(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value).casefold()
    return " ".join(normalized.split())


def score_payload_v2(
    case: BenchmarkCaseV2,
    payload: LlmAnswerPayload,
) -> tuple[bool | None, bool | None, bool, bool, bool | None]:
    answer = _normalize(payload.answer)
    fact_correct = None
    refusal_correct = None
    if case.factRubric is not None:
        rubric = case.factRubric
        fact_correct = all(
            any(_normalize(alternative) in answer for alternative in group)
            for group in rubric.factGroups
        ) and all(_normalize(term) not in answer for term in rubric.forbiddenTerms)
    else:
        assert case.refusalRubric is not None
        rubric = case.refusalRubric
        refusal_correct = (
            _normalize(INSUFFICIENT_EVIDENCE_ANSWER) in answer
            or any(_normalize(marker) in answer for marker in rubric.markers)
        )
        refusal_correct = refusal_correct and all(
            _normalize(claim) not in answer for claim in rubric.forbiddenClaims
        )

    selected = payload.usedSources
    selected_set = frozenset(selected)
    acceptable = {frozenset(values) for values in case.acceptableSourceSets}
    evidence_correct = len(selected) == len(selected_set) and selected_set in acceptable
    unsupported = False
    if case.refusalRubric is not None:
        unsupported = not bool(refusal_correct) or bool(selected)

    mixed_violation = None
    report_count = len(case.reportEvidence)
    if report_count and case.webEvidence:
        maximum = report_count + len(case.webEvidence)
        valid = {value for value in selected if 1 <= value <= maximum}
        mixed_violation = (
            any(value <= report_count for value in valid)
            and any(value > report_count for value in valid)
        )
    effective = bool((fact_correct if fact_correct is not None else refusal_correct) and evidence_correct)
    return fact_correct, refusal_correct, evidence_correct, effective, mixed_violation


class OfficialBenchmarkRunner:
    def __init__(
        self,
        client: RawClient,
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

    async def run(self, dataset: BenchmarkDatasetV2, policy: EvaluationPolicyV2) -> list[OfficialSample]:
        minimum_interval = policy.schedule.minRequestIntervalMs / 1000
        previous_started: float | None = None

        async def pace() -> None:
            nonlocal previous_started
            if previous_started is not None:
                remaining = minimum_interval - (self.schedule_clock() - previous_started)
                if remaining > 0:
                    await self.sleeper(remaining)
            previous_started = self.schedule_clock()

        prompts = {case.id: (*build_case_prompt_v2(case), case) for case in dataset.cases}
        warmup_case = dataset.cases[0]
        system, user, _ = prompts[warmup_case.id]
        for model in self.models:
            await pace()
            try:
                await self.client.complete_raw(model, system, user)
            except Exception:
                pass

        await self.sleeper(float(policy.schedule.cooldownBeforeMeasuredSeconds))

        rng = random.Random(policy.seed)
        results: list[OfficialSample] = []
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
                            model, case, repetition, ordinal, call_order, hashed, system, user
                        )
                    )
        return results

    async def _measure(
        self,
        model: str,
        case: BenchmarkCaseV2,
        repetition: int,
        ordinal: int,
        call_order: int,
        hashed: str,
        system: str,
        user: str,
    ) -> OfficialSample:
        started = self.clock()
        try:
            completion = await self.client.complete_raw(model, system, user)
        except Exception:
            completion = RawCompletion("transport")
        elapsed = round(max(0.0, (self.clock() - started) * 1000), 3)
        if completion.outcome != "success":
            return self._sample(
                model, case, repetition, ordinal, call_order, hashed, elapsed,
                completion.outcome, completion.status_code,
            )
        parse_outcome, payload = strict_parse_payload(completion.text)
        if payload is None:
            return self._sample(
                model, case, repetition, ordinal, call_order, hashed, elapsed,
                parse_outcome, completion.status_code,
            )
        fact, refusal, evidence, effective, mixed = score_payload_v2(case, payload)
        unsupported = None
        if case.refusalRubric is not None:
            unsupported = not bool(refusal) or bool(payload.usedSources)
        return OfficialSample(
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
            used_sources=list(payload.usedSources),
            fact_correct=fact,
            refusal_correct=refusal,
            evidence_correct=evidence,
            effective_task_correct=effective,
            unsupported_answer_violation=unsupported,
            mixed_citation_violation=mixed,
        )

    @staticmethod
    def _sample(
        model: str,
        case: BenchmarkCaseV2,
        repetition: int,
        ordinal: int,
        call_order: int,
        hashed: str,
        elapsed: float,
        outcome: str,
        status_code: int | None,
    ) -> OfficialSample:
        return OfficialSample(
            model, case.id, case.category, repetition, ordinal, call_order,
            hashed, elapsed, outcome, status_code, None,
            None, None, None, None, None, None,
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


def aggregate_official(
    results: Sequence[OfficialSample],
    policy: EvaluationPolicyV2,
) -> dict[str, dict[str, Any]]:
    aggregated: dict[str, dict[str, Any]] = {}
    for model in policy.models:
        samples = [sample for sample in results if sample.model == model]
        successful = [sample for sample in samples if sample.outcome == "success"]
        fact = [sample for sample in successful if sample.fact_correct is not None]
        refusal = [sample for sample in successful if sample.refusal_correct is not None]
        long = [sample for sample in successful if sample.category == "long_report"]
        long_scheduled = [sample for sample in samples if sample.category == "long_report"]
        refusal_scheduled = [
            sample for sample in samples if sample.category == "insufficient"
        ]
        mixed_scheduled = [sample for sample in samples if sample.category == "mixed"]
        mixed = [sample for sample in successful if sample.mixed_citation_violation is not None]
        case_rates: list[float] = []
        for case_id in sorted({sample.case_id for sample in samples}):
            case_samples = [sample for sample in samples if sample.case_id == case_id]
            case_rates.append(sum(sample.effective_task_correct is True for sample in case_samples) / len(case_samples))
        effective_macro = None if not case_rates else round(sum(case_rates) / len(case_rates), 6)
        unsupported_metric = _rate(
            sum(sample.unsupported_answer_violation is True for sample in refusal), len(refusal)
        )
        mixed_metric = _rate(
            sum(sample.mixed_citation_violation is True for sample in mixed), len(mixed)
        )
        safe_completion = _rate(
            sum(
                sample.outcome == "success"
                and sample.unsupported_answer_violation is False
                for sample in refusal_scheduled
            ),
            len(refusal_scheduled),
        )
        mixed_compliant_completion = _rate(
            sum(
                sample.outcome == "success"
                and sample.mixed_citation_violation is False
                for sample in mixed_scheduled
            ),
            len(mixed_scheduled),
        )
        safety = safe_completion["rate"]
        mixed_compliance = mixed_compliant_completion["rate"]
        compliance_values = [value for value in (safety, mixed_compliance) if value is not None]
        safety_mixed = None if len(compliance_values) != 2 else round(sum(compliance_values) / 2, 6)
        latencies = [sample.elapsed_ms for sample in successful]
        failures = Counter(sample.outcome for sample in samples if sample.outcome != "success")
        metrics = {
            "scheduled_samples": len(samples),
            "successful_responses": len(successful),
            "completion_rate": _rate(len(successful), len(samples)),
            "failure_rate": _rate(len(samples) - len(successful), len(samples)),
            "failure_kinds": dict(sorted(failures.items())),
            "fact_accuracy_conditional": _rate(sum(sample.fact_correct is True for sample in fact), len(fact)),
            "refusal_accuracy_conditional": _rate(sum(sample.refusal_correct is True for sample in refusal), len(refusal)),
            "evidence_accuracy_conditional": _rate(sum(sample.evidence_correct is True for sample in successful), len(successful)),
            "effective_task_accuracy_conditional": _rate(sum(sample.effective_task_correct is True for sample in successful), len(successful)),
            "effective_task_case_macro": {
                "numerator": round(sum(case_rates), 6),
                "denominator": len(case_rates),
                "rate": effective_macro,
            },
            "long_accuracy_conditional": _rate(sum(sample.effective_task_correct is True for sample in long), len(long)),
            "long_task_success": _rate(
                sum(sample.effective_task_correct is True for sample in long_scheduled),
                len(long_scheduled),
            ),
            "unsupported_answer_violation_rate_conditional": unsupported_metric,
            "mixed_citation_violation_rate_conditional": mixed_metric,
            "safe_completion": safe_completion,
            "mixed_compliant_completion": mixed_compliant_completion,
            "safety_mixed_compliance": {"rate": safety_mixed},
            "latency_ms": {
                "successful_n": len(latencies),
                "p50": _nearest_rank(latencies, 0.50),
                "p95": _nearest_rank(latencies, 0.95),
                "method": "nearest-rank-successful-responses",
            },
        }
        aggregated[model] = metrics
    return aggregated


def _passes(value: float | None, *, minimum: float | None = None, maximum: float | None = None, exclusive_maximum: float | None = None) -> bool:
    if value is None:
        return False
    if minimum is not None and value < minimum:
        return False
    if maximum is not None and value > maximum:
        return False
    if exclusive_maximum is not None and value >= exclusive_maximum:
        return False
    return True


def build_official_summary(
    *,
    dataset: BenchmarkDatasetV2,
    dataset_hash: str,
    policy: EvaluationPolicyV2,
    policy_hash: str,
    results: Sequence[OfficialSample],
    run_id: str,
    started_at: datetime,
    finished_at: datetime,
) -> dict[str, Any]:
    models = aggregate_official(results, policy)
    any_rate_limit = any(
        metrics["failure_kinds"].get("rate-limit", 0) > 0 for metrics in models.values()
    )
    for metrics in models.values():
        gates = policy.gates
        checks = {
            "completion_rate": _passes(metrics["completion_rate"]["rate"], minimum=gates.completionRateMin),
            "effective_task_case_macro": _passes(metrics["effective_task_case_macro"]["rate"], minimum=gates.effectiveTaskMacroMin),
            "long_accuracy": _passes(metrics["long_task_success"]["rate"], minimum=gates.longAccuracyMin),
            "unsupported_violation": _passes(metrics["unsupported_answer_violation_rate_conditional"]["rate"], maximum=gates.unsupportedViolationMax),
            "mixed_violation": _passes(metrics["mixed_citation_violation_rate_conditional"]["rate"], maximum=gates.mixedViolationMax),
            "safety_mixed_compliance": _passes(metrics["safety_mixed_compliance"]["rate"], minimum=gates.safetyMixedComplianceMin),
            "p95": _passes(metrics["latency_ms"]["p95"], exclusive_maximum=gates.p95MsExclusiveMax),
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
            metrics["project_fit_score"] = round(100 * (
                weights.effectiveTaskMacro * metrics["effective_task_case_macro"]["rate"]
                + weights.longAccuracy * metrics["long_task_success"]["rate"]
                + weights.safetyMixedCompliance * metrics["safety_mixed_compliance"]["rate"]
                + weights.relativeLatency * (fastest / p95)
            ), 3)

    quality_valid = not any_rate_limit
    leaderboard: list[dict[str, Any]] = []
    if quality_valid:
        eligible = [
            (model, metrics["project_fit_score"])
            for model, metrics in models.items()
            if metrics["project_fit_score"] is not None
        ]
        eligible.sort(key=lambda item: (-item[1], item[0]))
        leaderboard = [
            {"rank": index, "model": model, "project_fit_score": score}
            for index, (model, score) in enumerate(eligible, 1)
        ]
    hashes = {}
    for case in dataset.cases:
        system, user = build_case_prompt_v2(case)
        hashes[case.id] = prompt_hash(system, user)
    return {
        "schema_version": 2,
        "run_id": run_id,
        "started_at_utc": started_at.isoformat(),
        "finished_at_utc": finished_at.isoformat(),
        "dataset": {"name": dataset.name, "case_count": len(dataset.cases), "sha256": dataset_hash, "case_prompt_sha256": hashes},
        "policy": {"id": policy.policyId, "sha256": policy_hash},
        "config": {
            "models": list(policy.models), "repetitions": policy.repetitions,
            "warmup_per_model": policy.warmupPerModel,
            "measured_calls": len(dataset.cases) * policy.repetitions * len(policy.models),
            "total_calls": policy.totalCalls, "temperature": policy.temperature,
            "retry_count": policy.retryCount, "seed": policy.seed,
            "parser": policy.parser, "single_lane": policy.schedule.singleLane,
            "latin_square": policy.schedule.latinSquare,
            "min_request_interval_ms": policy.schedule.minRequestIntervalMs,
            "cooldown_before_measured_seconds": policy.schedule.cooldownBeforeMeasuredSeconds,
        },
        "metric_basis": {
            "completion_and_failures": "all-scheduled-samples",
            "quality_and_latency": "schema-valid-successful-responses",
            "effective_macro": "equal-weight-mean-of-case-success-rates",
        },
        "quality_comparison_valid": quality_valid,
        "quality_comparison_invalid_reason": "rate-limit-observed" if any_rate_limit else None,
        "models": models,
        "leaderboard": leaderboard,
    }


def _summary_csv(summary: Mapping[str, Any]) -> str:
    output = io.StringIO(newline="")
    writer = csv.writer(output)
    writer.writerow([
        "model", "completion_numerator", "completion_denominator", "completion_rate",
        "fact_accuracy_numerator", "fact_accuracy_denominator", "fact_accuracy_rate",
        "refusal_accuracy_numerator", "refusal_accuracy_denominator", "refusal_accuracy_rate",
        "evidence_accuracy_numerator", "evidence_accuracy_denominator", "evidence_accuracy_rate",
        "effective_macro_rate", "long_task_numerator", "long_task_denominator",
        "long_task_rate", "safe_completion_numerator", "safe_completion_denominator",
        "safe_completion_rate", "mixed_compliant_numerator", "mixed_compliant_denominator",
        "mixed_compliant_rate", "unsupported_numerator", "unsupported_denominator",
        "unsupported_rate", "mixed_numerator", "mixed_denominator", "mixed_rate",
        "p95_ms", "p95_n", "gate_pass", "project_fit_score",
    ])
    for model, metrics in summary["models"].items():
        completion = metrics["completion_rate"]
        fact = metrics["fact_accuracy_conditional"]
        refusal = metrics["refusal_accuracy_conditional"]
        evidence = metrics["evidence_accuracy_conditional"]
        long = metrics["long_task_success"]
        safe = metrics["safe_completion"]
        mixed_compliant = metrics["mixed_compliant_completion"]
        unsupported = metrics["unsupported_answer_violation_rate_conditional"]
        mixed = metrics["mixed_citation_violation_rate_conditional"]
        writer.writerow([
            model, completion["numerator"], completion["denominator"], completion["rate"],
            fact["numerator"], fact["denominator"], fact["rate"],
            refusal["numerator"], refusal["denominator"], refusal["rate"],
            evidence["numerator"], evidence["denominator"], evidence["rate"],
            metrics["effective_task_case_macro"]["rate"],
            long["numerator"], long["denominator"], long["rate"],
            safe["numerator"], safe["denominator"], safe["rate"],
            mixed_compliant["numerator"], mixed_compliant["denominator"], mixed_compliant["rate"],
            unsupported["numerator"], unsupported["denominator"], unsupported["rate"],
            mixed["numerator"], mixed["denominator"], mixed["rate"],
            metrics["latency_ms"]["p95"], metrics["latency_ms"]["successful_n"],
            metrics["gate_pass"], metrics["project_fit_score"],
        ])
    return output.getvalue()


def _percent(metric: Mapping[str, Any]) -> str:
    value = metric.get("rate")
    return "N/A" if value is None else f"{100 * value:.1f}% ({metric.get('numerator', '-')} / {metric.get('denominator', '-')})"


def _summary_markdown(summary: Mapping[str, Any]) -> str:
    lines = [
        "# GMS three-model project-fit evaluation v2", "",
        f"- Run: `{summary['run_id']}`",
        f"- Quality comparison valid: `{str(summary['quality_comparison_valid']).lower()}`",
        "- Quality metrics are conditional on schema-valid success; completion uses all scheduled samples.",
        "- Latency is the raw GMS model call only; p95 is nearest-rank and shows successful n.", "",
        "| Model | Completion | Fact accuracy | Evidence accuracy | Effective macro | Long task | Safe completion | Mixed-compliant | Unsupported violation | Mixed violation | p95 (n) | Gate | Score |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|:---:|---:|",
    ]
    for model, metrics in summary["models"].items():
        p95 = metrics["latency_ms"]["p95"]
        n = metrics["latency_ms"]["successful_n"]
        lines.append(
            f"| {model} | {_percent(metrics['completion_rate'])} | "
            f"{_percent(metrics['fact_accuracy_conditional'])} | {_percent(metrics['evidence_accuracy_conditional'])} | "
            f"{_percent(metrics['effective_task_case_macro'])} | {_percent(metrics['long_task_success'])} | "
            f"{_percent(metrics['safe_completion'])} | {_percent(metrics['mixed_compliant_completion'])} | "
            f"{_percent(metrics['unsupported_answer_violation_rate_conditional'])} | "
            f"{_percent(metrics['mixed_citation_violation_rate_conditional'])} | {p95} ({n}) | "
            f"{'PASS' if metrics['gate_pass'] else 'FAIL'} | {metrics['project_fit_score']} |"
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
        lines.extend(["", "Leaderboard withheld because at least one HTTP 429 was observed."])
    lines.extend(["", "Synthetic 20-case controlled benchmark; this is not an end-to-end service SLA.", ""])
    return "\n".join(lines)


def write_official_results(output_root: Path, summary: Mapping[str, Any], results: Sequence[OfficialSample]) -> Path:
    if len(results) != 180:
        raise ValueError("official publish requires exactly 180 measured samples")
    output_root.mkdir(parents=True, exist_ok=True)
    final_dir = output_root / str(summary["run_id"])
    staging = output_root / f".{summary['run_id']}.staging"
    if final_dir.exists() or staging.exists():
        raise FileExistsError("run output already exists")
    staging.mkdir()
    try:
        payloads = {
            "samples.jsonl": "".join(json.dumps(asdict(sample), ensure_ascii=False, sort_keys=True) + "\n" for sample in results),
            "summary.json": json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
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
        (staging / "checksums.sha256").write_text("\n".join(checksums) + "\n", encoding="utf-8", newline="\n")
        staging.replace(final_dir)
    except Exception:
        if staging.exists():
            shutil.rmtree(staging)
        raise
    return final_dir


def validate_official_profile(
    *,
    dataset: BenchmarkDatasetV2,
    dataset_hash: str,
    policy: EvaluationPolicyV2,
    policy_hash: str,
    arguments: argparse.Namespace,
) -> None:
    if policy_hash != OFFICIAL_POLICY_SHA256:
        raise ValueError("official policy hash does not match")
    if dataset_hash != policy.datasetSha256:
        raise ValueError("official dataset hash does not match policy")
    if len(dataset.cases) != policy.caseCount:
        raise ValueError("official dataset requires exactly 20 cases")
    expected_distribution = {
        "direct_report": 4, "long_report": 6, "web": 3,
        "insufficient": 3, "profile": 2, "mixed": 2,
    }
    if Counter(case.category for case in dataset.cases) != expected_distribution:
        raise ValueError("official category distribution does not match")
    expected = {
        "repetitions": policy.repetitions,
        "warmup": policy.warmupPerModel,
        "seed": policy.seed,
        "min_request_interval_ms": policy.schedule.minRequestIntervalMs,
        "cooldown_seconds": policy.schedule.cooldownBeforeMeasuredSeconds,
    }
    if any(getattr(arguments, name) != value for name, value in expected.items()):
        raise ValueError("official runtime arguments do not match pinned policy")
    calculated = (len(dataset.cases) * arguments.repetitions + arguments.warmup) * len(policy.models)
    if calculated != policy.totalCalls or calculated != 183:
        raise ValueError("official benchmark must make exactly 183 calls")


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run the pre-registered GMS project-fit evaluation v2")
    parser.add_argument("--official", action="store_true", required=True)
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--dataset", type=Path, default=DATASET_V2_PATH)
    parser.add_argument("--policy", type=Path, default=POLICY_V2_PATH)
    parser.add_argument("--repetitions", type=int, default=3)
    parser.add_argument("--warmup", type=int, default=1)
    parser.add_argument("--seed", type=int, default=20260809)
    parser.add_argument("--min-request-interval-ms", type=int, default=6500)
    parser.add_argument("--cooldown-seconds", type=int, default=300)
    parser.add_argument("--output-dir", type=Path, default=Path("../output/model-eval"))
    return parser


async def _run_cli(arguments: argparse.Namespace) -> Path:
    dataset, dataset_hash = load_dataset_v2(arguments.dataset)
    policy, policy_hash = load_policy_v2(arguments.policy)
    validate_official_profile(
        dataset=dataset, dataset_hash=dataset_hash, policy=policy,
        policy_hash=policy_hash, arguments=arguments,
    )
    if arguments.env_file is None or not arguments.env_file.is_file():
        raise ValueError("official benchmark requires an env file")
    settings = GmsModelSuiteSettings.from_mapping(dotenv_values(arguments.env_file))
    if (settings.gemini_model, settings.openai_model, settings.anthropic_model) != tuple(policy.models):
        raise ValueError("configured model ids do not match official policy")
    previous_retry = os.environ.get("AI_JSON_RETRY_COUNT")
    os.environ["AI_JSON_RETRY_COUNT"] = "0"
    try:
        client = GmsRawClient(settings, temperature=policy.temperature)
        started = datetime.now(timezone.utc)
        run_id = started.strftime("%Y%m%dT%H%M%S%fZ")
        results = await OfficialBenchmarkRunner(client, policy.models).run(dataset, policy)
        finished = datetime.now(timezone.utc)
        summary = build_official_summary(
            dataset=dataset, dataset_hash=dataset_hash, policy=policy,
            policy_hash=policy_hash, results=results, run_id=run_id,
            started_at=started, finished_at=finished,
        )
        return write_official_results(arguments.output_dir, summary, results)
    finally:
        if previous_retry is None:
            os.environ.pop("AI_JSON_RETRY_COUNT", None)
        else:
            os.environ["AI_JSON_RETRY_COUNT"] = previous_retry


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
