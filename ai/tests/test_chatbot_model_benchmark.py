"""Network-free tests for the synthetic three-model chatbot benchmark."""

from __future__ import annotations

import argparse
import asyncio
from dataclasses import replace
from datetime import datetime, timezone

import pytest
from pydantic import ValidationError

import app.evaluation.chatbot_model_benchmark as benchmark
from app.evaluation.chatbot_model_benchmark import (
    BenchmarkDataset,
    BenchmarkRunner,
    SampleResult,
    aggregate_results,
    build_summary,
    load_dataset,
    score_payload,
    write_results,
)
from app.prompts.chatbot_prompt import SYSTEM_PROMPT
from app.providers.llm_provider import LlmProviderError
from app.schemas.chatbot import LlmAnswerPayload


class RecordingProvider:
    def __init__(self, payload=None, error: Exception | None = None) -> None:
        self.payload = payload or {
            "answer": "근거가 부족해 확인할 수 없습니다.",
            "usedSources": [],
        }
        self.error = error
        self.calls: list[tuple[str, str]] = []

    async def complete_json(self, system_prompt: str, user_prompt: str) -> dict:
        self.calls.append((system_prompt, user_prompt))
        if self.error is not None:
            raise self.error
        return self.payload


def _one_case_dataset() -> BenchmarkDataset:
    dataset, _ = load_dataset()
    return BenchmarkDataset(
        schemaVersion=1,
        name="one-case-test",
        cases=[dataset.cases[0]],
    )


def test_official_dataset_has_twelve_labeled_synthetic_cases() -> None:
    dataset, dataset_hash = load_dataset()

    assert len(dataset.cases) == 12
    assert len(dataset_hash) == 64
    assert {case.category for case in dataset.cases} == {
        "report",
        "web",
        "insufficient",
        "profile",
        "mixed",
    }
    assert sum(case.requiresRefusal for case in dataset.cases) == 3


def test_dataset_rejects_duplicate_ids_and_bad_source_index() -> None:
    dataset, _ = load_dataset()
    duplicate = dataset.model_dump()
    duplicate["cases"][1]["id"] = duplicate["cases"][0]["id"]
    with pytest.raises(ValidationError, match="case ids"):
        BenchmarkDataset.model_validate(duplicate)

    invalid_index = dataset.cases[0].model_dump()
    invalid_index["acceptableSourceSets"] = [[99]]
    with pytest.raises(ValidationError, match="out of range"):
        benchmark.BenchmarkCase.model_validate(invalid_index)


def test_scorer_checks_answer_exact_sources_and_forbidden_terms() -> None:
    dataset, _ = load_dataset()
    case = dataset.cases[0]

    correct = score_payload(
        case,
        LlmAnswerPayload(answer="세대당 1.2대입니다.", usedSources=[1]),
    )
    wrong = score_payload(
        case,
        LlmAnswerPayload(answer="총 920대입니다.", usedSources=[2]),
    )

    assert correct == (True, True, None, None)
    assert wrong == (False, False, None, None)


def test_scorer_detects_unsupported_and_raw_mixed_citations() -> None:
    dataset, _ = load_dataset()
    insufficient = next(case for case in dataset.cases if case.requiresRefusal)
    mixed = next(case for case in dataset.cases if case.category == "mixed")

    refusal = score_payload(
        insufficient,
        LlmAnswerPayload(
            answer="제공된 근거로는 확인할 수 없습니다.", usedSources=[]
        ),
    )
    unsupported = score_payload(
        insufficient,
        LlmAnswerPayload(answer="통학버스를 운행합니다.", usedSources=[1]),
    )
    mixed_citations = score_payload(
        mixed,
        LlmAnswerPayload(
            answer="밤 11시에 소음이 있었습니다.", usedSources=[1, 2]
        ),
    )

    assert refusal[2] is False
    assert unsupported[2] is True
    assert mixed_citations[3] is True
    assert mixed_citations[1] is False


@pytest.mark.asyncio
async def test_runner_uses_identical_prompts_and_exact_call_count() -> None:
    dataset, _ = load_dataset()
    providers = {
        model: RecordingProvider()
        for model in ("gemini", "openai", "claude")
    }

    results = await BenchmarkRunner(providers).run(
        dataset,
        repetitions=2,
        warmup=1,
        seed=7,
    )

    assert len(results) == 3 * 12 * 2
    assert all(len(provider.calls) == 25 for provider in providers.values())
    assert providers["gemini"].calls == providers["openai"].calls
    assert providers["openai"].calls == providers["claude"].calls
    grouped: dict[tuple[int, int], set[str]] = {}
    for sample in results:
        grouped.setdefault((sample.repetition, sample.ordinal), set()).add(
            sample.prompt_hash
        )
    assert all(len(hashes) == 1 for hashes in grouped.values())


@pytest.mark.asyncio
async def test_runner_parallelizes_models_but_not_calls_per_model() -> None:
    dataset = _one_case_dataset()

    class Probe:
        active = 0
        maximum = 0
        all_started = asyncio.Event()

    class ConcurrentProvider(RecordingProvider):
        def __init__(self) -> None:
            super().__init__()
            self.active = 0
            self.maximum = 0

        async def complete_json(self, system_prompt, user_prompt):
            self.calls.append((system_prompt, user_prompt))
            self.active += 1
            self.maximum = max(self.maximum, self.active)
            Probe.active += 1
            Probe.maximum = max(Probe.maximum, Probe.active)
            if Probe.active == 3:
                Probe.all_started.set()
            await asyncio.wait_for(Probe.all_started.wait(), timeout=1)
            Probe.active -= 1
            self.active -= 1
            return self.payload

    providers = {name: ConcurrentProvider() for name in ("a", "b", "c")}
    await BenchmarkRunner(providers).run(
        dataset,
        repetitions=1,
        warmup=0,
        seed=1,
    )

    assert Probe.maximum == 3
    assert all(provider.maximum == 1 for provider in providers.values())


@pytest.mark.asyncio
async def test_runner_paces_batch_starts_outside_measured_latency() -> None:
    dataset = _one_case_dataset()
    now = 0.0
    sleeps: list[float] = []

    def fake_clock() -> float:
        return now

    async def fake_sleep(seconds: float) -> None:
        nonlocal now
        sleeps.append(seconds)
        now += seconds

    providers = {name: RecordingProvider() for name in ("a", "b", "c")}
    results = await BenchmarkRunner(
        providers,
        clock=fake_clock,
        schedule_clock=fake_clock,
        sleeper=fake_sleep,
    ).run(
        dataset,
        repetitions=2,
        warmup=1,
        seed=1,
        min_batch_interval_ms=6500,
    )

    assert sleeps == [6.5, 6.5]
    assert all(sample.elapsed_ms == 0 for sample in results)


@pytest.mark.asyncio
async def test_provider_failure_is_sanitized_and_does_not_stop_other_models() -> None:
    dataset = _one_case_dataset()
    providers = {
        "good": RecordingProvider(
            {"answer": "세대당 1.2대입니다.", "usedSources": [1]}
        ),
        "bad": RecordingProvider(
            error=LlmProviderError(
                "secret-key https://ai-gateway.example.com/private",
                status_code=503,
            )
        ),
        "schema": RecordingProvider({"unexpected": "raw-secret-answer"}),
    }

    results = await BenchmarkRunner(providers).run(
        dataset,
        repetitions=1,
        warmup=0,
        seed=1,
    )

    by_model = {sample.model: sample for sample in results}
    assert by_model["good"].outcome == "success"
    assert by_model["bad"].outcome == "provider_error"
    assert by_model["bad"].status_code == 503
    assert by_model["schema"].outcome == "schema_error"
    assert all(provider.calls for provider in providers.values())


def test_nearest_rank_p95_and_failure_denominators() -> None:
    samples = [
        SampleResult(
            model="model-a",
            case_id="case",
            repetition=1,
            ordinal=index,
            prompt_hash="0" * 64,
            elapsed_ms=float(index),
            outcome="success",
            status_code=None,
            used_sources=[],
            answer_correct=True,
            evidence_correct=True,
            unsupported_answer_violation=None,
            mixed_citation_violation=None,
        )
        for index in range(1, 21)
    ]
    samples.append(
        replace(
            samples[0],
            ordinal=21,
            outcome="schema_error",
            answer_correct=False,
            evidence_correct=False,
        )
    )

    metrics = aggregate_results(samples)["model-a"]

    assert metrics["latency_ms"]["p95"] == 19.0
    assert metrics["latency_ms"]["successful_n"] == 20
    assert metrics["answer_accuracy"] == {
        "numerator": 20,
        "denominator": 21,
        "rate": 0.952381,
    }
    assert metrics["failure_kinds"] == {"schema_error": 1}
    assert metrics["failure_rate"] == {
        "numerator": 1,
        "denominator": 21,
        "rate": 0.047619,
    }
    assert metrics["technical_failure_rate"]["numerator"] == 0
    assert metrics["schema_failure_rate"]["numerator"] == 1


@pytest.mark.asyncio
async def test_written_outputs_exclude_secrets_endpoints_prompts_and_answers(
    tmp_path,
) -> None:
    dataset = _one_case_dataset()
    _, dataset_hash = load_dataset()
    raw_answer = "RAW_SECRET_ANSWER 세대당 1.2대입니다."
    providers = {
        name: RecordingProvider(
            {"answer": raw_answer, "usedSources": [1]}
        )
        for name in ("gemini", "openai", "claude")
    }
    results = await BenchmarkRunner(providers).run(
        dataset,
        repetitions=1,
        warmup=0,
        seed=1,
    )
    now = datetime(2026, 8, 9, tzinfo=timezone.utc)
    summary = build_summary(
        dataset=dataset,
        dataset_hash=dataset_hash,
        results=results,
        repetitions=1,
        warmup=0,
        seed=1,
        run_id="safe-run",
        started_at=now,
        finished_at=now,
    )

    run_dir = write_results(tmp_path, summary, results)
    combined = "\n".join(
        path.read_text(encoding="utf-8") for path in run_dir.iterdir()
    )

    for forbidden in (
        raw_answer,
        SYSTEM_PROMPT,
        "secret-key",
        "https://ai-gateway.example.com",
        "Authorization",
        "x-api-key",
        "x-goog-api-key",
    ):
        assert forbidden not in combined
    assert set(path.name for path in run_dir.iterdir()) == {
        "samples.jsonl",
        "summary.json",
        "summary.csv",
        "summary.md",
    }


@pytest.mark.asyncio
async def test_call_limit_fails_before_provider_creation(
    monkeypatch,
    tmp_path,
) -> None:
    called = False

    def unexpected_suite():
        nonlocal called
        called = True
        return {}

    monkeypatch.setattr(benchmark, "create_gms_model_suite", unexpected_suite)
    arguments = argparse.Namespace(
        env_file=None,
        dataset=benchmark.DATASET_PATH,
        repetitions=6,
        warmup=1,
        seed=1,
        output_dir=tmp_path,
    )

    with pytest.raises(ValueError, match="183-call"):
        await benchmark._run_cli(arguments)

    assert called is False


@pytest.mark.asyncio
async def test_official_cli_rejects_wrong_model_ids(monkeypatch, tmp_path) -> None:
    monkeypatch.setenv("AI_JSON_RETRY_COUNT", "2")
    monkeypatch.setattr(
        benchmark,
        "create_gms_model_suite",
        lambda: {
            "gemini-3.5-flash": RecordingProvider(),
            "gpt-5.4-mini": RecordingProvider(),
            "wrong-claude": RecordingProvider(),
        },
    )
    arguments = argparse.Namespace(
        env_file=None,
        dataset=benchmark.DATASET_PATH,
        repetitions=1,
        warmup=0,
        seed=1,
        output_dir=tmp_path,
    )

    with pytest.raises(ValueError, match="model ids"):
        await benchmark._run_cli(arguments)

    assert not list(tmp_path.iterdir())
    assert benchmark.os.environ["AI_JSON_RETRY_COUNT"] == "2"
