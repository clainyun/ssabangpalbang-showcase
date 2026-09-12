"""Offline verification for the pinned production-fit GMS benchmark v3."""

from __future__ import annotations

import copy
import hashlib
from collections import Counter
from datetime import datetime, timezone

import pytest
from pydantic import ValidationError

import app.evaluation.chatbot_model_benchmark_v3 as benchmark
from app.evaluation.chatbot_model_benchmark_v3 import (
    BenchmarkCaseV3,
    GmsProductionClient,
    OfficialBenchmarkRunnerV3,
    OfficialRunV3,
    ProductionCompletion,
    RefusalRubricV3,
    build_official_summary_v3,
    load_dataset_v3,
    load_policy_v3,
    score_payload_v3,
    validate_official_profile_v3,
    validate_result_matrix,
    write_official_results_v3,
)
from app.providers.llm_provider import FakeLlmProvider, LlmProviderError
from app.schemas.chatbot import LlmAnswerPayload


class ScriptedClient:
    def __init__(
        self,
        *,
        warmup_rate_limit: bool = False,
        measured_rate_limit: bool = False,
        answer: str = "SENSITIVE_RAW_ANSWER_CANARY",
    ) -> None:
        self.calls: list[str] = []
        self.warmup_rate_limit = warmup_rate_limit
        self.measured_rate_limit = measured_rate_limit
        self.answer = answer

    async def complete(self, model, system_prompt, user_prompt):
        self.calls.append(model)
        if self.warmup_rate_limit and len(self.calls) == 1:
            return ProductionCompletion("rate-limit", status_code=429)
        if self.measured_rate_limit and len(self.calls) == 4:
            return ProductionCompletion("rate-limit", status_code=429)
        return ProductionCompletion(
            "success",
            payload=LlmAnswerPayload(answer=self.answer, usedSources=[]),
        )


async def _no_sleep(seconds: float) -> None:
    return None


def _official_args(**overrides):
    values = {
        "repetitions": 3,
        "warmup": 1,
        "seed": 20260809,
        "min_request_interval_ms": 6500,
        "cooldown_seconds": 300,
    }
    values.update(overrides)
    return type("Arguments", (), values)()


async def _run_scripted(**client_options):
    dataset, dataset_hash = load_dataset_v3()
    policy, policy_hash = load_policy_v3()
    client = ScriptedClient(**client_options)
    run = await OfficialBenchmarkRunnerV3(
        client,
        policy.models,
        sleeper=_no_sleep,
    ).run(dataset, policy)
    return dataset, dataset_hash, policy, policy_hash, client, run


def test_v3_dataset_policy_hashes_and_middle_position_are_pinned() -> None:
    dataset, dataset_hash = load_dataset_v3()
    policy, _ = load_policy_v3()

    validate_official_profile_v3(
        dataset=dataset,
        dataset_hash=dataset_hash,
        policy=policy,
        arguments=_official_args(),
    )
    assert len(dataset.cases) == 20
    assert dataset_hash == policy.datasetSha256
    assert len(policy.promptSha256ByCase) == 20
    assert policy.anthropicMaxTokens == 4096
    assert policy.providerModes.model_dump() == {
        "gemini": "json-mime-response-schema",
        "openai": "json-object-developer-role",
        "anthropic": "messages-prompt-json",
    }

    middle = next(
        case for case in dataset.cases if case.id == "long-report-8k-middle"
    )
    lengths = [len(item.expanded_content()) for item in middle.reportEvidence]
    assert sum(lengths[:1]) >= 3200
    assert sum(lengths[2:]) >= 3200


def test_policy_or_runtime_drift_fails_preflight() -> None:
    dataset, dataset_hash = load_dataset_v3()
    policy, _ = load_policy_v3()
    changed_prompt = dict(policy.promptSha256ByCase)
    changed_prompt[dataset.cases[0].id] = "0" * 64

    with pytest.raises(ValueError, match="prompt manifest"):
        validate_official_profile_v3(
            dataset=dataset,
            dataset_hash=dataset_hash,
            policy=policy.model_copy(
                update={"promptSha256ByCase": changed_prompt}
            ),
            arguments=_official_args(),
        )
    with pytest.raises(ValueError, match="evaluator hash"):
        validate_official_profile_v3(
            dataset=dataset,
            dataset_hash=dataset_hash,
            policy=policy.model_copy(update={"evaluatorSha256": "0" * 64}),
            arguments=_official_args(),
        )
    with pytest.raises(ValueError, match="runtime arguments"):
        validate_official_profile_v3(
            dataset=dataset,
            dataset_hash=dataset_hash,
            policy=policy,
            arguments=_official_args(repetitions=2),
        )


def test_refusal_rubric_requires_nonblank_terms_and_exact_empty_sources() -> None:
    with pytest.raises(ValidationError):
        RefusalRubricV3(markers=[""], forbiddenClaims=[])

    dataset, _ = load_dataset_v3()
    refusal_case = next(
        case for case in dataset.cases if case.id == "insufficient-01"
    )
    changed = refusal_case.model_dump()
    changed["acceptableSourceSets"] = [[], [1]]
    with pytest.raises(ValidationError, match="exactly an empty source set"):
        BenchmarkCaseV3.model_validate(changed)


def test_numeric_fact_and_negated_refusal_are_boundary_aware() -> None:
    dataset, _ = load_dataset_v3()
    fact_case = next(
        case for case in dataset.cases if case.id == "direct-report-01"
    )
    wrong_fact = score_payload_v3(
        fact_case,
        LlmAnswerPayload(answer="전세가율은 167.4%입니다.", usedSources=[1]),
    )
    right_fact = score_payload_v3(
        fact_case,
        LlmAnswerPayload(answer="전세가율은 67.4%입니다.", usedSources=[1]),
    )
    assert wrong_fact[0] is False
    assert right_fact[0] is True

    refusal_case = next(
        case for case in dataset.cases if case.id == "insufficient-01"
    )
    negated = score_payload_v3(
        refusal_case,
        LlmAnswerPayload(
            answer="확인할 수 없는 것이 아니라 확인 가능합니다.",
            usedSources=[],
        ),
    )
    affirmative = score_payload_v3(
        refusal_case,
        LlmAnswerPayload(answer="현재 근거로는 확인할 수 없습니다.", usedSources=[]),
    )
    assert negated[1] is False
    assert affirmative[1] is True


@pytest.mark.asyncio
async def test_production_client_separates_schema_429_and_programming_errors() -> None:
    models = benchmark.OFFICIAL_MODELS
    schema_client = GmsProductionClient(
        {
            model: FakeLlmProvider(
                payload={"answer": "ok", "usedSources": [], "extra": True}
            )
            for model in models
        }
    )
    assert (await schema_client.complete(models[0], "s", "u")).outcome == "schema"

    rate_client = GmsProductionClient(
        {
            model: FakeLlmProvider(
                error=LlmProviderError("hidden", status_code=429)
            )
            for model in models
        }
    )
    rate_result = await rate_client.complete(models[0], "s", "u")
    assert rate_result.outcome == "rate-limit"
    assert rate_result.status_code == 429

    bug_client = GmsProductionClient(
        {
            model: FakeLlmProvider(error=RuntimeError("programming bug"))
            for model in models
        }
    )
    with pytest.raises(RuntimeError, match="programming bug"):
        await bug_client.complete(models[0], "s", "u")


@pytest.mark.asyncio
async def test_runner_uses_exact_183_call_matrix_and_aborts_on_unexpected() -> None:
    dataset, _, policy, _, client, run = await _run_scripted()

    assert len(client.calls) == 183
    assert len(run.warmups) == 3
    assert len(run.samples) == 180
    validate_result_matrix(dataset, policy, run)
    first_positions = Counter(
        run.samples[index].model for index in range(0, len(run.samples), 3)
    )
    assert first_positions == Counter({model: 20 for model in policy.models})

    class BrokenClient:
        async def complete(self, model, system_prompt, user_prompt):
            raise RuntimeError("unexpected")

    with pytest.raises(RuntimeError, match="unexpected"):
        await OfficialBenchmarkRunnerV3(
            BrokenClient(), policy.models, sleeper=_no_sleep
        ).run(dataset, policy)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("options", "expected_warmup_rate_limits", "expected_measured_rate_limits"),
    [
        ({"warmup_rate_limit": True}, 1, 0),
        ({"measured_rate_limit": True}, 0, 1),
    ],
)
async def test_any_429_invalidates_leaderboard(
    options,
    expected_warmup_rate_limits,
    expected_measured_rate_limits,
) -> None:
    dataset, dataset_hash, policy, policy_hash, _, run = await _run_scripted(
        **options
    )
    summary = build_official_summary_v3(
        dataset=dataset,
        dataset_hash=dataset_hash,
        policy=policy,
        policy_hash=policy_hash,
        run=run,
        run_id="rate-limit-run",
        started_at=datetime(2026, 8, 10, tzinfo=timezone.utc),
        finished_at=datetime(2026, 8, 10, 0, 1, tzinfo=timezone.utc),
    )

    assert summary["quality_comparison_valid"] is False
    assert summary["leaderboard"] == []
    assert summary["warmup"]["rate_limit_count"] == expected_warmup_rate_limits
    assert sum(
        metrics["failure_kinds"].get("rate-limit", 0)
        for metrics in summary["models"].values()
    ) == expected_measured_rate_limits


@pytest.mark.asyncio
async def test_publish_rejects_bad_matrix_or_summary_before_final_directory(
    tmp_path,
) -> None:
    dataset, dataset_hash, policy, policy_hash, _, run = await _run_scripted()
    started = datetime(2026, 8, 10, tzinfo=timezone.utc)
    finished = datetime(2026, 8, 10, 0, 1, tzinfo=timezone.utc)
    summary = build_official_summary_v3(
        dataset=dataset,
        dataset_hash=dataset_hash,
        policy=policy,
        policy_hash=policy_hash,
        run=run,
        run_id="integrity-run",
        started_at=started,
        finished_at=finished,
    )

    bad_samples = [run.samples[0], *run.samples[:-1]]
    with pytest.raises(ValueError, match="duplicate"):
        write_official_results_v3(
            tmp_path,
            summary,
            dataset=dataset,
            dataset_hash=dataset_hash,
            policy=policy,
            policy_hash=policy_hash,
            run=OfficialRunV3(run.warmups, bad_samples),
        )
    assert not (tmp_path / "integrity-run").exists()

    bad_summary = copy.deepcopy(summary)
    bad_summary["models"][policy.models[0]]["completion_rate"]["numerator"] = 0
    with pytest.raises(ValueError, match="summary"):
        write_official_results_v3(
            tmp_path,
            bad_summary,
            dataset=dataset,
            dataset_hash=dataset_hash,
            policy=policy,
            policy_hash=policy_hash,
            run=run,
        )
    assert not (tmp_path / "integrity-run").exists()


@pytest.mark.asyncio
async def test_atomic_publish_has_checksums_fractions_and_no_raw_answer(
    tmp_path,
) -> None:
    dataset, dataset_hash, policy, policy_hash, _, run = await _run_scripted()
    summary = build_official_summary_v3(
        dataset=dataset,
        dataset_hash=dataset_hash,
        policy=policy,
        policy_hash=policy_hash,
        run=run,
        run_id="safe-run-v3",
        started_at=datetime(2026, 8, 10, tzinfo=timezone.utc),
        finished_at=datetime(2026, 8, 10, 0, 1, tzinfo=timezone.utc),
    )

    run_dir = write_official_results_v3(
        tmp_path,
        summary,
        dataset=dataset,
        dataset_hash=dataset_hash,
        policy=policy,
        policy_hash=policy_hash,
        run=run,
    )

    assert {path.name for path in run_dir.iterdir()} == {
        "samples.jsonl",
        "summary.json",
        "summary.csv",
        "summary.md",
        "checksums.sha256",
        "COMPLETE",
    }
    assert len(
        (run_dir / "samples.jsonl").read_text(encoding="utf-8").splitlines()
    ) == 180
    for line in (run_dir / "checksums.sha256").read_text(
        encoding="utf-8"
    ).splitlines():
        expected, name = line.split("  ", 1)
        assert hashlib.sha256((run_dir / name).read_bytes()).hexdigest() == expected
    complete_hash, name = (run_dir / "COMPLETE").read_text(
        encoding="utf-8"
    ).strip().split("  ", 1)
    assert name == "checksums.sha256"
    assert hashlib.sha256((run_dir / name).read_bytes()).hexdigest() == complete_hash

    combined = "\n".join(
        path.read_text(encoding="utf-8") for path in run_dir.iterdir()
    )
    for forbidden in (
        "SENSITIVE_RAW_ANSWER_CANARY",
        "Authorization",
        "x-api-key",
        "x-goog-api-key",
        "Bearer ",
        "systemInstruction",
    ):
        assert forbidden not in combined
    for metrics in summary["models"].values():
        for name in (
            "completion_rate",
            "effective_task_case_macro",
            "long_task_success",
            "safe_completion",
            "mixed_compliant_completion",
            "safety_mixed_compliance",
        ):
            assert "numerator" in metrics[name]
            assert "denominator" in metrics[name]
        assert metrics["latency_ms"]["successful_n"] == 60


def test_official_files_are_v3_defaults() -> None:
    assert benchmark.DATASET_V3_PATH.name == "chatbot_grounding_v3.json"
    assert benchmark.POLICY_V3_PATH.name == "evaluation_policy_v3.json"
