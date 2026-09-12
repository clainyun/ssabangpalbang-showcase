"""Deterministic three-model benchmark for grounded chatbot answers.

The benchmark intentionally bypasses retrieval and web search.  Every model
receives the same synthetic evidence prompt, which isolates answer grounding
and citation selection from upstream data-source variability.
"""

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
import sys
import time
import unicodedata
from collections import Counter
from collections.abc import Awaitable, Callable, Mapping, Sequence
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal

from dotenv import load_dotenv
from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator

from app.prompts.chatbot_prompt import SYSTEM_PROMPT, build_user_prompt
from app.providers.gms_model_suite import (
    ANTHROPIC_MODEL,
    GEMINI_MODEL,
    OPENAI_MODEL,
    create_gms_model_suite,
)
from app.providers.llm_provider import LlmProvider, LlmProviderError
from app.providers.web_search_provider import WebSearchResult
from app.rag.search import ApartmentProfile, ReportChunkHit
from app.schemas.chatbot import LlmAnswerPayload


DATASET_PATH = (
    Path(__file__).resolve().parent
    / "datasets"
    / "chatbot_grounding_v1.json"
)
MAX_OFFICIAL_CALLS = 183
OFFICIAL_CASE_COUNT = 12
OFFICIAL_MODELS = frozenset({GEMINI_MODEL, OPENAI_MODEL, ANTHROPIC_MODEL})
TEMPERATURE = 0.2
JSON_RETRY_COUNT = 0
DEFAULT_MIN_BATCH_INTERVAL_MS = 6500
REFUSAL_MARKERS = (
    "확인할 수 없",
    "확인되지 않",
    "근거가 부족",
    "정보가 부족",
    "자료가 없",
    "알 수 없",
    "답변하기 어렵",
)


class ProfileInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str
    address: str | None = None
    districtName: str | None = None
    dongName: str | None = None
    householdCount: int | None = Field(default=None, ge=0)
    completionYearMonth: str | None = None
    parkingSpaceCount: int | None = Field(default=None, ge=0)


class ReportEvidenceInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sourceId: int = Field(gt=0)
    content: str = Field(min_length=1)
    sourceAt: datetime | None = None
    similarity: float = Field(ge=-1, le=1)


class WebEvidenceInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: str = Field(min_length=1)
    snippet: str = Field(min_length=1)
    url: str = Field(min_length=1)
    domain: str = Field(min_length=1)
    retrievedAt: datetime


class BenchmarkCase(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: str = Field(pattern=r"^[a-z0-9][a-z0-9-]*$")
    category: Literal["report", "web", "insufficient", "profile", "mixed"]
    question: str = Field(min_length=1)
    profile: ProfileInput | None = None
    reportEvidence: list[ReportEvidenceInput] = Field(default_factory=list)
    webEvidence: list[WebEvidenceInput] = Field(default_factory=list)
    requiredAnswerGroups: list[list[str]] = Field(default_factory=list)
    forbiddenAnswerTerms: list[str] = Field(default_factory=list)
    acceptableSourceSets: list[list[int]] = Field(min_length=1)
    requiresRefusal: bool = False

    @model_validator(mode="after")
    def validate_labels(self) -> "BenchmarkCase":
        if self.requiresRefusal and self.requiredAnswerGroups:
            raise ValueError("refusal cases cannot require answer terms")
        if not self.requiresRefusal and not self.requiredAnswerGroups:
            raise ValueError("answerable cases need required answer groups")
        if self.requiresRefusal and [] not in self.acceptableSourceSets:
            raise ValueError("refusal cases must accept the empty source set")

        for group in self.requiredAnswerGroups:
            if not group or any(not term.strip() for term in group):
                raise ValueError("answer term groups cannot be empty")

        maximum = len(self.reportEvidence) + len(self.webEvidence)
        canonical_sets: set[tuple[int, ...]] = set()
        for source_set in self.acceptableSourceSets:
            if len(source_set) != len(set(source_set)):
                raise ValueError("acceptable source sets cannot contain duplicates")
            if any(number < 1 or number > maximum for number in source_set):
                raise ValueError("acceptable source index is out of range")
            canonical = tuple(sorted(source_set))
            if canonical in canonical_sets:
                raise ValueError("acceptable source sets must be distinct")
            canonical_sets.add(canonical)
        return self


class BenchmarkDataset(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schemaVersion: Literal[1]
    name: str = Field(min_length=1)
    cases: list[BenchmarkCase] = Field(min_length=1)

    @model_validator(mode="after")
    def unique_case_ids(self) -> "BenchmarkDataset":
        case_ids = [case.id for case in self.cases]
        if len(case_ids) != len(set(case_ids)):
            raise ValueError("dataset case ids must be unique")
        return self


@dataclass(frozen=True)
class SampleResult:
    model: str
    case_id: str
    repetition: int
    ordinal: int
    prompt_hash: str
    elapsed_ms: float
    outcome: str
    status_code: int | None
    used_sources: list[int] | None
    answer_correct: bool | None
    evidence_correct: bool
    unsupported_answer_violation: bool | None
    mixed_citation_violation: bool | None


def load_dataset(path: Path = DATASET_PATH) -> tuple[BenchmarkDataset, str]:
    raw = path.read_bytes()
    payload = json.loads(raw.decode("utf-8"))
    return BenchmarkDataset.model_validate(payload), _sha256(raw)


def build_case_prompt(case: BenchmarkCase) -> tuple[str, str]:
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
    report_hits = [
        ReportChunkHit(
            source_id=item.sourceId,
            content=item.content,
            source_at=item.sourceAt,
            similarity=item.similarity,
        )
        for item in case.reportEvidence
    ]
    web_results = [
        WebSearchResult(
            title=item.title,
            snippet=item.snippet,
            url=item.url,
            domain=item.domain,
            retrieved_at=item.retrievedAt,
        )
        for item in case.webEvidence
    ]
    return SYSTEM_PROMPT, build_user_prompt(
        case.question,
        profile,
        report_hits,
        web_results,
    )


def prompt_hash(system_prompt: str, user_prompt: str) -> str:
    return _sha256((system_prompt + "\0" + user_prompt).encode("utf-8"))


def score_payload(
    case: BenchmarkCase,
    payload: LlmAnswerPayload,
) -> tuple[bool | None, bool, bool | None, bool | None]:
    normalized_answer = _normalize(payload.answer)
    answer_correct: bool | None
    if case.requiresRefusal:
        answer_correct = None
    else:
        required_ok = all(
            any(_normalize(term) in normalized_answer for term in group)
            for group in case.requiredAnswerGroups
        )
        forbidden_ok = all(
            _normalize(term) not in normalized_answer
            for term in case.forbiddenAnswerTerms
        )
        answer_correct = required_ok and forbidden_ok

    selected = payload.usedSources
    selected_set = frozenset(selected)
    acceptable = {
        frozenset(source_set) for source_set in case.acceptableSourceSets
    }
    evidence_correct = (
        len(selected) == len(selected_set) and selected_set in acceptable
    )

    unsupported_violation = None
    if case.requiresRefusal:
        refused = any(
            _normalize(marker) in normalized_answer
            for marker in REFUSAL_MARKERS
        )
        unsupported_violation = bool(selected) or not refused

    mixed_violation = None
    report_count = len(case.reportEvidence)
    if report_count and case.webEvidence:
        valid = {
            number
            for number in selected
            if 1 <= number <= report_count + len(case.webEvidence)
        }
        has_report = any(number <= report_count for number in valid)
        has_web = any(number > report_count for number in valid)
        mixed_violation = has_report and has_web

    return (
        answer_correct,
        evidence_correct,
        unsupported_violation,
        mixed_violation,
    )


class BenchmarkRunner:
    def __init__(
        self,
        providers: Mapping[str, LlmProvider],
        *,
        clock: Callable[[], float] = time.perf_counter,
        schedule_clock: Callable[[], float] = time.perf_counter,
        sleeper: Callable[[float], Awaitable[None]] = asyncio.sleep,
    ) -> None:
        if not providers:
            raise ValueError("at least one provider is required")
        self.providers = dict(providers)
        self.clock = clock
        self.schedule_clock = schedule_clock
        self.sleeper = sleeper

    async def run(
        self,
        dataset: BenchmarkDataset,
        *,
        repetitions: int,
        warmup: int,
        seed: int,
        min_batch_interval_ms: int = 0,
    ) -> list[SampleResult]:
        if repetitions < 1:
            raise ValueError("repetitions must be positive")
        if warmup < 0:
            raise ValueError("warmup must be non-negative")
        if min_batch_interval_ms < 0:
            raise ValueError("min_batch_interval_ms must be non-negative")

        minimum_interval = min_batch_interval_ms / 1000
        previous_batch_started: float | None = None

        async def wait_for_batch_slot() -> None:
            nonlocal previous_batch_started
            if previous_batch_started is not None:
                remaining = minimum_interval - (
                    self.schedule_clock() - previous_batch_started
                )
                if remaining > 0:
                    await self.sleeper(remaining)
            previous_batch_started = self.schedule_clock()

        prepared = {
            case.id: (*build_case_prompt(case), case)
            for case in dataset.cases
        }
        first = dataset.cases[0]
        for _ in range(warmup):
            await wait_for_batch_slot()
            system_prompt, user_prompt, _ = prepared[first.id]
            await asyncio.gather(
                *(
                    self._warmup(provider, system_prompt, user_prompt)
                    for provider in self.providers.values()
                )
            )

        results: list[SampleResult] = []
        rng = random.Random(seed)
        ordinal = 0
        for repetition in range(1, repetitions + 1):
            ordered_cases = list(dataset.cases)
            rng.shuffle(ordered_cases)
            for case in ordered_cases:
                await wait_for_batch_slot()
                ordinal += 1
                system_prompt, user_prompt, _ = prepared[case.id]
                case_hash = prompt_hash(system_prompt, user_prompt)
                batch = await asyncio.gather(
                    *(
                        self._measure(
                            model,
                            provider,
                            case,
                            repetition,
                            ordinal,
                            case_hash,
                            system_prompt,
                            user_prompt,
                        )
                        for model, provider in self.providers.items()
                    )
                )
                results.extend(batch)
        return results

    async def _warmup(
        self,
        provider: LlmProvider,
        system_prompt: str,
        user_prompt: str,
    ) -> None:
        try:
            await provider.complete_json(system_prompt, user_prompt)
        except Exception:
            # Warmup is intentionally excluded from availability and latency.
            return

    async def _measure(
        self,
        model: str,
        provider: LlmProvider,
        case: BenchmarkCase,
        repetition: int,
        ordinal: int,
        case_hash: str,
        system_prompt: str,
        user_prompt: str,
    ) -> SampleResult:
        started = self.clock()
        try:
            raw = await provider.complete_json(system_prompt, user_prompt)
        except LlmProviderError as exc:
            return self._failure(
                model,
                case,
                repetition,
                ordinal,
                case_hash,
                started,
                "provider_error",
                exc.status_code,
            )
        except Exception:
            return self._failure(
                model,
                case,
                repetition,
                ordinal,
                case_hash,
                started,
                "unexpected_error",
                None,
            )

        elapsed_ms = max(0.0, (self.clock() - started) * 1000)
        try:
            payload = LlmAnswerPayload.model_validate(raw)
        except (ValidationError, TypeError):
            return SampleResult(
                model=model,
                case_id=case.id,
                repetition=repetition,
                ordinal=ordinal,
                prompt_hash=case_hash,
                elapsed_ms=round(elapsed_ms, 3),
                outcome="schema_error",
                status_code=None,
                used_sources=None,
                answer_correct=False if not case.requiresRefusal else None,
                evidence_correct=False,
                unsupported_answer_violation=None,
                mixed_citation_violation=None,
            )

        scores = score_payload(case, payload)
        return SampleResult(
            model=model,
            case_id=case.id,
            repetition=repetition,
            ordinal=ordinal,
            prompt_hash=case_hash,
            elapsed_ms=round(elapsed_ms, 3),
            outcome="success",
            status_code=None,
            used_sources=list(payload.usedSources),
            answer_correct=scores[0],
            evidence_correct=scores[1],
            unsupported_answer_violation=scores[2],
            mixed_citation_violation=scores[3],
        )

    def _failure(
        self,
        model: str,
        case: BenchmarkCase,
        repetition: int,
        ordinal: int,
        case_hash: str,
        started: float,
        outcome: str,
        status_code: int | None,
    ) -> SampleResult:
        elapsed_ms = max(0.0, (self.clock() - started) * 1000)
        return SampleResult(
            model=model,
            case_id=case.id,
            repetition=repetition,
            ordinal=ordinal,
            prompt_hash=case_hash,
            elapsed_ms=round(elapsed_ms, 3),
            outcome=outcome,
            status_code=status_code,
            used_sources=None,
            answer_correct=False if not case.requiresRefusal else None,
            evidence_correct=False,
            unsupported_answer_violation=None,
            mixed_citation_violation=None,
        )


def aggregate_results(
    results: Sequence[SampleResult],
) -> dict[str, dict[str, Any]]:
    aggregates: dict[str, dict[str, Any]] = {}
    for model in sorted({sample.model for sample in results}):
        samples = [sample for sample in results if sample.model == model]
        successful = [sample for sample in samples if sample.outcome == "success"]
        answer_samples = [
            sample for sample in samples if sample.answer_correct is not None
        ]
        unsupported_samples = [
            sample
            for sample in successful
            if sample.unsupported_answer_violation is not None
        ]
        mixed_samples = [
            sample
            for sample in successful
            if sample.mixed_citation_violation is not None
        ]
        latencies = [sample.elapsed_ms for sample in successful]
        failures = Counter(
            sample.outcome for sample in samples if sample.outcome != "success"
        )
        technical_failures = sum(
            sample.outcome in {"provider_error", "unexpected_error"}
            for sample in samples
        )
        schema_failures = sum(
            sample.outcome == "schema_error" for sample in samples
        )
        aggregates[model] = {
            "scheduled_samples": len(samples),
            "successful_responses": len(successful),
            "completion_rate": _rate(len(successful), len(samples)),
            "failure_rate": _rate(len(samples) - len(successful), len(samples)),
            "technical_failure_rate": _rate(
                technical_failures,
                len(samples),
            ),
            "schema_failure_rate": _rate(schema_failures, len(samples)),
            "answer_accuracy": _rate(
                sum(sample.answer_correct is True for sample in answer_samples),
                len(answer_samples),
            ),
            "evidence_selection_accuracy": _rate(
                sum(sample.evidence_correct for sample in samples),
                len(samples),
            ),
            "unsupported_answer_rate": _rate(
                sum(
                    sample.unsupported_answer_violation is True
                    for sample in unsupported_samples
                ),
                len(unsupported_samples),
            ),
            "mixed_citation_violation_rate": _rate(
                sum(
                    sample.mixed_citation_violation is True
                    for sample in mixed_samples
                ),
                len(mixed_samples),
            ),
            "latency_ms": {
                "successful_n": len(latencies),
                "p50": _nearest_rank(latencies, 0.50),
                "p95": _nearest_rank(latencies, 0.95),
                "method": "nearest-rank-successful-responses",
            },
            "failure_kinds": dict(sorted(failures.items())),
        }
    return aggregates


def build_summary(
    *,
    dataset: BenchmarkDataset,
    dataset_hash: str,
    results: Sequence[SampleResult],
    repetitions: int,
    warmup: int,
    seed: int,
    min_batch_interval_ms: int = 0,
    run_id: str,
    started_at: datetime,
    finished_at: datetime,
) -> dict[str, Any]:
    case_hashes = {}
    for case in dataset.cases:
        system_prompt, user_prompt = build_case_prompt(case)
        case_hashes[case.id] = prompt_hash(system_prompt, user_prompt)
    return {
        "schema_version": 1,
        "run_id": run_id,
        "started_at": started_at.isoformat(),
        "finished_at": finished_at.isoformat(),
        "dataset": {
            "name": dataset.name,
            "schema_version": dataset.schemaVersion,
            "sha256": dataset_hash,
            "case_count": len(dataset.cases),
            "prompt_hashes": case_hashes,
        },
        "configuration": {
            "models_in_parallel": True,
            "max_in_flight_per_model": 1,
            "min_batch_start_interval_ms": min_batch_interval_ms,
            "repetitions": repetitions,
            "warmup_per_model": warmup,
            "seed": seed,
            "temperature": TEMPERATURE,
            "json_retry_count": JSON_RETRY_COUNT,
            "runner_retry_count": 0,
            "measured_calls": len(dataset.cases) * repetitions * 3,
            "total_calls_including_warmup": (
                len(dataset.cases) * repetitions + warmup
            )
            * 3,
        },
        "models": aggregate_results(results),
    }


def write_results(
    output_dir: Path,
    summary: dict[str, Any],
    results: Sequence[SampleResult],
) -> Path:
    run_dir = output_dir / str(summary["run_id"])
    run_dir.mkdir(parents=True, exist_ok=False)

    samples = "".join(
        json.dumps(asdict(sample), ensure_ascii=False, sort_keys=True) + "\n"
        for sample in results
    )
    _atomic_write(run_dir / "samples.jsonl", samples)
    _atomic_write(
        run_dir / "summary.json",
        json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True)
        + "\n",
    )
    _atomic_write(run_dir / "summary.csv", _summary_csv(summary))
    _atomic_write(run_dir / "summary.md", _summary_markdown(summary))
    return run_dir


def _summary_csv(summary: Mapping[str, Any]) -> str:
    buffer = io.StringIO(newline="")
    writer = csv.writer(buffer)
    writer.writerow(
        [
            "model",
            "answer_accuracy",
            "evidence_selection_accuracy",
            "unsupported_answer_rate",
            "mixed_citation_violation_rate",
            "completion_rate",
            "failure_rate",
            "technical_failure_rate",
            "schema_failure_rate",
            "p95_ms",
            "successful_n",
        ]
    )
    for model, metrics in summary["models"].items():
        writer.writerow(
            [
                model,
                metrics["answer_accuracy"]["rate"],
                metrics["evidence_selection_accuracy"]["rate"],
                metrics["unsupported_answer_rate"]["rate"],
                metrics["mixed_citation_violation_rate"]["rate"],
                metrics["completion_rate"]["rate"],
                metrics["failure_rate"]["rate"],
                metrics["technical_failure_rate"]["rate"],
                metrics["schema_failure_rate"]["rate"],
                metrics["latency_ms"]["p95"],
                metrics["latency_ms"]["successful_n"],
            ]
        )
    return buffer.getvalue()


def _summary_markdown(summary: Mapping[str, Any]) -> str:
    lines = [
        "# GMS 챗봇 모델 비교",
        "",
        f"- Run: `{summary['run_id']}`",
        f"- Dataset SHA-256: `{summary['dataset']['sha256']}`",
        "- p95: 성공 응답 기준 nearest-rank",
        "",
        "| 모델 | 정답 정확도 | 근거 선택 정확도 | 근거 없는 답변율 | 혼합 인용 위반율 | 성공률 | p95 (ms) |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    for model, metrics in summary["models"].items():
        lines.append(
            "| "
            + " | ".join(
                [
                    model,
                    _percent(metrics["answer_accuracy"]),
                    _percent(metrics["evidence_selection_accuracy"]),
                    _percent(metrics["unsupported_answer_rate"]),
                    _percent(metrics["mixed_citation_violation_rate"]),
                    _percent(metrics["completion_rate"]),
                    str(metrics["latency_ms"]["p95"]),
                ]
            )
            + " |"
        )
    lines.extend(
        [
            "",
            "합성 12개 사례의 모델 응답만 비교하며 DB·검색 지연은 포함하지 않습니다.",
            "",
        ]
    )
    return "\n".join(lines)


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
    index = max(0, math.ceil(quantile * len(ordered)) - 1)
    return round(ordered[index], 3)


def _percent(metric: Mapping[str, Any]) -> str:
    value = metric["rate"]
    return "N/A" if value is None else f"{value * 100:.1f}%"


def _normalize(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value).casefold()
    return " ".join(normalized.split())


def _sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _atomic_write(path: Path, content: str) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(content, encoding="utf-8", newline="\n")
    temporary.replace(path)


def _positive_int(value: str) -> int:
    parsed = int(value)
    if parsed < 1:
        raise argparse.ArgumentTypeError("must be at least 1")
    return parsed


def _non_negative_int(value: str) -> int:
    parsed = int(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("must be at least 0")
    return parsed


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="세 GMS 모델의 챗봇 근거 준수와 응답시간을 비교합니다."
    )
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--dataset", type=Path, default=DATASET_PATH)
    parser.add_argument("--repetitions", type=_positive_int, default=5)
    parser.add_argument("--warmup", type=_non_negative_int, default=1)
    parser.add_argument("--seed", type=int, default=20260809)
    parser.add_argument(
        "--min-batch-interval-ms",
        type=_non_negative_int,
        default=DEFAULT_MIN_BATCH_INTERVAL_MS,
        help="minimum delay between starts of consecutive three-model batches",
    )
    parser.add_argument(
        "--output-dir", type=Path, default=Path("../output/model-eval")
    )
    return parser


async def _run_cli(arguments: argparse.Namespace) -> Path:
    if arguments.env_file is not None:
        if not arguments.env_file.is_file():
            raise ValueError("env file does not exist")
        load_dotenv(arguments.env_file, override=False)

    dataset, dataset_hash = load_dataset(arguments.dataset)
    if len(dataset.cases) != OFFICIAL_CASE_COUNT:
        raise ValueError("official benchmark requires exactly 12 cases")
    total_calls = (
        len(dataset.cases) * arguments.repetitions + arguments.warmup
    ) * 3
    if total_calls > MAX_OFFICIAL_CALLS:
        raise ValueError("benchmark exceeds the approved 183-call limit")

    # The comparison is a raw single-attempt profile.  This process-local
    # override prevents Gemini's production JSON repair retry from skewing it.
    previous_retry_count = os.environ.get("AI_JSON_RETRY_COUNT")
    os.environ["AI_JSON_RETRY_COUNT"] = str(JSON_RETRY_COUNT)
    try:
        providers = create_gms_model_suite()
        if set(providers) != OFFICIAL_MODELS:
            raise ValueError("official benchmark model ids do not match")

        started_at = datetime.now(timezone.utc)
        run_id = started_at.strftime("%Y%m%dT%H%M%SZ")
        results = await BenchmarkRunner(providers).run(
            dataset,
            repetitions=arguments.repetitions,
            warmup=arguments.warmup,
            seed=arguments.seed,
            min_batch_interval_ms=arguments.min_batch_interval_ms,
        )
        finished_at = datetime.now(timezone.utc)
        summary = build_summary(
            dataset=dataset,
            dataset_hash=dataset_hash,
            results=results,
            repetitions=arguments.repetitions,
            warmup=arguments.warmup,
            seed=arguments.seed,
            min_batch_interval_ms=arguments.min_batch_interval_ms,
            run_id=run_id,
            started_at=started_at,
            finished_at=finished_at,
        )
        return write_results(arguments.output_dir, summary, results)
    finally:
        if previous_retry_count is None:
            os.environ.pop("AI_JSON_RETRY_COUNT", None)
        else:
            os.environ["AI_JSON_RETRY_COUNT"] = previous_retry_count


def main(argv: Sequence[str] | None = None) -> int:
    resolved_argv = list(sys.argv[1:] if argv is None else argv)
    if "--e2e-dogok-rexle" in resolved_argv:
        # Dedicated hybrid E2E: production DB/RAG/web, scripted LLM only.
        # Route before the legacy parser so its explicit safety flags remain
        # isolated from, and cannot weaken, v1 or official v3.
        from app.evaluation.chatbot_e2e_benchmark_v3 import main as e2e_main

        return e2e_main(resolved_argv)
    if "--official" in resolved_argv:
        # Keep v1/v2 as reproducible pilot and raw-JSON stress modules while
        # routing the official selection run through production-fit v3.
        from app.evaluation.chatbot_model_benchmark_v3 import main as v3_main

        return v3_main(resolved_argv)
    arguments = _parser().parse_args(resolved_argv)
    try:
        run_dir = asyncio.run(_run_cli(arguments))
    except Exception as exc:
        print(f"평가 실패: {type(exc).__name__}", file=sys.stderr)
        return 1
    print(f"평가 완료: {run_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
