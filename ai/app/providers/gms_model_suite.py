"""Explicit three-model SSAFY GMS provider suite for offline evaluation."""

from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass, field
from typing import Any
from urllib.parse import urlsplit

from app.config import load_ai_env
from app.providers.llm_provider import (
    AnthropicMessagesProvider,
    GmsGeminiProvider,
    LlmProvider,
    LlmProviderError,
    OpenAiCompatibleProvider,
)

GEMINI_MODEL = "gemini-3.5-flash"
OPENAI_MODEL = "gpt-5.4-mini"
CHECKLIST_OPENAI_MODEL = "gpt-5.4-nano"
CHECKLIST_BENCHMARK_MODELS = (GEMINI_MODEL, CHECKLIST_OPENAI_MODEL)
ANTHROPIC_MODEL = "claude-sonnet-4-6"
ANTHROPIC_VERSION = "2023-06-01"
DEFAULT_ANTHROPIC_MAX_TOKENS = 4096
GEMINI_ENDPOINT = (
    "https://ai-gateway.example.com/gmsapi/generativelanguage.googleapis.com/"
    "v1beta/models/gemini-3.5-flash:generateContent"
)
_GMS_HOST = "ai-gateway.example.com"


def _validate_gms_endpoint(
    name: str,
    value: str,
    *,
    path_suffix: str,
) -> None:
    """Reject destinations that could disclose the shared GMS credential."""
    try:
        parsed = urlsplit(value)
        port = parsed.port
    except ValueError as exc:
        raise LlmProviderError(f"{name} must be a valid GMS HTTPS URL") from exc

    if (
        parsed.scheme != "https"
        or parsed.hostname != _GMS_HOST
        or port not in {None, 443}
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
        or not parsed.path.endswith(path_suffix)
    ):
        raise LlmProviderError(
            f"{name} must be an HTTPS {_GMS_HOST} URL ending in {path_suffix}"
        )


@dataclass(frozen=True)
class GmsModelSuiteSettings:
    """Non-secret endpoints/models plus the single shared GMS credential."""

    api_key: str = field(repr=False)
    gemini_endpoint_url: str
    gemini_model: str
    openai_endpoint_url: str
    openai_model: str
    anthropic_endpoint_url: str
    anthropic_model: str
    anthropic_version: str
    anthropic_max_tokens: int

    @classmethod
    def from_env(cls) -> "GmsModelSuiteSettings":
        load_ai_env()
        return cls.from_mapping(os.environ)

    @classmethod
    def from_mapping(
        cls,
        values: Mapping[str, str | None],
    ) -> "GmsModelSuiteSettings":
        """Resolve one explicit configuration source without loading dotenv files."""

        def resolved(name: str, default: str = "") -> str:
            value = values.get(name)
            return default if value is None else value

        raw_max_tokens = resolved(
            "GMS_ANTHROPIC_MAX_TOKENS",
            str(DEFAULT_ANTHROPIC_MAX_TOKENS),
        ).strip()
        try:
            max_tokens = int(raw_max_tokens)
        except ValueError as exc:
            raise LlmProviderError(
                "GMS_ANTHROPIC_MAX_TOKENS must be a positive integer"
            ) from exc

        settings = cls(
            api_key=resolved("GMS_KEY").strip(),
            gemini_endpoint_url=resolved(
                "GMS_GEMINI_ENDPOINT_URL",
                GEMINI_ENDPOINT,
            ).strip(),
            gemini_model=resolved(
                "GMS_GEMINI_MODEL",
                GEMINI_MODEL,
            ).strip(),
            openai_endpoint_url=resolved(
                "GMS_OPENAI_ENDPOINT_URL",
            ).strip(),
            openai_model=resolved(
                "GMS_OPENAI_MODEL",
                OPENAI_MODEL,
            ).strip(),
            anthropic_endpoint_url=resolved(
                "GMS_ANTHROPIC_ENDPOINT_URL",
            ).strip(),
            anthropic_model=resolved(
                "GMS_ANTHROPIC_MODEL",
                ANTHROPIC_MODEL,
            ).strip(),
            anthropic_version=resolved(
                "GMS_ANTHROPIC_VERSION",
                ANTHROPIC_VERSION,
            ).strip(),
            anthropic_max_tokens=max_tokens,
        )
        settings.validate()
        return settings

    def validate(self) -> None:
        required = {
            "GMS_KEY": self.api_key,
            "GMS_GEMINI_ENDPOINT_URL": self.gemini_endpoint_url,
            "GMS_GEMINI_MODEL": self.gemini_model,
            "GMS_OPENAI_ENDPOINT_URL": self.openai_endpoint_url,
            "GMS_OPENAI_MODEL": self.openai_model,
            "GMS_ANTHROPIC_ENDPOINT_URL": self.anthropic_endpoint_url,
            "GMS_ANTHROPIC_MODEL": self.anthropic_model,
            "GMS_ANTHROPIC_VERSION": self.anthropic_version,
        }
        missing = [
            name
            for name, value in required.items()
            if not isinstance(value, str) or not value.strip()
        ]
        if missing:
            raise LlmProviderError(
                "Missing GMS model suite configuration: "
                + ", ".join(missing)
            )
        padded = [
            name
            for name, value in required.items()
            if isinstance(value, str) and value != value.strip()
        ]
        if padded:
            raise LlmProviderError(
                "GMS model suite values must not have surrounding whitespace: "
                + ", ".join(padded)
            )
        if self.anthropic_max_tokens <= 0:
            raise LlmProviderError(
                "GMS_ANTHROPIC_MAX_TOKENS must be a positive integer"
            )
        model_ids = {
            self.gemini_model,
            self.openai_model,
            self.anthropic_model,
        }
        if len(model_ids) != 3:
            raise LlmProviderError(
                "GMS model identifiers must be distinct"
            )

        _validate_gms_endpoint(
            "GMS_GEMINI_ENDPOINT_URL",
            self.gemini_endpoint_url,
            path_suffix=(
                f"/v1beta/models/{self.gemini_model}:generateContent"
            ),
        )
        _validate_gms_endpoint(
            "GMS_OPENAI_ENDPOINT_URL",
            self.openai_endpoint_url,
            path_suffix="/chat/completions",
        )
        _validate_gms_endpoint(
            "GMS_ANTHROPIC_ENDPOINT_URL",
            self.anthropic_endpoint_url,
            path_suffix="/v1/messages",
        )


@dataclass(frozen=True)
class GmsChecklistBenchmarkSettings:
    """Minimal two-model settings for the checklist benchmark."""

    api_key: str = field(repr=False)
    gemini_endpoint_url: str
    openai_endpoint_url: str

    @classmethod
    def from_mapping(
        cls,
        values: Mapping[str, str | None],
    ) -> "GmsChecklistBenchmarkSettings":
        def resolved(name: str, default: str = "") -> str:
            value = values.get(name)
            return default if value is None else value

        settings = cls(
            api_key=resolved("GMS_KEY").strip(),
            gemini_endpoint_url=resolved(
                "GMS_GEMINI_ENDPOINT_URL",
                GEMINI_ENDPOINT,
            ).strip(),
            openai_endpoint_url=resolved("GMS_OPENAI_ENDPOINT_URL").strip(),
        )
        settings.validate()
        return settings

    def validate(self) -> None:
        required = {
            "GMS_KEY": self.api_key,
            "GMS_GEMINI_ENDPOINT_URL": self.gemini_endpoint_url,
            "GMS_OPENAI_ENDPOINT_URL": self.openai_endpoint_url,
        }
        missing = [name for name, value in required.items() if not value]
        if missing:
            raise LlmProviderError(
                "Missing GMS checklist benchmark configuration: "
                + ", ".join(missing)
            )
        _validate_gms_endpoint(
            "GMS_GEMINI_ENDPOINT_URL",
            self.gemini_endpoint_url,
            path_suffix=(
                f"/v1beta/models/{GEMINI_MODEL}:generateContent"
            ),
        )
        _validate_gms_endpoint(
            "GMS_OPENAI_ENDPOINT_URL",
            self.openai_endpoint_url,
            path_suffix="/chat/completions",
        )


def create_gms_model_suite(
    settings: GmsModelSuiteSettings | None = None,
) -> dict[str, LlmProvider]:
    """Build three independent providers without changing production routing."""
    resolved = settings or GmsModelSuiteSettings.from_env()
    resolved.validate()

    return {
        resolved.gemini_model: GmsGeminiProvider(
            endpoint_url=resolved.gemini_endpoint_url,
            api_key=resolved.api_key,
            model=resolved.gemini_model,
            json_mode=False,
        ),
        resolved.openai_model: OpenAiCompatibleProvider(
            endpoint_url=resolved.openai_endpoint_url,
            api_key=resolved.api_key,
            model=resolved.openai_model,
            instruction_role="developer",
            json_mode=False,
        ),
        resolved.anthropic_model: AnthropicMessagesProvider(
            endpoint_url=resolved.anthropic_endpoint_url,
            api_key=resolved.api_key,
            model=resolved.anthropic_model,
            anthropic_version=resolved.anthropic_version,
            max_tokens=resolved.anthropic_max_tokens,
            temperature=0.2,
        ),
    }


def create_gms_production_fit_suite(
    settings: GmsModelSuiteSettings,
    *,
    response_schema: dict[str, Any],
) -> dict[str, LlmProvider]:
    """Build the pinned production-fit evaluation adapters.

    Unlike the raw v2 benchmark, this suite deliberately uses the structured
    output mode that each deployed provider supports.  Retry and structured
    output choices are explicit so ambient process variables cannot skew an
    official comparison.
    """
    settings.validate()
    if settings.anthropic_max_tokens != DEFAULT_ANTHROPIC_MAX_TOKENS:
        raise LlmProviderError(
            "production-fit Anthropic max tokens must be 4096"
        )
    if not response_schema:
        raise ValueError("production-fit response schema is required")

    return {
        settings.gemini_model: GmsGeminiProvider(
            endpoint_url=settings.gemini_endpoint_url,
            api_key=settings.api_key,
            model=settings.gemini_model,
            response_schema=response_schema,
            json_mode=True,
            structured_output=True,
            json_retry_count=0,
        ),
        settings.openai_model: OpenAiCompatibleProvider(
            endpoint_url=settings.openai_endpoint_url,
            api_key=settings.api_key,
            model=settings.openai_model,
            response_schema=response_schema,
            instruction_role="developer",
            json_mode=True,
        ),
        settings.anthropic_model: AnthropicMessagesProvider(
            endpoint_url=settings.anthropic_endpoint_url,
            api_key=settings.api_key,
            model=settings.anthropic_model,
            anthropic_version=settings.anthropic_version,
            max_tokens=settings.anthropic_max_tokens,
            temperature=0.2,
            response_schema=response_schema,
        ),
    }


def create_gms_checklist_benchmark_suite(
    settings: GmsChecklistBenchmarkSettings,
    *,
    response_schema: dict[str, Any],
) -> dict[str, LlmProvider]:
    """Build the pinned Gemini/GPT checklist-selection adapters."""
    settings.validate()
    if not response_schema:
        raise ValueError("checklist benchmark response schema is required")

    return {
        GEMINI_MODEL: GmsGeminiProvider(
            endpoint_url=settings.gemini_endpoint_url,
            api_key=settings.api_key,
            model=GEMINI_MODEL,
            response_schema=response_schema,
            json_mode=True,
            structured_output=True,
            json_retry_count=0,
        ),
        CHECKLIST_OPENAI_MODEL: OpenAiCompatibleProvider(
            endpoint_url=settings.openai_endpoint_url,
            api_key=settings.api_key,
            model=CHECKLIST_OPENAI_MODEL,
            response_schema=response_schema,
            instruction_role="developer",
            json_mode=True,
        ),
    }
