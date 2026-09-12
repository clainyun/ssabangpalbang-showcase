"""Offline regression tests for the checklist model benchmark."""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone

import pytest

import app.evaluation.checklist_model_benchmark as benchmark
from app.evaluation.checklist_model_benchmark import (
    BenchmarkRun,
    BenchmarkSample,
    ChecklistBenchmarkRunner,
    SelectionFailure,
    WarmupSample,
    aggregate_results,
    build_requests,
    build_summary,
    evaluate_payload,
    load_catalog,
    load_dataset,
    load_policy,
    validate_official_profile,
    write_results,
)
from app.prompts.checklist_select_prompt import build_select_user_prompt
from app.providers.gms_model_suite import (
    CHECKLIST_BENCHMARK_MODELS,
    CHECKLIST_OPENAI_MODEL,
    GEMINI_MODEL,
    GmsChecklistBenchmarkSettings,
    create_gms_checklist_benchmark_suite,
)
from app.providers.llm_provider import (
    GmsGeminiProvider,
    LlmProviderError,
    OpenAiCompatibleProvider,
)
from app.schemas.checklist_select import select_response_json_schema


def _inputs():
    dataset, dataset_hash = load_dataset()
    catalog, catalog_hash = load_catalog()
    policy, policy_hash = load_policy()
    requests = build_requests(catalog, dataset, policy)
    return dataset, dataset_hash, catalog, catalog_hash, policy, policy_hash, requests


def _test_policy(policy, *, repetitions=1, warmup=0):
    return policy.model_copy(
        update={
            "repetitions": repetitions,
            "warmupPerModel": warmup,
            "totalCalls": (
                policy.caseCount * repetitions + warmup
            )
            * len(policy.models),
            "minRequestIntervalMs": 0,
            "cooldownAfterWarmupSeconds": 0,
        }
    )


class PromptSelectionProvider:
    def __init__(self, *, error: Exception | None = None) -> None:
        self.error = error
        self.calls = 0

    async def complete_json(self, system_prompt: str, user_prompt: str) -> dict:
        self.calls += 1
        if self.error is not None:
            raise self.error
        marker = "shortlist(JSON):\n"
        payload = json.loads(user_prompt.split(marker, 1)[1].split("\n\n", 1)[0])
        return {"itemCodes": [item["itemCode"] for item in payload[:25]]}


def test_two_model_factory_is_exact_and_does_not_require_anthropic():
    settings = GmsChecklistBenchmarkSettings.from_mapping(
        {
            "GMS_KEY": "one-shared-secret",
            "GMS_GEMINI_ENDPOINT_URL": (
                "https://ai-gateway.example.com/testing/gemini/"
                "v1beta/models/gemini-3.5-flash:generateContent"
            ),
            "GMS_OPENAI_ENDPOINT_URL": (
                "https://ai-gateway.example.com/testing/openai/v1/chat/completions"
            ),
        }
    )

    suite = create_gms_checklist_benchmark_suite(
        settings,
        response_schema=select_response_json_schema(),
    )

    assert tuple(suite) == CHECKLIST_BENCHMARK_MODELS
    assert isinstance(suite[GEMINI_MODEL], GmsGeminiProvider)
    assert isinstance(suite[CHECKLIST_OPENAI_MODEL], OpenAiCompatibleProvider)
    assert suite[GEMINI_MODEL].model == "gemini-3.5-flash"
    assert suite[CHECKLIST_OPENAI_MODEL].model == "gpt-5.4-nano"
    assert suite[GEMINI_MODEL].json_retry_count == 0
    assert suite[GEMINI_MODEL].structured_output is True
    assert suite[CHECKLIST_OPENAI_MODEL].instruction_role == "developer"
    assert "one-shared-secret" not in repr(settings)


@pytest.mark.parametrize(
    ("name", "value"),
    [
        ("GMS_KEY", ""),
        ("GMS_OPENAI_ENDPOINT_URL", "https://attacker.example/chat/completions"),
    ],
)
def test_two_model_factory_rejects_missing_or_unsafe_configuration(name, value):
    values = {
        "GMS_KEY": "secret",
        "GMS_GEMINI_ENDPOINT_URL": (
            "https://ai-gateway.example.com/testing/gemini/"
            "v1beta/models/gemini-3.5-flash:generateContent"
        ),
        "GMS_OPENAI_ENDPOINT_URL": (
            "https://ai-gateway.example.com/testing/openai/v1/chat/completions"
        ),
    }
    values[name] = value

    with pytest.raises(LlmProviderError) as exc:
        GmsChecklistBenchmarkSettings.from_mapping(values)

    assert "secret" not in str(exc.value)


def test_catalog_requests_are_deterministic_and_operationally_sized():
    dataset, _, catalog, _, policy, _, first = _inputs()
    second = build_requests(catalog, dataset, policy)

    assert set(first) == {case.id for case in dataset.cases}
    assert {
        case_id: request.model_dump_json()
        for case_id, request in first.items()
    } == {
        case_id: request.model_dump_json()
        for case_id, request in second.items()
    }
    for request in first.values():
        assert policy.targetItemCount <= len(request.shortlist) <= policy.shortlistSize
        assert any(item.isCommonCore for item in request.shortlist)
        assert any(item.categoryCode == "SUM" for item in request.shortlist)
        assert request.targetItemCount == 25


def test_top_scored_selection_passes_and_scores_expected_dimensions():
    dataset, _, _, _, policy, _, requests = _inputs()
    case = dataset.cases[0]
    request = requests[case.id]
    codes = [item.itemCode for item in request.shortlist[:25]]

    selected, metrics = evaluate_payload(
        {"itemCodes": codes},
        request=request,
        case=case,
        policy=policy,
    )

    assert selected == codes
    assert metrics.schema_valid is True
    assert metrics.operational_pass is True
    assert metrics.target_count_match is True
    assert metrics.common_core_present is True
    assert metrics.summary_present is True
    assert metrics.priority_alignment == 1.0
    assert metrics.server_score_ndcg == pytest.approx(1.0)
    assert 0 < metrics.composite_score <= 1


def test_invalid_and_policy_failing_responses_are_not_repaired():
    dataset, _, _, _, policy, _, requests = _inputs()
    case = dataset.cases[0]
    request = requests[case.id]
    valid_codes = [item.itemCode for item in request.shortlist[:25]]

    with pytest.raises(SelectionFailure) as duplicate:
        evaluate_payload(
            {"itemCodes": valid_codes[:-1] + [valid_codes[0]]},
            request=request,
            case=case,
            policy=policy,
        )
    assert duplicate.value.outcome == "schema"

    without_summary = [
        item.itemCode for item in request.shortlist if item.categoryCode != "SUM"
    ][:25]
    with pytest.raises(SelectionFailure) as policy_failure:
        evaluate_payload(
            {"itemCodes": without_summary},
            request=request,
            case=case,
            policy=policy,
        )
    assert policy_failure.value.outcome == "policy"
    assert policy_failure.value.metrics.schema_valid is True
    assert policy_failure.value.metrics.operational_pass is False
    assert policy_failure.value.metrics.composite_score == 0


@pytest.mark.asyncio
async def test_runner_uses_equal_matrix_and_failures_remain_in_denominator():
    dataset, _, _, _, policy, _, requests = _inputs()
    test_policy = _test_policy(policy)
    valid = PromptSelectionProvider()
    limited = PromptSelectionProvider(
        error=LlmProviderError("limited", status_code=429)
    )
    runner = ChecklistBenchmarkRunner(
        {
            GEMINI_MODEL: valid,
            CHECKLIST_OPENAI_MODEL: limited,
        },
        test_policy,
    )

    run = await runner.run(dataset, requests)
    models, comparison_valid = aggregate_results(run, test_policy)

    assert len(run.samples) == len(dataset.cases) * 2
    assert valid.calls == limited.calls == len(dataset.cases)
    assert models[GEMINI_MODEL]["operational_pass_rate"] == {
        "numerator": len(dataset.cases),
        "denominator": len(dataset.cases),
        "value": 1.0,
    }
    assert models[CHECKLIST_OPENAI_MODEL]["operational_pass_rate"] == {
        "numerator": 0,
        "denominator": len(dataset.cases),
        "value": 0.0,
    }
    assert models[CHECKLIST_OPENAI_MODEL]["project_fit_score"] == 0
    assert comparison_valid is False


def test_nearest_rank_p95_is_not_interpolated():
    assert benchmark._nearest_rank([1, 2, 3, 4, 100], 0.95) == 100
    assert benchmark._nearest_rank([], 0.95) is None


def test_official_profile_hashes_prevent_calls_after_input_change():
    dataset, dataset_hash, _, catalog_hash, policy, _, requests = _inputs()
    arguments = argparse.Namespace(
        repetitions=3,
        warmup=1,
        seed=20260811,
        min_request_interval_ms=6500,
        cooldown_seconds=60,
    )

    validate_official_profile(
        dataset=dataset,
        dataset_hash=dataset_hash,
        catalog_hash=catalog_hash,
        policy=policy,
        requests=requests,
        arguments=arguments,
    )
    with pytest.raises(ValueError, match="dataset hash"):
        validate_official_profile(
            dataset=dataset,
            dataset_hash="changed",
            catalog_hash=catalog_hash,
            policy=policy,
            requests=requests,
            arguments=arguments,
        )


def _successful_sample(model: str, case_id: str, repetition: int) -> BenchmarkSample:
    return BenchmarkSample(
        case_id=case_id,
        model=model,
        repetition=repetition,
        outcome="success",
        latency_ms=10.0,
        selected_codes=[f"CODE_{index}" for index in range(25)],
        selected_count=25,
        schema_valid=True,
        operational_pass=True,
        target_count_match=True,
        common_core_present=True,
        summary_present=True,
        priority_alignment=1.0,
        purpose_alignment=1.0,
        server_score_ndcg=1.0,
        category_diversity=1.0,
        composite_score=1.0,
    )


def test_result_bundle_has_matching_checksums_and_complete_marker(tmp_path):
    dataset, dataset_hash, _, catalog_hash, policy, policy_hash, requests = _inputs()
    samples = [
        _successful_sample(model, case.id, repetition)
        for repetition in range(1, policy.repetitions + 1)
        for case in dataset.cases
        for model in policy.models
    ]
    run = BenchmarkRun(
        warmups=[WarmupSample(model, "response", 1.0) for model in policy.models],
        samples=samples,
    )
    now = datetime(2026, 8, 11, tzinfo=timezone.utc)
    summary = build_summary(
        run=run,
        policy=policy,
        policy_hash=policy_hash,
        dataset_hash=dataset_hash,
        catalog_hash=catalog_hash,
        requests=requests,
        run_id="20260811T000000000000Z",
        started_at=now,
        finished_at=now,
    )

    output = write_results(tmp_path, summary=summary, run=run)

    assert summary["quality_comparison_valid"] is True
    assert len(summary["leaderboard"]) == 2
    manifest = (output / "checksums.sha256").read_bytes()
    for line in manifest.decode("utf-8").splitlines():
        expected, name = line.split("  ", 1)
        assert hashlib.sha256((output / name).read_bytes()).hexdigest() == expected
    assert (output / "COMPLETE").read_text(encoding="utf-8") == (
        f"{hashlib.sha256(manifest).hexdigest()}  checksums.sha256\n"
    )
    assert len((output / "samples.jsonl").read_text(encoding="utf-8").splitlines()) == 72
    assert not any(path.name.endswith(".staging") for path in tmp_path.iterdir())
