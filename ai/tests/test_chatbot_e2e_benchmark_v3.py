"""Offline contract tests for the Dogok Rexle hybrid E2E profile.

The suite uses only in-memory fakes and temporary local files.  It must never
run the evaluator or contact DB/embedding/web/GMS dependencies.
"""

from __future__ import annotations

import argparse
import asyncio
import json
from datetime import datetime, timezone
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.evaluation import chatbot_e2e_benchmark_v3 as target
from app.prompts.chatbot_prompt import SYSTEM_PROMPT, build_user_prompt
from app.providers.web_search_provider import (
    NullWebSearchProvider,
    WebSearchProvider,
    WebSearchResult,
)
from app.rag.search import ReportChunkHit
from app.schemas.chatbot import (
    REPORT_BASIS_LABEL,
    ChatbotAnswerResponse,
    ChatbotAnswerSource,
    LlmAnswerPayload,
)


def _arguments(**overrides: object) -> argparse.Namespace:
    values = {
        "e2e_dogok_rexle": True,
        "llm_mode": "scripted",
        "allow_deployment_db_read": True,
        "allow_live_web": True,
    }
    values.update(overrides)
    return argparse.Namespace(**values)


def _configuration():
    return target.load_configuration()


def test_frozen_dataset_and_policy_are_strictly_pinned() -> None:
    dataset, policy, dataset_digest, policy_digest = _configuration()

    assert len(dataset.cases) == 20
    assert policy.datasetSha256 == dataset_digest
    assert policy_digest == target.EXPECTED_POLICY_SHA256
    assert policy.datasetFile == target.EXPECTED_DATASET_FILE
    assert policy.metrics == target.EXPECTED_METRICS
    assert policy.categoryCounts == target.EXPECTED_CATEGORY_COUNTS
    assert policy.apartment.model_dump() == target.EXPECTED_APARTMENT
    assert policy.retrieval.hitK <= policy.retrieval.topK


def test_custom_dataset_and_policy_paths_are_rejected(tmp_path: Path) -> None:
    custom = tmp_path / "custom.json"
    custom.write_text("{}", encoding="utf-8")

    with pytest.raises(ValueError, match="custom.*datasets"):
        target.load_configuration(custom, target.POLICY_PATH)
    with pytest.raises(ValueError, match="custom.*policies"):
        target.load_configuration(target.DATASET_PATH, custom)


@pytest.mark.parametrize(
    ("override", "message"),
    [
        ({"allow_deployment_db_read": False}, "deployment"),
        ({"allow_live_web": False}, "live-web"),
        ({"llm_mode": "gms"}, "scripted"),
    ],
)
def test_execution_lock_requires_both_confirmations_and_scripted_mode(
    override: dict[str, object], message: str
) -> None:
    with pytest.raises(ValueError, match=message):
        target.validate_execution_lock(_arguments(**override))


def test_dependency_env_uses_only_protected_keys_from_explicit_file(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    env_file = tmp_path / "approved.env"
    env_file.write_text(
        "RAG_DB_HOST=approved-db\n"
        "WEB_SEARCH_PROVIDER=kakao\n"
        "AI_PROVIDER=gms-gemini\n",
        encoding="utf-8",
    )
    monkeypatch.setenv("RAG_DB_HOST", "ambient-db")
    monkeypatch.setenv("RAG_DB_NAME", "ambient-name")
    monkeypatch.setenv("AI_PROVIDER", "ambient-gms")

    target.load_isolated_dependency_env(env_file)

    assert target.os.environ["RAG_DB_HOST"] == "approved-db"
    assert target.os.environ["RAG_DB_NAME"] == ""
    assert target.os.environ["WEB_SEARCH_PROVIDER"] == "kakao"
    assert target.os.environ["AI_PROVIDER"] == "ambient-gms"


def test_case_and_retrieval_policy_coherence_is_enforced() -> None:
    with pytest.raises(ValidationError):
        target.DogokE2ECase(
            id="bad-web",
            category="web",
            question="q",
            answerMode="fixed",
            scriptedAnswer="answer",
            factRubric={"factGroups": [["answer"]]},
            reportSelector={"studyTitle": "report", "contentAll": ["answer"]},
        )
    with pytest.raises(ValidationError, match="hitK"):
        target.RetrievalPolicy(topK=10, hitK=11, maxContextChars=100)


def test_preflight_failure_happens_before_embedding_connection_or_web_search() -> None:
    calls = {"connection": 0}

    def connection_factory():
        calls["connection"] += 1
        raise AssertionError("must not connect")

    class Embedder:
        dimension = 768

        def encode_query(self, question: str):
            raise AssertionError("must not embed")

    _, policy, _, _ = _configuration()
    with pytest.raises(target.PreflightError, match="live web"):
        target.preflight(
            connection_factory=connection_factory,
            embedder=Embedder(),
            web_provider=NullWebSearchProvider(),
            policy=policy,
        )

    assert calls == {"connection": 0}


def test_preflight_rejects_non_finite_sentinel_before_database() -> None:
    class ConfiguredWeb(WebSearchProvider):
        def is_configured(self) -> bool:
            return True

        async def search(self, query: str, top_k: int):
            raise AssertionError("preflight must not search")

    class Embedder:
        dimension = target.EXPECTED_EMBEDDING_DIMENSION

        def encode_query(self, question: str):
            return [float("nan")] * target.EXPECTED_EMBEDDING_DIMENSION

    _, policy, _, _ = _configuration()
    with pytest.raises(target.PreflightError, match="finite"):
        target.preflight(
            connection_factory=lambda: pytest.fail("must not connect"),
            embedder=Embedder(),
            web_provider=ConfiguredWeb(),
            policy=policy,
        )


def test_scripted_provider_validates_real_prompt_and_uses_prompt_rank() -> None:
    dataset, policy, _, _ = _configuration()
    case = next(item for item in dataset.cases if item.id == "rag-subway-walk")
    assert case.reportSelector is not None
    report_id = 912345
    content = " ".join(case.reportSelector.contentAll + case.reportSelector.contentAny[:1])
    prompt_hits = [
        ReportChunkHit(111, "other", None, 0.99),
        ReportChunkHit(222, "other", None, 0.98),
        ReportChunkHit(report_id, content, None, 0.70),
    ]
    state = target.RuntimeState(
        case=case,
        report_ids_by_title={policy.requiredReportStudyTitles[0]: report_id},
        raw_hits=[prompt_hits[2], *prompt_hits[:2]],
        prompt_hits=prompt_hits,
    )
    provider = target.ScriptedPromptProvider(state, target.StageClock())
    prompt = build_user_prompt(case.question, None, prompt_hits, [])

    raw = asyncio.run(provider.complete_json(SYSTEM_PROMPT, prompt))

    assert raw["usedSources"] == [3]
    assert target._raw_report_rank(case, state, 60) == 1
    with pytest.raises(RuntimeError, match="question or numbered"):
        asyncio.run(provider.complete_json(SYSTEM_PROMPT, prompt + "tampered"))


def test_web_number_uses_prompt_offset_and_hostname_boundary() -> None:
    dataset, _, _, _ = _configuration()
    case = next(item for item in dataset.cases if item.id == "web-naver-current")
    now = datetime.now(timezone.utc)
    prompt_hits = [
        ReportChunkHit(1, "a", None, 0.9),
        ReportChunkHit(2, "b", None, 0.8),
    ]
    web_results = [
        WebSearchResult("other", "other", "https://example.com", "example.com", now),
        WebSearchResult(
                "도곡 렉슬",
            "current evidence",
            "https://new.land.naver.com/example",
            "new.land.naver.com",
            now,
        ),
    ]
    state = target.RuntimeState(
        case=case,
        prompt_hits=prompt_hits,
        web_results=web_results,
    )
    provider = target.ScriptedPromptProvider(state, target.StageClock())
    prompt = build_user_prompt(case.question, None, prompt_hits, web_results)

    raw = asyncio.run(provider.complete_json(SYSTEM_PROMPT, prompt))

    assert raw["usedSources"] == [4]
    assert target._hostname_matches("m.new.land.naver.com", "new.land.naver.com")
    assert not target._hostname_matches(
        "new.land.naver.com.attacker.test", "new.land.naver.com"
    )


def test_empty_live_web_is_classified_as_availability_failure() -> None:
    dataset, _, _, _ = _configuration()
    case = next(item for item in dataset.cases if item.category == "web")
    state = target.RuntimeState(case=case)
    provider = target.ScriptedPromptProvider(state, target.StageClock())
    prompt = build_user_prompt(case.question, None, [], [])

    with pytest.raises(target.WebAvailabilityError):
        asyncio.run(provider.complete_json(SYSTEM_PROMPT, prompt))
    assert target._failure_stage(target.WebAvailabilityError("empty")) == "web_availability"


def test_late_web_completion_cannot_pollute_next_generation() -> None:
    async def scenario() -> None:
        dataset, _, _, _ = _configuration()
        first, second = dataset.cases[:2]
        gate: asyncio.Future[list[WebSearchResult]] = asyncio.get_running_loop().create_future()

        class DeferredWeb(WebSearchProvider):
            async def search(self, query: str, top_k: int):
                return await gate

        state = target.RuntimeState()
        timings = target.StageClock()
        first_generation = state.begin(first)
        timings.reset(first_generation)
        provider = target.TimedLiveWebProvider(DeferredWeb(), state, timings)
        pending = asyncio.create_task(provider.search("first", 5))
        await asyncio.sleep(0)
        second_generation = state.begin(second)
        timings.reset(second_generation)
        gate.set_result(
            [
                WebSearchResult(
                    "late", "late", "https://example.com", "example.com", datetime.now(timezone.utc)
                )
            ]
        )
        await pending
        assert state.web_results == []
        assert "web" not in timings.snapshot()

    asyncio.run(scenario())


def test_local_v1_grounding_scorer_rejects_duplicates_wrong_number_and_forbidden() -> None:
    kwargs = {
        "required_answer_groups": [["정답"]],
        "forbidden_answer_terms": ["금지"],
        "acceptable_source_sets": [[2]],
        "report_count": 2,
        "web_count": 0,
    }
    assert target.score_v1_grounding(
        LlmAnswerPayload(answer="정답", usedSources=[2]), **kwargs
    ).evidence_correct
    assert not target.score_v1_grounding(
        LlmAnswerPayload(answer="정답", usedSources=[2, 2]), **kwargs
    ).evidence_correct
    assert not target.score_v1_grounding(
        LlmAnswerPayload(answer="정답", usedSources=[1]), **kwargs
    ).evidence_correct
    assert not target.score_v1_grounding(
        LlmAnswerPayload(answer="정답 금지", usedSources=[2]), **kwargs
    ).answer_correct


def test_final_report_mapping_requires_one_fully_matching_source() -> None:
    dataset, policy, _, _ = _configuration()
    case = next(item for item in dataset.cases if item.category == "rag")
    assert case.reportSelector is not None
    report_id = 701
    state = target.RuntimeState(
        report_ids_by_title={case.reportSelector.studyTitle: report_id}
    )
    raw = LlmAnswerPayload(answer="answer", usedSources=[1])
    exact = ChatbotAnswerResponse(
        basisType="REPORT",
        basisLabel=REPORT_BASIS_LABEL,
        answer="answer",
        sources=[
            ChatbotAnswerSource(
                sourceType="REPORT",
                sourceId=report_id,
                reportId=report_id,
                title=policy.apartment.name,
                url=None,
            )
        ],
    )
    wrong_source_id = exact.model_copy(deep=True)
    wrong_source_id.sources[0].sourceId = report_id + 1

    assert target._score_final_mapping(case, state, raw, exact)
    assert not target._score_final_mapping(case, state, raw, wrong_source_id)


def test_summary_counts_technical_errors_in_metric_denominators() -> None:
    dataset, policy, dataset_hash, policy_hash = _configuration()
    samples = [
        target.E2ESample(
            case_id="ok",
            category="rag",
            repetition=1,
            outcome="passed",
            failure_stage=None,
            answer_correct=True,
            evidence_correct=True,
            refusal_correct=None,
            mixed_citation_violation=False,
            retrieval_hit=True,
            reciprocal_rank=1.0,
            raw_source_count=1,
            final_mapping_correct=True,
            stage_ms={},
            e2e_ms=1.0,
        ),
        target.E2ESample(
            case_id="error",
            category="rag",
            repetition=1,
            outcome="error",
            failure_stage="database_rag",
            answer_correct=None,
            evidence_correct=False,
            refusal_correct=None,
            mixed_citation_violation=None,
            retrieval_hit=False,
            reciprocal_rank=0.0,
            raw_source_count=None,
            final_mapping_correct=False,
            stage_ms={},
            e2e_ms=1.0,
        ),
    ]
    summary = target.build_summary(
        dataset=dataset,
        policy=policy,
        dataset_hash=dataset_hash,
        policy_hash=policy_hash,
        results=samples,
        started_at=datetime.now(timezone.utc),
        finished_at=datetime.now(timezone.utc),
    )

    fact = summary["scoring"]["answerFactPassRate"]
    assert fact == {"numerator": 1, "denominator": 2, "rate": 0.5}
    assert summary["policySha256"] == policy_hash
    assert summary["seed"] == policy.run.seed


def test_serialized_artifacts_contain_no_raw_sensitive_material(tmp_path: Path) -> None:
    sample = target.E2ESample(
        case_id="safe",
        category="rag",
        repetition=1,
        outcome="passed",
        failure_stage=None,
        answer_correct=True,
        evidence_correct=True,
        refusal_correct=None,
        mixed_citation_violation=False,
        retrieval_hit=True,
        reciprocal_rank=1.0,
        raw_source_count=1,
        final_mapping_correct=True,
        stage_ms={"rag": 1.0},
        e2e_ms=2.0,
    )
    metric = {"numerator": 1, "denominator": 1, "rate": 1.0}
    run_dir = target.write_results(
        tmp_path,
        {
            "caseCount": 1,
            "passed": 1,
            "failed": 0,
            "errors": 0,
            "retrieval": {"k": 60, "hitAtK": metric, "mrr": metric},
            "scoring": {
                "answerFactPassRate": metric,
                "rawExactSourcePassRate": metric,
                "finalCitationMappingPassRate": metric,
            },
        },
        [sample],
    )
    combined = "\n".join(path.read_text("utf-8") for path in run_dir.iterdir())

    for forbidden in (
        "rawAnswer",
        "systemPrompt",
        "userPrompt",
        "webContent",
        "password",
    ):
        assert forbidden.lower() not in combined.lower()


def test_runner_has_no_transitive_v1_or_gms_import() -> None:
    source = Path(target.__file__).read_text(encoding="utf-8")

    assert "chatbot_model_benchmark import" not in source
    assert "create_gms_model_suite" not in source
    assert "create_llm_provider" not in source
    assert "default_transaction_read_only=on" in source
    assert "statement_timeout=" in source
