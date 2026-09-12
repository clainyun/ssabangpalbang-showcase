"""Network-free tests for the pinned project-fit benchmark v2."""

from __future__ import annotations

import argparse
import hashlib
from collections import Counter
from dataclasses import replace
from datetime import datetime, timezone

import pytest

from app.evaluation.chatbot_model_benchmark_v2 import (
    DATASET_V2_PATH,
    OFFICIAL_MODELS,
    POLICY_V2_PATH,
    OfficialBenchmarkRunner,
    OfficialSample,
    aggregate_official,
    build_official_summary,
    load_dataset_v2,
    load_policy_v2,
    score_payload_v2,
    strict_parse_payload,
    validate_official_profile,
    write_official_results,
)
from app.evaluation.gms_raw_client import GmsRawClient, RawCompletion
from app.schemas.chatbot import INSUFFICIENT_EVIDENCE_ANSWER, LlmAnswerPayload


class SuccessfulRawClient:
    def __init__(self, text: str = '{"answer":"근거가 부족해 확인할 수 없습니다.","usedSources":[]}') -> None:
        self.text = text
        self.calls: list[str] = []
        self.active = 0
        self.maximum = 0

    async def complete_raw(self, model, system_prompt, user_prompt):
        self.calls.append(model)
        self.active += 1
        self.maximum = max(self.maximum, self.active)
        self.active -= 1
        return RawCompletion("success", text=self.text)


def _official_args(**overrides):
    values = {
        "repetitions": 3,
        "warmup": 1,
        "seed": 20260809,
        "min_request_interval_ms": 6500,
        "cooldown_seconds": 300,
    }
    values.update(overrides)
    return argparse.Namespace(**values)


def _perfect_samples():
    dataset, _ = load_dataset_v2()
    samples = []
    call_order = 0
    for model in OFFICIAL_MODELS:
        for repetition in range(1, 4):
            for ordinal, case in enumerate(dataset.cases, 1):
                call_order += 1
                samples.append(
                    OfficialSample(
                        model=model,
                        case_id=case.id,
                        category=case.category,
                        repetition=repetition,
                        ordinal=(repetition - 1) * 20 + ordinal,
                        call_order=call_order,
                        prompt_hash="a" * 64,
                        elapsed_ms={OFFICIAL_MODELS[0]: 1000, OFFICIAL_MODELS[1]: 2000, OFFICIAL_MODELS[2]: 3000}[model],
                        outcome="success",
                        status_code=None,
                        used_sources=[],
                        fact_correct=True if case.factRubric else None,
                        refusal_correct=True if case.refusalRubric else None,
                        evidence_correct=True,
                        effective_task_correct=True,
                        unsupported_answer_violation=False if case.refusalRubric else None,
                        mixed_citation_violation=False if case.category == "mixed" else None,
                    )
                )
    return samples


def test_v2_dataset_policy_and_long_context_are_pinned() -> None:
    dataset, dataset_hash = load_dataset_v2()
    policy, policy_hash = load_policy_v2()

    assert len(dataset.cases) == 20
    assert Counter(case.category for case in dataset.cases) == {
        "direct_report": 4, "long_report": 6, "web": 3,
        "insufficient": 3, "profile": 2, "mixed": 2,
    }
    assert dataset_hash == policy.datasetSha256
    assert len(policy_hash) == 64
    assert policy.totalCalls == 183
    assert policy.models == list(OFFICIAL_MODELS)
    long = [case for case in dataset.cases if case.category == "long_report"]
    assert Counter(case.longContextChars for case in long) == {8000: 2, 20000: 2, 29000: 2}


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ('{"answer":"ok","usedSources":[1]}', "success"),
        ('```json\n{"answer":"ok","usedSources":[1]}\n```', "json"),
        ('{"answer":"ok","usedSources":[1]} trailing', "json"),
        ('{"answer":"ok","usedSources":[true]}', "schema"),
        ('{"answer":"ok","usedSources":["1"]}', "schema"),
        ('{"answer":"ok","usedSources":[1],"extra":1}', "schema"),
        ('["not-an-object"]', "schema"),
    ],
)
def test_common_parser_is_strict_for_every_provider(text, expected) -> None:
    outcome, _ = strict_parse_payload(text)
    assert outcome == expected


def test_fact_rubric_accepts_concise_answer_without_question_echo() -> None:
    dataset, _ = load_dataset_v2()
    case = next(case for case in dataset.cases if case.id == "direct-report-01")
    result = score_payload_v2(case, LlmAnswerPayload(answer="67.4%입니다.", usedSources=[1]))
    forbidden = score_payload_v2(case, LlmAnswerPayload(answer="67.4%가 아니라 71.0%입니다.", usedSources=[1]))

    assert result == (True, None, True, True, None)
    assert forbidden[0] is False


def test_refusal_and_raw_mixed_source_rules() -> None:
    dataset, _ = load_dataset_v2()
    refusal = next(case for case in dataset.cases if case.id == "insufficient-01")
    mixed = next(case for case in dataset.cases if case.category == "mixed")

    assert score_payload_v2(
        refusal,
        LlmAnswerPayload(answer="근거가 부족해 확인할 수 없습니다.", usedSources=[]),
    )[1:4] == (True, True, True)
    mixed_score = score_payload_v2(
        mixed,
        LlmAnswerPayload(answer="7시 10분입니다.", usedSources=[1, 2]),
    )
    assert mixed_score[4] is True
    assert mixed_score[2] is False

    standard_refusal = score_payload_v2(
        refusal,
        LlmAnswerPayload(answer=INSUFFICIENT_EVIDENCE_ANSWER, usedSources=[]),
    )
    assert standard_refusal[1:4] == (True, True, True)


def test_raw_decoders_map_to_canonical_outcomes() -> None:
    assert GmsRawClient._decode_gemini({"promptFeedback": {"blockReason": "SAFETY"}}).outcome == "policy"
    assert GmsRawClient._decode_gemini({"candidates": [{"finishReason": "MAX_TOKENS"}]}).outcome == "incomplete"
    assert GmsRawClient._decode_openai({"choices": [{"finish_reason": "content_filter"}]}).outcome == "policy"
    assert GmsRawClient._decode_openai({"choices": [{"finish_reason": "length"}]}).outcome == "incomplete"
    assert GmsRawClient._decode_anthropic({"stop_reason": "refusal"}).outcome == "policy"
    assert GmsRawClient._decode_anthropic({"stop_reason": "max_tokens"}).outcome == "incomplete"


def test_raw_decoders_require_explicit_success_finish_states() -> None:
    assert GmsRawClient._decode_gemini({"candidates": [{"content": {"parts": [{"text": "{}"}]}}]}).outcome == "incomplete"
    assert GmsRawClient._decode_openai({"choices": [{"message": {"content": "{}"}}]}).outcome == "incomplete"
    assert GmsRawClient._decode_anthropic({"content": [{"type": "text", "text": "{}"}]}).outcome == "incomplete"


@pytest.mark.asyncio
async def test_runner_uses_single_lane_latin_square_and_exact_183_calls() -> None:
    dataset, _ = load_dataset_v2()
    policy, _ = load_policy_v2()
    client = SuccessfulRawClient()
    now = 0.0
    sleeps: list[float] = []

    def clock():
        return now

    async def sleep(seconds):
        nonlocal now
        sleeps.append(seconds)
        now += seconds

    results = await OfficialBenchmarkRunner(
        client, policy.models, clock=clock, schedule_clock=clock, sleeper=sleep
    ).run(dataset, policy)

    assert len(client.calls) == 183
    assert len(results) == 180
    assert client.maximum == 1
    assert sleeps.count(300.0) == 1
    assert sum(value == 6.5 for value in sleeps) == 181
    first_by_ordinal = [
        min((sample for sample in results if sample.ordinal == ordinal), key=lambda sample: sample.call_order).model
        for ordinal in range(1, 61)
    ]
    assert Counter(first_by_ordinal) == {model: 20 for model in OFFICIAL_MODELS}


def test_scheduled_availability_is_separate_from_conditional_quality() -> None:
    policy, _ = load_policy_v2()
    samples = _perfect_samples()
    first = next(sample for sample in samples if sample.model == OFFICIAL_MODELS[0])
    samples[samples.index(first)] = replace(
        first, outcome="transport", used_sources=None, fact_correct=None,
        evidence_correct=None, effective_task_correct=None,
    )
    metrics = aggregate_official(samples, policy)[OFFICIAL_MODELS[0]]

    assert metrics["completion_rate"] == {"numerator": 59, "denominator": 60, "rate": 0.983333}
    assert metrics["effective_task_accuracy_conditional"] == {"numerator": 59, "denominator": 59, "rate": 1.0}
    assert metrics["effective_task_case_macro"] == {
        "numerator": 19.666667,
        "denominator": 20,
        "rate": 0.983333,
    }
    assert metrics["latency_ms"]["successful_n"] == 59


def test_any_rate_limit_withholds_leaderboard_even_when_gates_pass() -> None:
    dataset, dataset_hash = load_dataset_v2()
    policy, policy_hash = load_policy_v2()
    samples = _perfect_samples()
    sample = samples[0]
    samples[0] = replace(
        sample, outcome="rate-limit", status_code=429, used_sources=None,
        fact_correct=None, evidence_correct=None, effective_task_correct=None,
    )
    now = datetime(2026, 8, 9, tzinfo=timezone.utc)
    summary = build_official_summary(
        dataset=dataset, dataset_hash=dataset_hash, policy=policy,
        policy_hash=policy_hash, results=samples, run_id="run", started_at=now, finished_at=now,
    )

    assert summary["quality_comparison_valid"] is False
    assert summary["quality_comparison_invalid_reason"] == "rate-limit-observed"
    assert summary["leaderboard"] == []


def test_perfect_results_apply_gates_weights_and_latency_score() -> None:
    dataset, dataset_hash = load_dataset_v2()
    policy, policy_hash = load_policy_v2()
    now = datetime(2026, 8, 9, tzinfo=timezone.utc)
    summary = build_official_summary(
        dataset=dataset, dataset_hash=dataset_hash, policy=policy,
        policy_hash=policy_hash, results=_perfect_samples(), run_id="run", started_at=now, finished_at=now,
    )

    assert summary["quality_comparison_valid"] is True
    assert summary["leaderboard"][0]["model"] == OFFICIAL_MODELS[0]
    assert summary["models"][OFFICIAL_MODELS[0]]["project_fit_score"] == 100.0
    assert summary["models"][OFFICIAL_MODELS[1]]["project_fit_score"] == 95.0
    assert all(metrics["gate_pass"] for metrics in summary["models"].values())


def test_profile_validation_rejects_runtime_drift_before_calls() -> None:
    dataset, dataset_hash = load_dataset_v2()
    policy, policy_hash = load_policy_v2()
    validate_official_profile(
        dataset=dataset, dataset_hash=dataset_hash, policy=policy,
        policy_hash=policy_hash, arguments=_official_args(),
    )
    with pytest.raises(ValueError, match="runtime arguments"):
        validate_official_profile(
            dataset=dataset, dataset_hash=dataset_hash, policy=policy,
            policy_hash=policy_hash, arguments=_official_args(seed=7),
        )


def test_atomic_publish_has_180_samples_checksums_and_no_sensitive_text(tmp_path) -> None:
    dataset, dataset_hash = load_dataset_v2()
    policy, policy_hash = load_policy_v2()
    now = datetime(2026, 8, 9, tzinfo=timezone.utc)
    samples = _perfect_samples()
    summary = build_official_summary(
        dataset=dataset, dataset_hash=dataset_hash, policy=policy,
        policy_hash=policy_hash, results=samples, run_id="safe-run", started_at=now, finished_at=now,
    )
    run_dir = write_official_results(tmp_path, summary, samples)

    assert not (tmp_path / ".safe-run.staging").exists()
    assert set(path.name for path in run_dir.iterdir()) == {
        "samples.jsonl", "summary.json", "summary.csv", "summary.md", "checksums.sha256"
    }
    assert len((run_dir / "samples.jsonl").read_text(encoding="utf-8").splitlines()) == 180
    for line in (run_dir / "checksums.sha256").read_text(encoding="utf-8").splitlines():
        expected, name = line.split("  ", 1)
        assert hashlib.sha256((run_dir / name).read_bytes()).hexdigest() == expected
    combined = "\n".join(path.read_text(encoding="utf-8") for path in run_dir.iterdir())
    for forbidden in ("Authorization", "x-api-key", "x-goog-api-key", "Bearer ", "systemInstruction"):
        assert forbidden not in combined


def test_official_files_are_the_expected_defaults() -> None:
    assert DATASET_V2_PATH.name == "chatbot_grounding_v2.json"
    assert POLICY_V2_PATH.name == "evaluation_policy_v2.json"
