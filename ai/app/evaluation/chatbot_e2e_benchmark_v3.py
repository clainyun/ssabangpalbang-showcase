"""Dogok Rexle hybrid E2E benchmark with a deterministic LLM boundary.

This profile deliberately exercises the production ``ChatbotAnswerService``
path: the deployment PostgreSQL database is opened read-only, the shared query
embedder and pgvector search are real, and the configured web provider is live.
Only the LLM boundary is scripted.  Consequently this module never imports or
constructs the GMS model suite and its output is not a model-quality result.

The CLI is intentionally locked behind two independent acknowledgements.  A
failed preflight exits before the first case (and therefore before web search).
"""

from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import math
import os
import sys
import time
import unicodedata
from collections import Counter, defaultdict
from collections.abc import Callable, Mapping, Sequence
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal
from urllib.parse import urlsplit

from dotenv import dotenv_values
from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.prompts.chatbot_prompt import SYSTEM_PROMPT, build_user_prompt
from app.providers.llm_provider import LlmProvider
from app.providers.web_search_provider import (
    NullWebSearchProvider,
    WebSearchProvider,
    WebSearchResult,
    create_web_search_provider,
)
from app.rag.config import RagSettings
from app.rag.embedder import EXPECTED_EMBEDDING_DIMENSION, get_shared_embedder
from app.rag.search import ApartmentProfile, ReportChunkHit
from app.rag.settings_chatbot import ChatbotSettings, WebSearchSettings
from app.schemas.chatbot import (
    ChatbotAnswerRequest,
    ChatbotAnswerResponse,
    LlmAnswerPayload,
)
from app.services.chatbot_answer_service import (
    ChatbotAnswerError,
    ChatbotAnswerService,
)


EVALUATION_DIR = Path(__file__).resolve().parent
DATASET_PATH = (
    EVALUATION_DIR
    / "datasets"
    / "chatbot_grounding_dogok_rexle_e2e_v3.json"
)
POLICY_PATH = EVALUATION_DIR / "evaluation_policy_dogok_rexle_e2e_v3.json"
MODEL_LABEL = "scripted-llm-boundary"
NOT_MODEL_QUALITY = (
    "This run does not measure GMS or model quality; the LLM boundary is scripted."
)
EXPECTED_POLICY_ID = "chatbot-dogok-rexle-e2e-v3"
EXPECTED_PROFILE = "deployment-db-real-rag-live-web-scripted-llm"
EXPECTED_DATASET_FILE = "datasets/chatbot_grounding_dogok_rexle_e2e_v3.json"
EXPECTED_DATASET_ID = "chatbot-grounding-dogok-rexle-e2e-v3"
EXPECTED_POLICY_SHA256 = "3be6b6a5bc2d2a499855bdff278d8ae32bcbc809aa4028f08b448c6eb1792b9a"
EXPECTED_APARTMENT = {"complexCode": "A13527203", "name": "도곡 렉슬"}
EXPECTED_REPORT_TITLES = [
    "도곡렉슬 임장 기록 #1",
    "도곡렉슬 임장 기록 #2",
    "도곡렉슬 임장 기록 #3",
]
EXPECTED_CATEGORY_COUNTS = {
    "rag": 8,
    "profile": 3,
    "web": 3,
    "insufficient": 3,
    "priority": 3,
}
EXPECTED_METRICS = [
    "retrieval_hit_at_k",
    "retrieval_mrr",
    "answer_fact_pass_rate",
    "raw_exact_source_pass_rate",
    "final_citation_mapping_pass_rate",
    "stage_failure_counts",
    "stage_and_e2e_latency_p50_p95",
]
PROTECTED_DEPENDENCY_ENV_DEFAULTS = {
    "RAG_DB_HOST": "",
    "RAG_DB_PORT": "5432",
    "RAG_DB_NAME": "",
    "RAG_DB_USER": "",
    "RAG_DB_PASSWORD": "",
    "RAG_DB_CONNECT_TIMEOUT_SECONDS": "5",
    "RAG_DB_STATEMENT_TIMEOUT_MS": "5000",
    "RAG_EMBEDDING_MODEL": "dragonkue/multilingual-e5-small-ko-v2",
    "RAG_EMBEDDING_DEVICE": "cpu",
    "RAG_EMBEDDING_BATCH_SIZE": "16",
    "WEB_SEARCH_PROVIDER": "none",
    "WEB_SEARCH_API_KEY": "",
    "WEB_SEARCH_BASE_URL": "",
    "WEB_SEARCH_TOP_K": "5",
    "WEB_SEARCH_TIMEOUT_SECONDS": "10",
}


class FactRubric(BaseModel):
    model_config = ConfigDict(extra="forbid")

    factGroups: list[list[str]] = Field(min_length=1)
    forbiddenTerms: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def non_blank_terms(self) -> "FactRubric":
        if any(not group or any(not term.strip() for term in group) for group in self.factGroups):
            raise ValueError("fact rubric groups and terms must be non-blank")
        if any(not term.strip() for term in self.forbiddenTerms):
            raise ValueError("forbidden fact terms must be non-blank")
        return self


class RefusalRubric(BaseModel):
    model_config = ConfigDict(extra="forbid")

    markers: list[str] = Field(min_length=1)
    forbiddenTerms: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def non_blank_terms(self) -> "RefusalRubric":
        if any(not term.strip() for term in self.markers + self.forbiddenTerms):
            raise ValueError("refusal rubric terms must be non-blank")
        return self


class ReportSelector(BaseModel):
    model_config = ConfigDict(extra="forbid")

    studyTitle: str = Field(min_length=1)
    contentAll: list[str] = Field(min_length=1)
    contentAny: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def non_blank_terms(self) -> "ReportSelector":
        if any(not term.strip() for term in self.contentAll + self.contentAny):
            raise ValueError("report selector terms must be non-blank")
        return self


class WebSelector(BaseModel):
    model_config = ConfigDict(extra="forbid")

    domainContains: str = Field(min_length=1)
    titleContains: list[str] = Field(default_factory=list)
    snippetContains: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def hostname_only(self) -> "WebSelector":
        expected = self.domainContains.strip().lower().rstrip(".")
        if not expected or urlsplit(f"//{expected}").hostname != expected:
            raise ValueError("web selector domain must be a hostname")
        if any(not term.strip() for term in self.titleContains + self.snippetContains):
            raise ValueError("web selector terms must be non-blank")
        self.domainContains = expected
        return self


class DogokE2ECase(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: str = Field(pattern=r"^[a-z0-9][a-z0-9-]*$")
    category: Literal["rag", "profile", "web", "insufficient", "priority"]
    question: str = Field(min_length=1)
    answerMode: Literal["fixed", "profile", "selected_web_snippet", "refusal"]
    scriptedAnswer: str | None = None
    factRubric: FactRubric | None = None
    refusalRubric: RefusalRubric | None = None
    reportSelector: ReportSelector | None = None
    webSelector: WebSelector | None = None
    profileField: Literal[
        "name", "household_count", "completion_year_month"
    ] | None = None

    @model_validator(mode="after")
    def coherent_case(self) -> "DogokE2ECase":
        present = {
            "scripted": self.scriptedAnswer is not None,
            "fact": self.factRubric is not None,
            "refusal": self.refusalRubric is not None,
            "report": self.reportSelector is not None,
            "web": self.webSelector is not None,
            "profile": self.profileField is not None,
        }
        expected = {
            "rag": ("fixed", {"scripted", "fact", "report"}),
            "priority": ("fixed", {"scripted", "fact", "report"}),
            "profile": ("profile", {"fact", "profile"}),
            "web": ("selected_web_snippet", {"web"}),
            "insufficient": ("refusal", {"scripted", "refusal"}),
        }
        expected_mode, expected_fields = expected[self.category]
        actual_fields = {name for name, value in present.items() if value}
        if self.answerMode != expected_mode or actual_fields != expected_fields:
            raise ValueError(
                f"{self.category} requires mode={expected_mode} and fields={sorted(expected_fields)}"
            )
        if self.scriptedAnswer is not None and not self.scriptedAnswer.strip():
            raise ValueError("scriptedAnswer must be non-blank")
        return self


class ApartmentIdentity(BaseModel):
    model_config = ConfigDict(extra="forbid")

    complexCode: str
    name: str


class DogokE2EDataset(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schemaVersion: Literal[3]
    datasetId: str
    description: str
    apartment: ApartmentIdentity
    cases: list[DogokE2ECase]

    @model_validator(mode="after")
    def frozen_shape(self) -> "DogokE2EDataset":
        counts = Counter(case.category for case in self.cases)
        if len(self.cases) != 20 or dict(counts) != EXPECTED_CATEGORY_COUNTS:
            raise ValueError("Dogok E2E dataset must have the frozen 20-case split")
        ids = [case.id for case in self.cases]
        if len(ids) != len(set(ids)):
            raise ValueError("dataset case ids must be unique")
        return self


class ExecutionLock(BaseModel):
    model_config = ConfigDict(extra="forbid")

    llmMode: Literal["scripted"]
    requireDeploymentDbReadConfirmation: Literal[True]
    requireLiveWebConfirmation: Literal[True]
    gmsClientCreation: Literal["forbidden"]


class DependenciesPolicy(BaseModel):
    model_config = ConfigDict(extra="forbid")

    databaseMode: Literal["read-only"]
    ragMode: Literal["real-pgvector"]
    embeddingMode: Literal["real-shared-embedder"]
    webMode: Literal["live-configured-provider"]
    llmMode: Literal["scripted-only"]


class RetrievalPolicy(BaseModel):
    model_config = ConfigDict(extra="forbid")

    topK: int = Field(gt=0)
    hitK: int = Field(gt=0)
    maxContextChars: int = Field(gt=0)

    @model_validator(mode="after")
    def hit_k_within_search(self) -> "RetrievalPolicy":
        if self.hitK > self.topK:
            raise ValueError("retrieval.hitK must be at most retrieval.topK")
        return self


class RunPolicy(BaseModel):
    model_config = ConfigDict(extra="forbid")

    repetitions: Literal[1]
    seed: int


class ArtifactPolicy(BaseModel):
    model_config = ConfigDict(extra="forbid")

    persistRawAnswer: Literal[False]
    persistPrompt: Literal[False]
    persistWebContent: Literal[False]
    persistConnectionData: Literal[False]


class DogokE2EPolicy(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schemaVersion: Literal[3]
    policyId: str
    profile: str
    datasetFile: str
    datasetSha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    apartment: ApartmentIdentity
    requiredReportStudyTitles: list[str]
    caseCount: Literal[20]
    categoryCounts: dict[str, int]
    executionLock: ExecutionLock
    dependencies: DependenciesPolicy
    retrieval: RetrievalPolicy
    run: RunPolicy
    artifacts: ArtifactPolicy
    metrics: list[str]


class PreflightError(RuntimeError):
    """Raised before case execution when deployment prerequisites are absent."""


class SelectorMiss(RuntimeError):
    """The actual retrieved evidence did not contain the frozen target."""


class WebAvailabilityError(RuntimeError):
    """The live provider completed but returned no evidence for a web case."""


@dataclass
class StageClock:
    values: dict[str, list[float]] = field(
        default_factory=lambda: defaultdict(list)
    )
    generation: int | None = None

    def record(
        self,
        stage: str,
        elapsed_ms: float,
        *,
        generation: int | None = None,
    ) -> None:
        if generation is not None and generation != self.generation:
            return
        self.values[stage].append(round(elapsed_ms, 3))

    def reset(self, generation: int | None = None) -> None:
        self.values.clear()
        self.generation = generation

    def snapshot(self) -> dict[str, float]:
        return {
            stage: round(sum(values), 3)
            for stage, values in self.values.items()
        }


class TimedCursor:
    def __init__(self, cursor: Any, timings: StageClock) -> None:
        self._cursor = cursor
        self._timings = timings

    def __enter__(self) -> "TimedCursor":
        self._cursor.__enter__()
        return self

    def __exit__(self, *args: Any) -> Any:
        return self._cursor.__exit__(*args)

    def execute(self, *args: Any, **kwargs: Any) -> Any:
        started = time.perf_counter()
        try:
            self._cursor.execute(*args, **kwargs)
            return self
        finally:
            self._timings.record("database", (time.perf_counter() - started) * 1000)

    def fetchone(self) -> Any:
        started = time.perf_counter()
        try:
            return self._cursor.fetchone()
        finally:
            self._timings.record("database", (time.perf_counter() - started) * 1000)

    def fetchall(self) -> Any:
        started = time.perf_counter()
        try:
            return self._cursor.fetchall()
        finally:
            self._timings.record("database", (time.perf_counter() - started) * 1000)

    def __getattr__(self, name: str) -> Any:
        return getattr(self._cursor, name)


class TimedReadOnlyConnection:
    def __init__(self, connection: Any, timings: StageClock) -> None:
        self._connection = connection
        self._timings = timings

    def cursor(self, *args: Any, **kwargs: Any) -> TimedCursor:
        return TimedCursor(self._connection.cursor(*args, **kwargs), self._timings)

    def close(self) -> None:
        self._connection.close()

    def __getattr__(self, name: str) -> Any:
        return getattr(self._connection, name)


class ReadOnlyConnectionFactory:
    """PostgreSQL factory that enforces read-only at connection creation."""

    def __init__(
        self,
        settings: RagSettings,
        timings: StageClock,
        *,
        connect_timeout_seconds: int = 5,
        statement_timeout_ms: int = 5000,
    ) -> None:
        if connect_timeout_seconds < 1 or statement_timeout_ms < 1:
            raise ValueError("database timeouts must be positive")
        self.settings = settings
        self.timings = timings
        self.connect_timeout_seconds = connect_timeout_seconds
        self.statement_timeout_ms = statement_timeout_ms

    def __call__(self) -> TimedReadOnlyConnection:
        import psycopg
        from pgvector.psycopg import register_vector

        started = time.perf_counter()
        connection = None
        try:
            connection = psycopg.connect(
                host=self.settings.host,
                port=self.settings.port,
                dbname=self.settings.database,
                user=self.settings.user,
                password=self.settings.password,
                connect_timeout=self.connect_timeout_seconds,
                options=(
                    "-c default_transaction_read_only=on "
                    f"-c statement_timeout={self.statement_timeout_ms}"
                ),
            )
            register_vector(connection)
            return TimedReadOnlyConnection(connection, self.timings)
        except Exception:
            if connection is not None:
                connection.close()
            raise
        finally:
            self.timings.record("database", (time.perf_counter() - started) * 1000)


class TimedEmbedder:
    def __init__(self, embedder: Any, timings: StageClock) -> None:
        self._embedder = embedder
        self._timings = timings
        self.dimension = getattr(embedder, "dimension", None)

    def encode_query(self, question: str) -> Any:
        started = time.perf_counter()
        try:
            return self._embedder.encode_query(question)
        finally:
            self._timings.record("embedding", (time.perf_counter() - started) * 1000)


@dataclass
class RuntimeState:
    case: DogokE2ECase | None = None
    report_ids_by_title: dict[str, int] = field(default_factory=dict)
    raw_hits: list[ReportChunkHit] = field(default_factory=list)
    prompt_hits: list[ReportChunkHit] = field(default_factory=list)
    profile: ApartmentProfile | None = None
    web_results: list[WebSearchResult] = field(default_factory=list)
    raw_payload: LlmAnswerPayload | None = None
    generation: int = 0

    def begin(self, case: DogokE2ECase) -> int:
        self.generation += 1
        self.case = case
        self.raw_hits = []
        self.prompt_hits = []
        self.profile = None
        self.web_results = []
        self.raw_payload = None
        return self.generation


class TimedLiveWebProvider(WebSearchProvider):
    def __init__(
        self,
        provider: WebSearchProvider,
        state: RuntimeState,
        timings: StageClock,
    ) -> None:
        self._provider = provider
        self._state = state
        self._timings = timings

    async def search(self, query: str, top_k: int) -> list[WebSearchResult]:
        generation = self._state.generation
        started = time.perf_counter()
        try:
            results = await self._provider.search(query, top_k)
            if generation == self._state.generation:
                self._state.web_results = list(results)
            return results
        finally:
            self._timings.record(
                "web",
                (time.perf_counter() - started) * 1000,
                generation=generation,
            )


class ScriptedPromptProvider(LlmProvider):
    """Deterministic provider fed by actual prompt inputs and evidence order."""

    def __init__(self, state: RuntimeState, timings: StageClock) -> None:
        self.state = state
        self.timings = timings

    async def complete_json(
        self, system_prompt: str, user_prompt: str
    ) -> dict[str, Any]:
        started = time.perf_counter()
        try:
            case = self.state.case
            if case is None:
                raise RuntimeError("scripted provider has no active case")
            self._validate_actual_prompt(case, system_prompt, user_prompt)
            answer, used_sources = self._script(case)
            payload = LlmAnswerPayload(
                answer=answer,
                usedSources=used_sources,
            )
            self.state.raw_payload = payload
            return payload.model_dump()
        finally:
            self.timings.record("scripted_llm", (time.perf_counter() - started) * 1000)

    def _validate_actual_prompt(
        self,
        case: DogokE2ECase,
        system_prompt: str,
        user_prompt: str,
    ) -> None:
        if system_prompt != SYSTEM_PROMPT:
            raise RuntimeError("system prompt differs from production prompt")
        expected_user_prompt = build_user_prompt(
            case.question,
            self.state.profile,
            self.state.prompt_hits,
            self.state.web_results,
        )
        if user_prompt != expected_user_prompt:
            raise RuntimeError(
                "user prompt question or numbered report/web evidence order differs from runtime state"
            )

    def _script(self, case: DogokE2ECase) -> tuple[str, list[int]]:
        if case.answerMode == "fixed" or case.answerMode == "refusal":
            assert case.scriptedAnswer is not None
            if case.reportSelector is None:
                return case.scriptedAnswer, []
            return case.scriptedAnswer, [self._report_number(case.reportSelector)]

        if case.answerMode == "profile":
            profile = self.state.profile
            if profile is None or case.profileField is None:
                raise SelectorMiss("required apartment profile was not retrieved")
            value = getattr(profile, case.profileField)
            if value is None:
                raise SelectorMiss("required apartment profile field is null")
            return f"단지 기본 정보 기준 {case.profileField} 값은 {value}입니다.", []

        if case.answerMode == "selected_web_snippet":
            assert case.webSelector is not None
            if not self.state.web_results:
                raise WebAvailabilityError("live web returned no results")
            web_index = self._web_index(case.webSelector)
            selected = self.state.web_results[web_index]
            excerpt = (selected.snippet or selected.title).strip()[:500]
            return f"웹 근거에 따르면 {excerpt}", [len(self.state.prompt_hits) + web_index + 1]

        raise RuntimeError(f"unsupported answer mode: {case.answerMode}")

    def _report_number(self, selector: ReportSelector) -> int:
        report_id = self.state.report_ids_by_title.get(selector.studyTitle)
        if report_id is None:
            raise SelectorMiss("preflight did not resolve the report title")
        for index, hit in enumerate(self.state.prompt_hits, start=1):
            content = _normalize(hit.content)
            all_ok = all(_normalize(term) in content for term in selector.contentAll)
            any_ok = not selector.contentAny or any(
                _normalize(term) in content for term in selector.contentAny
            )
            if hit.source_id == report_id and all_ok and any_ok:
                return index
        raise SelectorMiss("target report chunk was not present in actual RAG order")

    def _web_index(self, selector: WebSelector) -> int:
        for index, result in enumerate(self.state.web_results):
            domain_ok = _hostname_matches(result.domain, selector.domainContains)
            title_ok = all(
                _normalize(term) in _normalize(result.title)
                for term in selector.titleContains
            )
            snippet_ok = all(
                _normalize(term) in _normalize(result.snippet)
                for term in selector.snippetContains
            )
            if domain_ok and title_ok and snippet_ok:
                return index
        raise SelectorMiss("target live web evidence was not returned")


class TracingChatbotAnswerService(ChatbotAnswerService):
    """Capture actual retrieval output without replacing production logic."""

    def __init__(self, *args: Any, state: RuntimeState, timings: StageClock, **kwargs: Any) -> None:
        super().__init__(*args, **kwargs)
        self._state = state
        self._timings = timings

    def _retrieve(
        self, request: ChatbotAnswerRequest
    ) -> tuple[list[ReportChunkHit], ApartmentProfile | None]:
        started = time.perf_counter()
        try:
            hits, profile = super()._retrieve(request)
            self._state.raw_hits = list(hits)
            self._state.profile = profile
            return hits, profile
        finally:
            self._timings.record("rag", (time.perf_counter() - started) * 1000)

    async def _generate(
        self,
        question: str,
        profile: ApartmentProfile | None,
        hits: Sequence[ReportChunkHit],
        web_results: Sequence[WebSearchResult],
    ) -> LlmAnswerPayload:
        # These are the exact, post-truncation sequences numbered in the real
        # prompt.  Keeping them here prevents a raw-search rank from being
        # mistaken for the one-based evidence number seen by the provider.
        self._state.prompt_hits = list(hits)
        self._state.profile = profile
        self._state.web_results = list(web_results)
        return await super()._generate(question, profile, hits, web_results)


@dataclass(frozen=True)
class PreflightResult:
    apartment_id: int
    apartment_name: str
    report_ids_by_title: dict[str, int]


@dataclass(frozen=True)
class E2ESample:
    case_id: str
    category: str
    repetition: int
    outcome: str
    failure_stage: str | None
    answer_correct: bool | None
    evidence_correct: bool
    refusal_correct: bool | None
    mixed_citation_violation: bool | None
    retrieval_hit: bool | None
    reciprocal_rank: float | None
    raw_source_count: int | None
    final_mapping_correct: bool
    stage_ms: dict[str, float]
    e2e_ms: float


def load_configuration(
    dataset_path: Path = DATASET_PATH,
    policy_path: Path = POLICY_PATH,
) -> tuple[DogokE2EDataset, DogokE2EPolicy, str, str]:
    if dataset_path.resolve() != DATASET_PATH.resolve():
        raise ValueError("custom Dogok E2E datasets are forbidden")
    if policy_path.resolve() != POLICY_PATH.resolve():
        raise ValueError("custom Dogok E2E policies are forbidden")
    dataset_raw = dataset_path.read_bytes()
    policy_raw = policy_path.read_bytes()
    dataset_hash = hashlib.sha256(dataset_raw).hexdigest()
    policy_hash = hashlib.sha256(policy_raw).hexdigest()
    if policy_hash != EXPECTED_POLICY_SHA256:
        raise ValueError("policy hash differs from the frozen contract")
    dataset = DogokE2EDataset.model_validate_json(dataset_raw)
    policy = DogokE2EPolicy.model_validate_json(policy_raw)
    if dataset.datasetId != EXPECTED_DATASET_ID:
        raise ValueError("dataset id differs from the frozen target")
    if dataset.apartment.model_dump() != EXPECTED_APARTMENT:
        raise ValueError("dataset apartment differs from the frozen target")
    if policy.policyId != EXPECTED_POLICY_ID or policy.profile != EXPECTED_PROFILE:
        raise ValueError("policy identity/profile differs from the frozen target")
    if policy.datasetFile != EXPECTED_DATASET_FILE:
        raise ValueError("policy datasetFile differs from the frozen path")
    if policy.apartment.model_dump() != EXPECTED_APARTMENT:
        raise ValueError("policy apartment differs from the frozen target")
    if policy.requiredReportStudyTitles != EXPECTED_REPORT_TITLES:
        raise ValueError("policy report title targets differ from the frozen set")
    if policy.categoryCounts != EXPECTED_CATEGORY_COUNTS:
        raise ValueError("policy category split differs from the frozen target")
    if policy.metrics != EXPECTED_METRICS:
        raise ValueError("policy metrics differ from the frozen contract")
    if dataset_hash != policy.datasetSha256:
        raise ValueError("dataset hash does not match the frozen policy")
    if dataset.apartment != policy.apartment:
        raise ValueError("dataset and policy apartment identities differ")
    if policy.caseCount != len(dataset.cases):
        raise ValueError("policy case count differs from dataset")
    if policy.categoryCounts != dict(Counter(case.category for case in dataset.cases)):
        raise ValueError("policy category counts differ from dataset")
    return dataset, policy, dataset_hash, policy_hash


def validate_execution_lock(arguments: argparse.Namespace) -> None:
    """Validate both acknowledgements before loading env or dependencies."""
    if not arguments.e2e_dogok_rexle:
        raise ValueError("--e2e-dogok-rexle is required")
    if arguments.llm_mode != "scripted":
        raise ValueError("Dogok E2E permits only --llm-mode scripted")
    if not arguments.allow_deployment_db_read:
        raise ValueError("--allow-deployment-db-read confirmation is required")
    if not arguments.allow_live_web:
        raise ValueError("--allow-live-web confirmation is required")


def load_isolated_dependency_env(env_file: Path) -> None:
    """Load only approved DB/embedder/web keys from one explicit env file.

    Every protected key is first installed in ``os.environ`` with a controlled
    empty/default value.  The production settings helpers may subsequently
    inspect ``ai/.env*``, but python-dotenv cannot fill keys that already exist,
    so neither ambient values nor repository dotenv fallbacks can redirect the
    run.  GMS/model keys are intentionally ignored.
    """
    if not env_file.is_file():
        raise ValueError("env file does not exist")
    parsed = dotenv_values(env_file, interpolate=False)
    for name, default in PROTECTED_DEPENDENCY_ENV_DEFAULTS.items():
        os.environ[name] = default
    for name in PROTECTED_DEPENDENCY_ENV_DEFAULTS:
        value = parsed.get(name)
        if value is not None:
            os.environ[name] = value


def _positive_env_int(name: str) -> int:
    try:
        value = int(os.environ[name])
    except (KeyError, ValueError) as exc:
        raise ValueError(f"{name} must be a positive integer") from exc
    if value < 1:
        raise ValueError(f"{name} must be a positive integer")
    return value


def preflight(
    *,
    connection_factory: Callable[[], TimedReadOnlyConnection],
    embedder: TimedEmbedder,
    web_provider: WebSearchProvider,
    policy: DogokE2EPolicy,
) -> PreflightResult:
    """Read-only readiness checks.  This function never performs web search."""
    if isinstance(web_provider, NullWebSearchProvider):
        raise PreflightError("live web provider is not configured")
    configured = getattr(web_provider, "is_configured", None)
    if callable(configured) and not configured():
        raise PreflightError("live web provider configuration is incomplete")
    if embedder.dimension != EXPECTED_EMBEDDING_DIMENSION:
        raise PreflightError(
            "shared embedder dimension is not "
            f"{EXPECTED_EMBEDDING_DIMENSION}"
        )
    try:
        sentinel = [float(value) for value in embedder.encode_query("도곡 렉슬 임장 보고서")]
    except Exception as exc:
        raise PreflightError("sentinel query embedding failed") from exc
    if (
        len(sentinel) != EXPECTED_EMBEDDING_DIMENSION
        or not all(math.isfinite(value) for value in sentinel)
    ):
        raise PreflightError(
            "sentinel query embedding is not finite "
            f"vector({EXPECTED_EMBEDDING_DIMENSION})"
        )

    connection = None
    try:
        connection = connection_factory()
        with connection.cursor() as cursor:
            cursor.execute("SHOW transaction_read_only")
            row = cursor.fetchone()
            if row is None or str(row[0]).lower() not in {"on", "true"}:
                raise PreflightError("deployment DB connection is not read-only")

            cursor.execute(
                """
                SELECT id, name, household_count, completion_year_month
                FROM apartment
                WHERE complex_code = %(complex_code)s
                  AND regexp_replace(name, '\\s+', '', 'g') =
                      regexp_replace(%(name)s, '\\s+', '', 'g')
                """,
                {
                    "complex_code": policy.apartment.complexCode,
                    "name": policy.apartment.name,
                },
            )
            apartment_rows = cursor.fetchall()
            if len(apartment_rows) != 1:
                raise PreflightError("Dogok Rexle apartment identity is missing or ambiguous")
            apartment_id, apartment_name, household_count, completion = apartment_rows[0]
            if (
                household_count is None
                or completion is None
                or not str(completion).strip()
            ):
                raise PreflightError("required apartment profile fields are null or blank")

            cursor.execute(
                """
                SELECT r.id, s.title, r.status,
                       count(d.id) FILTER (WHERE d.embedding IS NOT NULL) AS embedded_chunks
                FROM report r
                JOIN study s ON s.id = r.study_id
                LEFT JOIN apartment_rag_document d
                  ON d.apartment_id = r.apartment_id
                 AND d.source_type = 'REPORT'
                 AND d.source_id = r.id
                WHERE r.apartment_id = %(apartment_id)s
                  AND s.title = ANY(%(titles)s::varchar[])
                  AND s.deleted_at IS NULL
                  AND s.status <> 'CANCELED'
                GROUP BY r.id, s.title, r.status
                """,
                {
                    "apartment_id": apartment_id,
                    "titles": policy.requiredReportStudyTitles,
                },
            )
            report_rows = cursor.fetchall()
    except PreflightError:
        raise
    except Exception as exc:
        raise PreflightError("deployment DB read-only preflight query failed") from exc
    finally:
        if connection is not None:
            connection.close()

    if len(report_rows) != len(policy.requiredReportStudyTitles):
        raise PreflightError("the three required demo reports were not resolved")
    report_ids: dict[str, int] = {}
    for report_id, title, status, embedded_chunks in report_rows:
        if status != "DONE" or int(embedded_chunks) < 1:
            raise PreflightError("a required report is not DONE with embedded chunks")
        if title in report_ids:
            raise PreflightError("a required report title is ambiguous")
        report_ids[str(title)] = int(report_id)
    if set(report_ids) != set(policy.requiredReportStudyTitles):
        raise PreflightError("resolved report title set differs from policy")
    return PreflightResult(
        apartment_id=int(apartment_id),
        apartment_name=str(apartment_name),
        report_ids_by_title=report_ids,
    )


class DogokE2ERunner:
    def __init__(
        self,
        *,
        dataset: DogokE2EDataset,
        policy: DogokE2EPolicy,
        service: ChatbotAnswerService,
        state: RuntimeState,
        timings: StageClock,
        apartment_id: int,
        apartment_name: str,
    ) -> None:
        self.dataset = dataset
        self.policy = policy
        self.service = service
        self.state = state
        self.timings = timings
        self.apartment_id = apartment_id
        self.apartment_name = apartment_name

    async def run(self) -> list[E2ESample]:
        results: list[E2ESample] = []
        for repetition in range(1, self.policy.run.repetitions + 1):
            for case in self.dataset.cases:
                results.append(await self._run_case(case, repetition))
        return results

    async def _run_case(self, case: DogokE2ECase, repetition: int) -> E2ESample:
        generation = self.state.begin(case)
        self.timings.reset(generation)
        started = time.perf_counter()
        try:
            response = await self.service.answer(
                ChatbotAnswerRequest(
                    apartmentId=self.apartment_id,
                    apartmentName=self.apartment_name,
                    question=case.question,
                )
            )
            raw = self.state.raw_payload
            if raw is None:
                raise RuntimeError("service returned without a raw scripted payload")
            required_groups, forbidden_terms = _fact_contract(case, self.state)
            score = score_v1_grounding(
                raw,
                required_answer_groups=required_groups,
                forbidden_answer_terms=forbidden_terms,
                acceptable_source_sets=[_expected_source_set(case, self.state)],
                report_count=len(self.state.prompt_hits),
                web_count=len(self.state.web_results),
            )
            answer_ok = score.answer_correct
            evidence_ok = score.evidence_correct
            mixed_violation = score.mixed_citation_violation
            refusal_ok = _score_refusal(case, raw)
            if refusal_ok is not None:
                answer_ok = refusal_ok
            rank = _raw_report_rank(case, self.state, self.policy.retrieval.hitK)
            retrieval_hit = None if case.reportSelector is None else rank is not None
            reciprocal_rank = None if case.reportSelector is None else (
                0.0 if rank is None else 1.0 / rank
            )
            final_ok = _score_final_mapping(case, self.state, raw, response)
            passed = (
                (answer_ok is not False)
                and evidence_ok
                and (refusal_ok is not False)
                and not bool(mixed_violation)
                and final_ok
                and (retrieval_hit is not False)
            )
            return E2ESample(
                case_id=case.id,
                category=case.category,
                repetition=repetition,
                outcome="passed" if passed else "failed",
                failure_stage=None if passed else "scoring",
                answer_correct=answer_ok,
                evidence_correct=evidence_ok,
                refusal_correct=refusal_ok,
                mixed_citation_violation=mixed_violation,
                retrieval_hit=retrieval_hit,
                reciprocal_rank=reciprocal_rank,
                raw_source_count=len(raw.usedSources),
                final_mapping_correct=final_ok,
                stage_ms=self.timings.snapshot(),
                e2e_ms=round((time.perf_counter() - started) * 1000, 3),
            )
        except Exception as exc:
            return E2ESample(
                case_id=case.id,
                category=case.category,
                repetition=repetition,
                outcome="error",
                failure_stage=_failure_stage(exc),
                answer_correct=None,
                evidence_correct=False,
                refusal_correct=None,
                mixed_citation_violation=None,
                retrieval_hit=False if case.reportSelector is not None else None,
                reciprocal_rank=0.0 if case.reportSelector is not None else None,
                raw_source_count=None,
                final_mapping_correct=False,
                stage_ms=self.timings.snapshot(),
                e2e_ms=round((time.perf_counter() - started) * 1000, 3),
            )


@dataclass(frozen=True)
class V1GroundingScore:
    answer_correct: bool
    evidence_correct: bool
    mixed_citation_violation: bool | None


def score_v1_grounding(
    payload: LlmAnswerPayload,
    *,
    required_answer_groups: Sequence[Sequence[str]],
    forbidden_answer_terms: Sequence[str],
    acceptable_source_sets: Sequence[Sequence[int]],
    report_count: int,
    web_count: int,
) -> V1GroundingScore:
    """Independent v1-compatible NFKC fact and exact raw-source scorer.

    Required groups use all-groups/any-term semantics, forbidden terms must all
    be absent, and a raw source list passes only when it is duplicate-free and
    its set exactly equals one acceptable set.  This intentionally avoids
    importing the GMS-oriented v1 benchmark module.
    """
    normalized_answer = _normalize(payload.answer)
    answer_correct = all(
        any(_normalize(term) in normalized_answer for term in group)
        for group in required_answer_groups
    ) and all(
        _normalize(term) not in normalized_answer
        for term in forbidden_answer_terms
    )
    selected = payload.usedSources
    selected_set = frozenset(selected)
    acceptable = {frozenset(source_set) for source_set in acceptable_source_sets}
    evidence_correct = (
        len(selected) == len(selected_set) and selected_set in acceptable
    )

    mixed_violation = None
    if report_count and web_count:
        maximum = report_count + web_count
        valid = {number for number in selected if 1 <= number <= maximum}
        mixed_violation = (
            any(number <= report_count for number in valid)
            and any(number > report_count for number in valid)
        )
    return V1GroundingScore(
        answer_correct=answer_correct,
        evidence_correct=evidence_correct,
        mixed_citation_violation=mixed_violation,
    )


def _fact_contract(
    case: DogokE2ECase,
    state: RuntimeState,
) -> tuple[list[list[str]], list[str]]:
    if case.factRubric is not None:
        profile_value = ""
        if state.profile is not None and case.profileField is not None:
            value = getattr(state.profile, case.profileField)
            profile_value = "" if value is None else str(value)
        return (
            [
                [term.replace("{profileValue}", profile_value) for term in group]
                for group in case.factRubric.factGroups
            ],
            list(case.factRubric.forbiddenTerms),
        )
    if case.webSelector is not None:
        selected = state.web_results[_expected_web_index(case.webSelector, state)]
        return [[(selected.snippet or selected.title).strip()[:500]]], []
    assert case.refusalRubric is not None
    return [list(case.refusalRubric.markers)], list(case.refusalRubric.forbiddenTerms)


def _score_refusal(case: DogokE2ECase, raw: LlmAnswerPayload) -> bool | None:
    if case.refusalRubric is None:
        return None
    answer = _normalize(raw.answer)
    marker_ok = any(_normalize(marker) in answer for marker in case.refusalRubric.markers)
    forbidden_ok = all(
        _normalize(term) not in answer for term in case.refusalRubric.forbiddenTerms
    )
    return marker_ok and forbidden_ok and raw.usedSources == []


def _matching_report_rank(
    selector: ReportSelector,
    state: RuntimeState,
    hits: Sequence[ReportChunkHit],
) -> int | None:
    report_id = state.report_ids_by_title.get(selector.studyTitle)
    for index, hit in enumerate(hits, start=1):
        content = _normalize(hit.content)
        if (
            hit.source_id == report_id
            and all(_normalize(term) in content for term in selector.contentAll)
            and (
                not selector.contentAny
                or any(_normalize(term) in content for term in selector.contentAny)
            )
        ):
            return index
    return None


def _expected_prompt_report_number(
    case: DogokE2ECase, state: RuntimeState
) -> int | None:
    selector = case.reportSelector
    if selector is None:
        return None
    return _matching_report_rank(selector, state, state.prompt_hits)


def _raw_report_rank(
    case: DogokE2ECase,
    state: RuntimeState,
    hit_k: int,
) -> int | None:
    selector = case.reportSelector
    if selector is None:
        return None
    return _matching_report_rank(selector, state, state.raw_hits[:hit_k])


def _expected_source_set(
    case: DogokE2ECase, state: RuntimeState
) -> list[int]:
    """Expected raw one-based selector, independent of the provider payload."""
    if case.reportSelector is not None:
        rank = _expected_prompt_report_number(case, state)
        if rank is None:
            raise SelectorMiss("expected report evidence was not retrieved")
        return [rank]
    if case.webSelector is not None:
        index = _expected_web_index(case.webSelector, state)
        return [len(state.prompt_hits) + index + 1]
    return []


def _expected_web_index(selector: WebSelector, state: RuntimeState) -> int:
    if not state.web_results:
        raise WebAvailabilityError("live web returned no results")
    for index, result in enumerate(state.web_results):
        if (
            _hostname_matches(result.url, selector.domainContains)
            and all(
                _normalize(term) in _normalize(result.title)
                for term in selector.titleContains
            )
            and all(
                _normalize(term) in _normalize(result.snippet)
                for term in selector.snippetContains
            )
        ):
            return index
    raise SelectorMiss("expected live web evidence was not retrieved")


def _selected_web_result(state: RuntimeState, raw: LlmAnswerPayload) -> WebSearchResult:
    if len(raw.usedSources) != 1:
        raise SelectorMiss("web case did not select exactly one source")
    index = raw.usedSources[0] - len(state.prompt_hits) - 1
    if index < 0 or index >= len(state.web_results):
        raise SelectorMiss("selected web source number is out of range")
    return state.web_results[index]


def _score_final_mapping(
    case: DogokE2ECase,
    state: RuntimeState,
    raw: LlmAnswerPayload,
    response: ChatbotAnswerResponse,
) -> bool:
    if case.reportSelector is not None:
        expected_id = state.report_ids_by_title.get(case.reportSelector.studyTitle)
        return (
            response.basisType == "REPORT"
            and len(response.sources) == 1
            and response.sources[0].sourceType == "REPORT"
            and response.sources[0].sourceId == expected_id
            and response.sources[0].reportId == expected_id
            and response.sources[0].url is None
        )
    if case.webSelector is not None:
        selected = _selected_web_result(state, raw)
        return (
            response.basisType == "WEB"
            and len(response.sources) == 1
            and response.sources[0].sourceType == "WEB"
            and response.sources[0].sourceId is None
            and response.sources[0].reportId is None
            and response.sources[0].url == selected.url
        )
    return (
        response.basisType == "NONE"
        and response.basisLabel is None
        and len(response.sources) == 0
    )


def _failure_stage(exc: Exception) -> str:
    if isinstance(exc, WebAvailabilityError):
        return "web_availability"
    if isinstance(exc, SelectorMiss):
        return "evidence_selection"
    if isinstance(exc, ChatbotAnswerError):
        return {
            "EMBEDDING_FAILED": "embedding",
            "RAG_DB_FAILED": "database_rag",
            "PROVIDER_FAILED": "scripted_llm",
            "SCHEMA_VALIDATION_FAILED": "scripted_llm_schema",
        }.get(exc.code, "service")
    return "service"


def build_summary(
    *,
    dataset: DogokE2EDataset,
    policy: DogokE2EPolicy,
    dataset_hash: str,
    policy_hash: str,
    results: Sequence[E2ESample],
    started_at: datetime,
    finished_at: datetime,
) -> dict[str, Any]:
    retrieval = [item for item in results if item.retrieval_hit is not None]
    latency_by_stage: dict[str, list[float]] = defaultdict(list)
    for item in results:
        latency_by_stage["e2e"].append(item.e2e_ms)
        for stage, elapsed in item.stage_ms.items():
            latency_by_stage[stage].append(elapsed)
    return {
        "schemaVersion": 3,
        "profile": policy.profile,
        "policyId": policy.policyId,
        "datasetId": dataset.datasetId,
        "datasetSha256": dataset_hash,
        "policySha256": policy_hash,
        "seed": policy.run.seed,
        "apartment": {
            "complexCode": policy.apartment.complexCode,
            "name": policy.apartment.name,
        },
        "startedAt": started_at.isoformat(),
        "finishedAt": finished_at.isoformat(),
        "llmMode": "scripted",
        "modelQualityMeasured": False,
        "warning": NOT_MODEL_QUALITY,
        "caseCount": len(results),
        "passed": sum(item.outcome == "passed" for item in results),
        "failed": sum(item.outcome == "failed" for item in results),
        "errors": sum(item.outcome == "error" for item in results),
        "retrieval": {
            "k": policy.retrieval.hitK,
            "hitAtK": _ratio_metric(
                [bool(item.retrieval_hit) for item in retrieval]
            ),
            "mrr": _mean_metric(
                [item.reciprocal_rank or 0.0 for item in retrieval]
            ),
        },
        "scoring": {
            "answerFactPassRate": _ratio_metric(
                # Technical failures have answer_correct=None and deliberately
                # remain denominator failures rather than disappearing.
                [bool(item.answer_correct) for item in results]
            ),
            "rawExactSourcePassRate": _ratio_metric(
                [item.evidence_correct for item in results]
            ),
            "finalCitationMappingPassRate": _ratio_metric(
                [item.final_mapping_correct for item in results]
            ),
        },
        "failureStageCounts": dict(
            Counter(item.failure_stage for item in results if item.failure_stage)
        ),
        "latencyMs": {
            stage: {
                "n": len(values),
                "p50": _percentile(values, 0.50),
                "p95": _percentile(values, 0.95),
            }
            for stage, values in sorted(latency_by_stage.items())
        },
    }


def write_results(
    output_dir: Path,
    summary: Mapping[str, Any],
    results: Sequence[E2ESample],
) -> Path:
    run_stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    run_dir = output_dir / f"dogok-rexle-e2e-v3-{run_stamp}"
    run_dir.mkdir(parents=True, exist_ok=False)
    _atomic_write(
        run_dir / "summary.json",
        json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
    )
    samples = "".join(
        json.dumps(asdict(item), ensure_ascii=False, sort_keys=True) + "\n"
        for item in results
    )
    _atomic_write(run_dir / "samples.jsonl", samples)
    _atomic_write(run_dir / "README.md", _render_readme(summary))
    return run_dir


def _render_readme(summary: Mapping[str, Any]) -> str:
    retrieval = summary["retrieval"]
    scoring = summary["scoring"]
    return (
        "# Dogok Rexle chatbot hybrid E2E v3\n\n"
        f"> {NOT_MODEL_QUALITY}\n\n"
        f"- Cases: {summary['caseCount']}\n"
        f"- Passed / failed / errors: {summary['passed']} / {summary['failed']} / {summary['errors']}\n"
        f"- Retrieval Hit@{retrieval['k']}: {retrieval['hitAtK']['rate']:.4f}\n"
        f"- Retrieval MRR: {retrieval['mrr']['rate']:.4f}\n"
        f"- Answer fact pass rate: {scoring['answerFactPassRate']['rate']:.4f}\n"
        f"- Raw exact-source pass rate: {scoring['rawExactSourcePassRate']['rate']:.4f}\n"
        f"- Final citation mapping pass rate: {scoring['finalCitationMappingPassRate']['rate']:.4f}\n"
        "\nRaw answers, prompts, web content, endpoints, credentials, and DB connection data are not persisted.\n"
    )


def _atomic_write(path: Path, content: str) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(content, encoding="utf-8", newline="\n")
    temporary.replace(path)


def _ratio_metric(values: Sequence[bool]) -> dict[str, int | float]:
    numerator = sum(bool(value) for value in values)
    denominator = len(values)
    return {
        "numerator": numerator,
        "denominator": denominator,
        "rate": 0.0 if denominator == 0 else numerator / denominator,
    }


def _mean_metric(values: Sequence[float]) -> dict[str, int | float]:
    numerator = float(sum(values))
    denominator = len(values)
    return {
        "numerator": numerator,
        "denominator": denominator,
        "rate": 0.0 if denominator == 0 else numerator / denominator,
    }


def _percentile(values: Sequence[float], quantile: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    position = (len(ordered) - 1) * quantile
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return round(ordered[lower], 3)
    weight = position - lower
    return round(ordered[lower] * (1 - weight) + ordered[upper] * weight, 3)


def _normalize(value: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", value).casefold().split())


def _hostname_matches(actual: str, expected: str) -> bool:
    candidate = actual.strip()
    parsed = urlsplit(candidate if "://" in candidate else f"//{candidate}")
    actual_hostname = (parsed.hostname or "").lower().rstrip(".")
    expected_hostname = expected.strip().lower().rstrip(".")
    return actual_hostname == expected_hostname or actual_hostname.endswith(
        "." + expected_hostname
    )


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Dogok Rexle deployment DB/RAG/live-web hybrid E2E (scripted LLM only)"
    )
    parser.add_argument("--e2e-dogok-rexle", action="store_true")
    parser.add_argument("--llm-mode", choices=("scripted",), required=True)
    parser.add_argument("--allow-deployment-db-read", action="store_true")
    parser.add_argument("--allow-live-web", action="store_true")
    parser.add_argument("--env-file", type=Path, required=True)
    parser.add_argument("--dataset", type=Path, default=DATASET_PATH)
    parser.add_argument("--policy", type=Path, default=POLICY_PATH)
    parser.add_argument("--repetitions", type=int, default=1)
    parser.add_argument("--output-dir", type=Path, default=Path("../output/model-eval"))
    return parser


async def _run_cli(arguments: argparse.Namespace) -> Path:
    validate_execution_lock(arguments)
    if arguments.repetitions != 1:
        raise ValueError("Dogok E2E repetitions are frozen at 1")
    dataset, policy, dataset_hash, policy_hash = load_configuration(
        arguments.dataset, arguments.policy
    )
    if arguments.repetitions != policy.run.repetitions:
        raise ValueError("CLI repetitions differ from policy")
    load_isolated_dependency_env(arguments.env_file)

    timings = StageClock()
    rag_settings = RagSettings.from_env()
    chatbot_settings = ChatbotSettings(
        top_k=policy.retrieval.topK,
        max_context_chars=policy.retrieval.maxContextChars,
    )
    web_settings = WebSearchSettings.from_env()
    raw_web_provider = create_web_search_provider(web_settings)
    embedder = TimedEmbedder(get_shared_embedder(rag_settings), timings)
    connection_factory = ReadOnlyConnectionFactory(
        rag_settings,
        timings,
        connect_timeout_seconds=_positive_env_int(
            "RAG_DB_CONNECT_TIMEOUT_SECONDS"
        ),
        statement_timeout_ms=_positive_env_int("RAG_DB_STATEMENT_TIMEOUT_MS"),
    )

    # Preflight completes before constructing the case runner.  It creates the
    # configured web adapter but never calls search().
    readiness = preflight(
        connection_factory=connection_factory,
        embedder=embedder,
        web_provider=raw_web_provider,
        policy=policy,
    )
    timings.reset()

    state = RuntimeState(report_ids_by_title=readiness.report_ids_by_title)
    scripted_provider = ScriptedPromptProvider(state, timings)
    timed_web_provider = TimedLiveWebProvider(raw_web_provider, state, timings)
    service = TracingChatbotAnswerService(
        provider=scripted_provider,
        embedder=embedder,
        connection_factory=connection_factory,
        settings=chatbot_settings,
        web_search_provider=timed_web_provider,
        web_settings=web_settings,
        state=state,
        timings=timings,
    )
    runner = DogokE2ERunner(
        dataset=dataset,
        policy=policy,
        service=service,
        state=state,
        timings=timings,
        apartment_id=readiness.apartment_id,
        apartment_name=readiness.apartment_name,
    )
    started_at = datetime.now(timezone.utc)
    results = await runner.run()
    finished_at = datetime.now(timezone.utc)
    summary = build_summary(
        dataset=dataset,
        policy=policy,
        dataset_hash=dataset_hash,
        policy_hash=policy_hash,
        results=results,
        started_at=started_at,
        finished_at=finished_at,
    )
    return write_results(arguments.output_dir, summary, results)


def main(argv: Sequence[str] | None = None) -> int:
    arguments = _parser().parse_args(list(sys.argv[1:] if argv is None else argv))
    try:
        run_dir = asyncio.run(_run_cli(arguments))
    except PreflightError as exc:
        # Preflight messages are static, secret-free diagnostics. Dependency
        # exception text remains chained internally and is never printed.
        print(f"Dogok Rexle E2E preflight failed: {exc}", file=sys.stderr)
        return 1
    except Exception as exc:
        print(f"Dogok Rexle E2E failed: {type(exc).__name__}", file=sys.stderr)
        return 1
    print(f"Dogok Rexle E2E complete: {run_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
