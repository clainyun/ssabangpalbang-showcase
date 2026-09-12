"""Unified report/web-grounded chatbot answer orchestration (AI-009)."""

from __future__ import annotations

import asyncio
import logging
from collections.abc import Sequence
from datetime import datetime, timedelta, timezone
from typing import Any

import jsonschema
from pydantic import ValidationError

from app.prompts.chatbot_prompt import (
    SEARCH_QUERY_SYSTEM_PROMPT,
    SYSTEM_PROMPT,
    build_search_query,
    build_search_query_prompt,
    build_user_prompt,
)
from app.providers.llm_provider import LlmProvider, LlmProviderError
from app.providers.web_search_provider import (
    WebSearchProvider,
    WebSearchResult,
    create_web_search_provider,
    strip_html,
)
from app.rag.search import (
    ApartmentProfile,
    ReportChunkHit,
    fetch_apartment_profile,
    report_documents_exist,
    search_report_documents,
    truncate_to_char_limit,
)
from app.rag.settings_chatbot import ChatbotSettings, WebSearchSettings
from app.schemas.chatbot import (
    INSUFFICIENT_EVIDENCE_ANSWER,
    NONE_BASIS_TYPE,
    REPORT_BASIS_LABEL,
    REPORT_BASIS_TYPE,
    WEB_BASIS_LABEL,
    WEB_BASIS_TYPE,
    ChatbotAnswerRequest,
    ChatbotAnswerResponse,
    ChatbotAnswerSource,
    LlmAnswerPayload,
    SearchQueryPayload,
    llm_answer_json_schema,
)


logger = logging.getLogger(__name__)

_WEB_FILTER_CANDIDATE_LIMIT = 50


class ChatbotAnswerError(Exception):
    """Technical failure that Spring should map to fallback."""

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


class ChatbotAnswerService:
    def __init__(
        self,
        provider: LlmProvider,
        embedder: Any,
        connection_factory: Any,
        settings: ChatbotSettings,
        web_search_provider: WebSearchProvider | None = None,
        web_settings: WebSearchSettings | None = None,
        rewrite_provider: LlmProvider | None = None,
    ) -> None:
        self.provider = provider
        self.embedder = embedder
        self.connection_factory = connection_factory
        self.settings = settings
        self.web_settings = web_settings or WebSearchSettings.from_env()
        self.web_search_provider = (
            web_search_provider
            or create_web_search_provider(self.web_settings)
        )
        self.rewrite_provider = rewrite_provider

    async def answer(
        self, request: ChatbotAnswerRequest
    ) -> ChatbotAnswerResponse:
        profile, has_report_documents = await asyncio.to_thread(
            self._fetch_profile, request
        )
        if not self.settings.lazy_web_enabled:
            return await self._answer_eager(request, profile)
        return await self._answer_lazy(
            request, profile, has_report_documents
        )

    async def _answer_eager(
        self,
        request: ChatbotAnswerRequest,
        profile: ApartmentProfile | None,
    ) -> ChatbotAnswerResponse:
        fallback_query = build_search_query(
            profile.name if profile is not None else request.apartmentName,
            profile.district_name if profile is not None else None,
            profile.dong_name if profile is not None else None,
            request.question,
        )
        report_task = asyncio.to_thread(self._retrieve, request)
        web_task = self._rewrite_search_and_filter(
            request, profile, fallback_query
        )
        hits, web_results = await asyncio.gather(report_task, web_task)

        top_similarity = hits[0].similarity if hits else None
        used_hits = truncate_to_char_limit(
            hits, self.settings.max_context_chars
        )
        used_web_results = web_results[: self.web_settings.top_k]

        if profile is None and not used_hits and not used_web_results:
            return self._insufficient_evidence(top_similarity)

        payload = await self._generate(
            request.question, profile, used_hits, used_web_results
        )
        return self._build_response(
            request,
            profile,
            used_hits,
            used_web_results,
            payload,
            top_similarity,
        )

    async def _answer_lazy(
        self,
        request: ChatbotAnswerRequest,
        profile: ApartmentProfile | None,
        has_report_documents: bool,
    ) -> ChatbotAnswerResponse:
        fallback_query = build_search_query(
            profile.name if profile is not None else request.apartmentName,
            profile.district_name if profile is not None else None,
            profile.dong_name if profile is not None else None,
            request.question,
        )
        if not has_report_documents:
            web_results = await self._rewrite_search_and_filter(
                request, profile, fallback_query
            )
            used_web_results = web_results[: self.web_settings.top_k]
            if profile is None and not used_web_results:
                return self._insufficient_evidence(None)
            payload = await self._generate(
                request.question, profile, [], used_web_results
            )
            return self._build_response(
                request,
                profile,
                [],
                used_web_results,
                payload,
                None,
            )

        hits = await asyncio.to_thread(self._retrieve, request)
        top_similarity = hits[0].similarity if hits else None
        used_hits = truncate_to_char_limit(
            hits, self.settings.max_context_chars
        )
        first_payload = await self._generate(
            request.question, profile, used_hits, []
        )
        first_response = self._build_response(
            request,
            profile,
            used_hits,
            [],
            first_payload,
            top_similarity,
        )
        if _valid_unique_numbers(first_payload.usedSources, len(used_hits)):
            return first_response
        used_profile = _valid_unique_profile_fields(
            first_payload.usedProfile, profile
        )
        if used_profile:
            logger.info(
                "Chatbot answer used profile fields: %s",
                used_profile,
            )
            return first_response

        web_results = await self._rewrite_search_and_filter(
            request, profile, fallback_query
        )
        used_web_results = web_results[: self.web_settings.top_k]
        if not used_web_results:
            return first_response

        try:
            second_payload = await self._generate(
                request.question,
                profile,
                used_hits,
                used_web_results,
            )
        except ChatbotAnswerError as exc:
            logger.warning(
                "Chatbot second answer generation failed: %s", exc.code
            )
            return first_response

        selected_numbers = _valid_unique_numbers(
            second_payload.usedSources,
            len(used_hits) + len(used_web_results),
        )
        if not any(number > len(used_hits) for number in selected_numbers):
            return first_response
        return self._build_response(
            request,
            profile,
            used_hits,
            used_web_results,
            second_payload,
            top_similarity,
        )

    async def _rewrite_search_and_filter(
        self,
        request: ChatbotAnswerRequest,
        profile: ApartmentProfile | None,
        fallback_query: str,
    ) -> list[WebSearchResult]:
        query = await self._rewrite_query(request, profile, fallback_query)
        logger.info("Chatbot web search query: %s", query)
        filter_enabled = (
            self.rewrite_provider is not None
            and self.settings.web_result_filter_enabled
        )
        results = await self._search_web(query)
        recent_results = _filter_web_results_by_age(
            results,
            self.web_settings.max_age_days,
        )
        logger.info(
            "Chatbot stale web results filtered: %d",
            len(results) - len(recent_results),
        )
        if not filter_enabled:
            return recent_results[: self.web_settings.top_k]

        apartment_name = (
            profile.name if profile is not None else request.apartmentName
        )
        filtered = _filter_web_results(
            recent_results,
            apartment_name,
            profile.district_name if profile is not None else None,
            profile.dong_name if profile is not None else None,
        )
        logger.info(
            "Chatbot web results filtered: %d",
            len(recent_results) - len(filtered),
        )
        return filtered[: self.web_settings.top_k]

    async def _rewrite_query(
        self,
        request: ChatbotAnswerRequest,
        profile: ApartmentProfile | None,
        fallback_query: str,
    ) -> str:
        if (
            not self.settings.search_rewrite_enabled
            or self.rewrite_provider is None
        ):
            return fallback_query

        apartment_name = (
            profile.name if profile is not None else request.apartmentName
        )
        prompt = build_search_query_prompt(
            request.question,
            apartment_name,
            profile.district_name if profile is not None else None,
            profile.dong_name if profile is not None else None,
        )
        try:
            raw = await self.rewrite_provider.complete_json(
                SEARCH_QUERY_SYSTEM_PROMPT, prompt
            )
            return SearchQueryPayload.model_validate(raw).query
        except (LlmProviderError, ValidationError) as exc:
            logger.warning(
                "Chatbot search query rewrite failed: %s",
                type(exc).__name__,
            )
            return fallback_query

    async def _search_web(
        self, query: str, top_k: int | None = None
    ) -> list[WebSearchResult]:
        try:
            return await self.web_search_provider.search(
                query,
                _WEB_FILTER_CANDIDATE_LIMIT if top_k is None else top_k,
            )
        except Exception as exc:
            logger.warning("Web search failed: %s", type(exc).__name__)
            return []

    def _fetch_profile(
        self, request: ChatbotAnswerRequest
    ) -> tuple[ApartmentProfile | None, bool]:
        connection = None
        try:
            connection = self.connection_factory()
            profile = fetch_apartment_profile(
                connection, request.apartmentId
            )
            has_report_documents = report_documents_exist(
                connection, request.apartmentId
            )
            logger.info(
                "Chatbot report documents exist: %s",
                has_report_documents,
            )
            return profile, has_report_documents
        except ChatbotAnswerError:
            raise
        except Exception as exc:
            raise ChatbotAnswerError("RAG_DB_FAILED", str(exc)) from exc
        finally:
            if connection is not None:
                connection.close()

    def _retrieve(self, request: ChatbotAnswerRequest) -> list[ReportChunkHit]:
        try:
            query_vector = self.embedder.encode_query(request.question)
        except Exception as exc:
            raise ChatbotAnswerError("EMBEDDING_FAILED", str(exc)) from exc

        connection = None
        try:
            connection = self.connection_factory()
            hits = search_report_documents(
                connection,
                request.apartmentId,
                query_vector,
                self.settings.top_k,
            )
            return hits
        except ChatbotAnswerError:
            raise
        except Exception as exc:
            raise ChatbotAnswerError("RAG_DB_FAILED", str(exc)) from exc
        finally:
            if connection is not None:
                connection.close()

    async def _generate(
        self,
        question: str,
        profile: ApartmentProfile | None,
        hits: Sequence[ReportChunkHit],
        web_results: Sequence[WebSearchResult],
    ) -> LlmAnswerPayload:
        user_prompt = build_user_prompt(
            question, profile, hits, web_results
        )
        try:
            raw = await self.provider.complete_json(
                SYSTEM_PROMPT, user_prompt
            )
        except LlmProviderError as exc:
            raise ChatbotAnswerError("PROVIDER_FAILED", str(exc)) from exc
        return self._validate_payload(raw)

    def _validate_payload(self, raw: dict[str, Any]) -> LlmAnswerPayload:
        try:
            jsonschema.validate(instance=raw, schema=llm_answer_json_schema())
        except jsonschema.ValidationError as exc:
            raise ChatbotAnswerError(
                "SCHEMA_VALIDATION_FAILED",
                f"JSON Schema validation failed: {exc.message}",
            ) from exc

        try:
            return LlmAnswerPayload.model_validate(raw)
        except ValidationError as exc:
            raise ChatbotAnswerError(
                "SCHEMA_VALIDATION_FAILED",
                f"Pydantic validation failed: {exc}",
            ) from exc

    def _build_response(
        self,
        request: ChatbotAnswerRequest,
        profile: ApartmentProfile | None,
        hits: Sequence[ReportChunkHit],
        web_results: Sequence[WebSearchResult],
        payload: LlmAnswerPayload,
        top_similarity: float | None,
    ) -> ChatbotAnswerResponse:
        selected_numbers = _valid_unique_numbers(
            payload.usedSources, len(hits) + len(web_results)
        )
        report_hits = [
            hits[number - 1]
            for number in selected_numbers
            if number <= len(hits)
        ]
        web_sources = [
            web_results[number - len(hits) - 1]
            for number in selected_numbers
            if number > len(hits)
        ]

        if report_hits:
            if web_sources:
                logger.warning(
                    "혼합 인용을 REPORT로 정규화했습니다. report=%d web=%d",
                    len(report_hits),
                    len(web_sources),
                )
            title = self._report_title(request, profile)
            return ChatbotAnswerResponse(
                basisType=REPORT_BASIS_TYPE,
                basisLabel=REPORT_BASIS_LABEL,
                answer=payload.answer,
                sources=_to_sources(report_hits, title),
                topSimilarity=top_similarity,
                fallbackToWeb=False,
            )

        if web_sources:
            return ChatbotAnswerResponse(
                basisType=WEB_BASIS_TYPE,
                basisLabel=WEB_BASIS_LABEL,
                answer=payload.answer,
                sources=[_to_web_source(result) for result in web_sources],
                topSimilarity=top_similarity,
                fallbackToWeb=False,
            )

        return ChatbotAnswerResponse(
            basisType=NONE_BASIS_TYPE,
            basisLabel=None,
            answer=payload.answer,
            sources=[],
            topSimilarity=top_similarity,
            fallbackToWeb=False,
        )

    def _report_title(
        self,
        request: ChatbotAnswerRequest,
        profile: ApartmentProfile | None,
    ) -> str:
        name = profile.name if profile is not None else request.apartmentName
        return f"{name} 임장 리포트" if name else "임장 리포트"

    def _insufficient_evidence(
        self, top_similarity: float | None
    ) -> ChatbotAnswerResponse:
        return ChatbotAnswerResponse(
            basisType=NONE_BASIS_TYPE,
            basisLabel=None,
            answer=INSUFFICIENT_EVIDENCE_ANSWER,
            sources=[],
            topSimilarity=top_similarity,
            fallbackToWeb=False,
        )


def _valid_unique_numbers(numbers: Sequence[int], maximum: int) -> list[int]:
    seen: set[int] = set()
    valid: list[int] = []
    for number in numbers:
        if number < 1 or number > maximum or number in seen:
            continue
        seen.add(number)
        valid.append(number)
    return valid


def _valid_unique_profile_fields(
    fields: Sequence[str], profile: ApartmentProfile | None
) -> list[str]:
    if profile is None:
        return []

    allowed = (
        "address",
        "district_name",
        "dong_name",
        "household_count",
        "completion_year_month",
        "parking_space_count",
    )
    seen: set[str] = set()
    valid: list[str] = []
    for field in fields:
        if field not in allowed or field in seen:
            continue
        value = getattr(profile, field)
        if value is None or (isinstance(value, str) and not value.strip()):
            continue
        seen.add(field)
        valid.append(field)
    return valid


def _to_sources(
    hits: Sequence[ReportChunkHit],
    title: str,
) -> list[ChatbotAnswerSource]:
    """One citation per report, keeping the first selected chunk of each."""
    seen: set[int] = set()
    sources: list[ChatbotAnswerSource] = []
    for hit in hits:
        if hit.source_id in seen:
            continue
        seen.add(hit.source_id)
        sources.append(
            ChatbotAnswerSource(
                sourceType=REPORT_BASIS_TYPE,
                sourceId=hit.source_id,
                reportId=hit.source_id,
                title=title,
                sectionLabel=None,
                url=None,
                sourceAt=(
                    None if hit.source_at is None else hit.source_at.isoformat()
                ),
            )
        )
    return sources


def _to_web_source(result: WebSearchResult) -> ChatbotAnswerSource:
    return ChatbotAnswerSource(
        sourceType=WEB_BASIS_TYPE,
        sourceId=None,
        reportId=None,
        title=result.title,
        sectionLabel=None,
        url=result.url,
        sourceAt=(result.published_at or result.retrieved_at).isoformat(),
    )


def _filter_web_results(
    results: Sequence[WebSearchResult],
    apartment_name: str | None,
    district_name: str | None,
    dong_name: str | None,
) -> list[WebSearchResult]:
    keywords = [
        normalized
        for value in (apartment_name, district_name, dong_name)
        if (normalized := _compact(value))
    ]
    if not keywords:
        return []
    return [
        result
        for result in results
        if any(
            keyword in _compact(f"{result.title} {result.snippet}")
            for keyword in keywords
        )
    ]


def _filter_web_results_by_age(
    results: Sequence[WebSearchResult],
    max_age_days: int,
    now: datetime | None = None,
) -> list[WebSearchResult]:
    reference = now or datetime.now(timezone.utc)
    if reference.tzinfo is None:
        reference = reference.replace(tzinfo=timezone.utc)
    else:
        reference = reference.astimezone(timezone.utc)
    cutoff = reference - timedelta(days=max_age_days)

    filtered: list[WebSearchResult] = []
    for result in results:
        published_at = result.published_at
        if published_at is None:
            filtered.append(result)
            continue
        if published_at.tzinfo is None:
            published_at = published_at.replace(tzinfo=timezone.utc)
        else:
            published_at = published_at.astimezone(timezone.utc)
        if published_at >= cutoff:
            filtered.append(result)
    return filtered


def _compact(value: str | None) -> str:
    if value is None:
        return ""
    return "".join(strip_html(value).split()).casefold()
