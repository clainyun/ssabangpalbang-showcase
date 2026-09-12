"""LLM provider port and protocol-specific external adapters."""

from __future__ import annotations

import json
import logging
import math
import os
import re
from copy import deepcopy
from abc import ABC, abstractmethod
from typing import Any

import httpx

from app.config import env_bool

logger = logging.getLogger(__name__)

_MAX_BRACE_REPAIR = 3
_NON_RETRYABLE_FINISH_REASONS = {
    "SAFETY",
    "RECITATION",
    "BLOCKLIST",
    "PROHIBITED_CONTENT",
    "LANGUAGE",
    "SPII",
    "MALFORMED_RESPONSE",
    "ESCALATION",
}


class LlmProviderError(Exception):
    """Raised when the external LLM call fails technically."""

    def __init__(self, message: str, *, status_code: int | None = None) -> None:
        super().__init__(message)
        self.status_code = status_code


class LlmProvider(ABC):
    @abstractmethod
    async def complete_json(self, system_prompt: str, user_prompt: str) -> dict[str, Any]:
        """Return parsed JSON object from the model."""


def _json_retry_count() -> int:
    retry_count = int(os.getenv("AI_JSON_RETRY_COUNT", "2"))
    if retry_count < 0:
        raise ValueError("AI_JSON_RETRY_COUNT must be non-negative")
    return retry_count


class _RetryableJsonError(Exception):
    """Internal signal for a retryable GMS Gemini JSON response failure."""

    def __init__(
        self,
        reason: str,
        *,
        finish_reason: Any = None,
        part_count: int = 0,
        text_len: int = 0,
        decode_msg: str | None = None,
        decode_pos: int | None = None,
    ) -> None:
        super().__init__(reason)
        self.reason = reason
        self.finish_reason = finish_reason
        self.part_count = part_count
        self.text_len = text_len
        self.decode_msg = decode_msg
        self.decode_pos = decode_pos

    def as_provider_error(self, attempts: int) -> LlmProviderError:
        return LlmProviderError(
            f"GMS Gemini returned invalid JSON after {attempts} attempts "
            f"(finishReason={self.finish_reason} parts={self.part_count} "
            f"len={self.text_len} at={self.decode_pos} msg={self.decode_msg})"
        )


def _decode_error_from(exc: BaseException) -> json.JSONDecodeError | None:
    current: BaseException | None = exc
    while current is not None:
        if isinstance(current, json.JSONDecodeError):
            return current
        current = current.__cause__
    return None


def _parse_with_brace_repair(text: str) -> tuple[dict[str, Any], bool]:
    """Parse a JSON object, repairing only top-level closing braces."""
    stripped = text.strip()
    first_error: LlmProviderError | None = None

    try:
        return _extract_json_object(stripped), False
    except LlmProviderError as exc:
        first_error = exc

    try:
        parsed, _ = json.JSONDecoder().raw_decode(stripped)
        if isinstance(parsed, dict):
            logger.warning(
                "GMS Gemini JSON brace-repaired (stage=trailing-data len=%d)",
                len(text),
            )
            return parsed, True
    except ValueError:
        pass

    for missing in range(1, _MAX_BRACE_REPAIR + 1):
        try:
            parsed = json.loads(stripped + ("}" * missing))
            if isinstance(parsed, dict):
                logger.warning(
                    "GMS Gemini JSON brace-repaired "
                    "(stage=missing-%d len=%d)",
                    missing,
                    len(text),
                )
                return parsed, True
        except ValueError:
            continue

    raise LlmProviderError(
        "GMS Gemini returned unrecoverable JSON"
    ) from first_error


def _timeouts() -> httpx.Timeout:
    connect_timeout = float(os.getenv("AI_CONNECT_TIMEOUT", "3"))
    read_timeout = float(os.getenv("AI_READ_TIMEOUT", "20"))
    return httpx.Timeout(
        connect=connect_timeout,
        read=read_timeout,
        write=read_timeout,
        pool=connect_timeout,
    )


def _extract_json_object(text: str) -> dict[str, Any]:
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned)
        cleaned = re.sub(r"\s*```$", "", cleaned)
    try:
        parsed = json.loads(cleaned)
    except json.JSONDecodeError as exc:
        match = re.search(r"\{.*\}", cleaned, flags=re.DOTALL)
        if not match:
            raise LlmProviderError("LLM returned invalid JSON") from exc
        try:
            parsed = json.loads(match.group(0))
        except json.JSONDecodeError as nested:
            raise LlmProviderError("LLM returned invalid JSON") from nested
    if not isinstance(parsed, dict):
        raise LlmProviderError("LLM JSON root must be an object")
    return parsed


_GEMINI_TO_JSON_TYPE = {
    "OBJECT": "object",
    "STRING": "string",
    "ARRAY": "array",
    "INTEGER": "integer",
    "NUMBER": "number",
    "BOOLEAN": "boolean",
}


def to_openai_json_schema(schema: dict[str, Any]) -> dict[str, Any]:
    """Convert Gemini responseSchema dialect to strict JSON Schema."""
    converted = {
        key: deepcopy(value)
        for key, value in schema.items()
        if key not in {"type", "properties", "items", "required", "additionalProperties"}
    }

    raw_type = schema.get("type")
    if raw_type is not None:
        if raw_type not in _GEMINI_TO_JSON_TYPE:
            raise ValueError(f"Unsupported Gemini schema type: {raw_type}")
        converted_type = _GEMINI_TO_JSON_TYPE[raw_type]
        converted["type"] = converted_type
    else:
        converted_type = None

    if "properties" in schema:
        properties = schema["properties"]
        converted["properties"] = {
            name: to_openai_json_schema(value)
            for name, value in properties.items()
        }
    if "items" in schema:
        converted["items"] = to_openai_json_schema(schema["items"])

    if converted_type == "object":
        properties = converted.get("properties", {})
        converted["required"] = list(properties.keys())
        converted["additionalProperties"] = False

    return converted


class _RetryableOpenAiError(Exception):
    """Internal signal for a retryable OpenAI-compatible failure."""

    def __init__(self, provider_error: LlmProviderError) -> None:
        super().__init__(str(provider_error))
        self.provider_error = provider_error


class OpenAiCompatibleProvider(LlmProvider):
    """OpenAI Chat Completions compatible REST adapter."""

    def __init__(
        self,
        *,
        base_url: str | None = None,
        api_key: str | None = None,
        model: str | None = None,
        response_schema: dict[str, Any] | None = None,
        endpoint_url: str | None = None,
        instruction_role: str = "system",
        json_mode: bool = True,
    ) -> None:
        self.base_url = (base_url or os.getenv("AI_API_BASE_URL", "")).rstrip("/")
        self.api_key = api_key or os.getenv("AI_API_KEY", "")
        self.model = model or os.getenv("AI_MODEL", "")
        self.endpoint_url = endpoint_url.strip() if endpoint_url is not None else None
        if instruction_role not in {"system", "developer"}:
            raise ValueError("instruction_role must be system or developer")
        self.instruction_role = instruction_role
        self.json_mode = json_mode
        self.response_schema = response_schema
        self.structured_output = (
            env_bool("AI_STRUCTURED_OUTPUT", True)
            if response_schema is not None
            else False
        )

    def is_configured(self) -> bool:
        return bool(
            (self.endpoint_url or self.base_url)
            and self.api_key
            and self.model
        )

    def _endpoint(self) -> str:
        if self.endpoint_url:
            return self.endpoint_url
        return f"{self.base_url}/chat/completions"

    async def complete_json(self, system_prompt: str, user_prompt: str) -> dict[str, Any]:
        if not self.is_configured():
            raise LlmProviderError("LLM provider is not configured")

        attempts = 1 + _json_retry_count()
        last_error: LlmProviderError | None = None

        for attempt in range(1, attempts + 1):
            try:
                parsed = await self._attempt_once(system_prompt, user_prompt)
            except _RetryableOpenAiError as exc:
                last_error = exc.provider_error
                if attempt < attempts:
                    logger.warning(
                        "OpenAI-compatible call failed; retrying "
                        "(attempt=%d/%d reason=%s)",
                        attempt,
                        attempts,
                        str(last_error),
                    )
                    continue
                break

            logger.info(
                "OpenAI-compatible response parsed successfully (attempt=%d/%d)",
                attempt,
                attempts,
            )
            return parsed

        if last_error is None:
            raise LlmProviderError("LLM returned invalid JSON")
        raise last_error

    async def _attempt_once(
        self,
        system_prompt: str,
        user_prompt: str,
    ) -> dict[str, Any]:

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        body: dict[str, Any] = {
            "model": self.model,
            "messages": [
                {"role": self.instruction_role, "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
        }
        if self.json_mode:
            if self.response_schema is not None and self.structured_output:
                body["response_format"] = {
                    "type": "json_schema",
                    "json_schema": {
                        "name": "chatbot_answer",
                        "strict": True,
                        "schema": to_openai_json_schema(self.response_schema),
                    },
                }
            else:
                body["response_format"] = {"type": "json_object"}

        temperature = os.getenv("AI_TEMPERATURE", "").strip()
        if temperature:
            # A malformed value must surface as LlmProviderError, not a bare
            # ValueError: the service maps only the former to PROVIDER_FAILED
            # (502), which is the contract Spring's fallback branch relies on.
            try:
                temperature_value = float(temperature)
                if not math.isfinite(temperature_value):
                    raise ValueError("temperature must be finite")
                body["temperature"] = temperature_value
            except ValueError as exc:
                raise LlmProviderError(
                    f"AI_TEMPERATURE must be a number: {temperature!r}"
                ) from exc

        logger.info("Calling OpenAI-compatible chat completions model=%s", self.model)
        try:
            async with httpx.AsyncClient(timeout=_timeouts()) as client:
                response = await client.post(
                    self._endpoint(),
                    headers=headers,
                    json=body,
                )
        except httpx.TimeoutException as exc:
            raise _RetryableOpenAiError(
                LlmProviderError("LLM provider timeout")
            ) from exc
        except httpx.HTTPError as exc:
            raise LlmProviderError(f"LLM provider connection failed: {exc}") from exc

        if response.status_code >= 500:
            raise _RetryableOpenAiError(
                LlmProviderError(
                    "OpenAI-compatible provider server error "
                f"(status={response.status_code})",
                    status_code=response.status_code,
                )
            )
        if not 200 <= response.status_code < 300:
            raise LlmProviderError(
                "OpenAI-compatible provider client error "
                f"(status={response.status_code})",
                status_code=response.status_code,
            )

        try:
            payload = response.json()
        except ValueError as exc:
            raise _RetryableOpenAiError(
                LlmProviderError("LLM returned invalid JSON")
            ) from exc

        try:
            choice = payload["choices"][0]
            finish_reason = choice.get("finish_reason")
            if finish_reason == "length":
                raise LlmProviderError(
                    "OpenAI-compatible provider returned truncated content "
                    "(finish_reason=length)"
                )
            if finish_reason == "content_filter":
                raise LlmProviderError(
                    "OpenAI-compatible provider blocked content "
                    "(finish_reason=content_filter)"
                )
            content = choice["message"]["content"]
            if not content or not str(content).strip():
                raise _RetryableOpenAiError(
                    LlmProviderError("LLM returned empty content")
                )
            try:
                return _extract_json_object(str(content))
            except LlmProviderError as exc:
                raise _RetryableOpenAiError(
                    LlmProviderError("LLM returned invalid JSON")
                ) from exc
        except LlmProviderError:
            raise
        except (ValueError, KeyError, IndexError, TypeError, AttributeError) as exc:
            raise _RetryableOpenAiError(
                LlmProviderError("LLM returned invalid JSON")
            ) from exc


class GmsGeminiProvider(LlmProvider):
    """SSAFY GMS Gemini generateContent adapter.

    Auth header: x-goog-api-key
    Endpoint:
      {AI_API_BASE_URL}/v1beta/models/{AI_MODEL}:generateContent
    """

    def __init__(
        self,
        *,
        base_url: str | None = None,
        api_key: str | None = None,
        model: str | None = None,
        response_schema: dict[str, Any] | None = None,
        endpoint_url: str | None = None,
        json_mode: bool = True,
        structured_output: bool | None = None,
        json_retry_count: int | None = None,
    ) -> None:
        self.base_url = (base_url or os.getenv("AI_API_BASE_URL", "")).rstrip("/")
        self.api_key = api_key or os.getenv("AI_API_KEY", "")
        self.model = model or os.getenv("AI_MODEL", "")
        self.endpoint_url = endpoint_url.strip() if endpoint_url is not None else None
        self.response_schema = response_schema
        self.json_mode = json_mode
        if structured_output is None:
            structured_output = env_bool("AI_STRUCTURED_OUTPUT", True)
        self.structured_output = bool(
            json_mode and response_schema is not None and structured_output
        )
        if json_retry_count is not None and json_retry_count < 0:
            raise ValueError("json_retry_count must be non-negative")
        self.json_retry_count = json_retry_count

    def is_configured(self) -> bool:
        return bool(
            (self.endpoint_url or self.base_url)
            and self.api_key
            and self.model
        )

    def _endpoint(self) -> str:
        if self.endpoint_url:
            return self.endpoint_url
        return f"{self.base_url}/v1beta/models/{self.model}:generateContent"

    async def complete_json(self, system_prompt: str, user_prompt: str) -> dict[str, Any]:
        if not self.is_configured():
            raise LlmProviderError("GMS Gemini provider is not configured")

        retry_count = (
            _json_retry_count()
            if self.json_retry_count is None
            else self.json_retry_count
        )
        attempts = 1 + retry_count
        last_error: LlmProviderError | None = None

        for attempt in range(1, attempts + 1):
            try:
                parsed = await self._attempt_once(system_prompt, user_prompt)
            except _RetryableJsonError as exc:
                last_error = exc.as_provider_error(attempts)
                if attempt < attempts:
                    logger.warning(
                        "GMS Gemini JSON parse failed; retrying "
                        "(attempt=%d/%d reason=%s)",
                        attempt,
                        attempts,
                        exc.reason,
                    )
                    continue
                break

            logger.info(
                "GMS Gemini response parsed successfully (attempt=%d/%d)",
                attempt,
                attempts,
            )
            return parsed

        if last_error is None:
            raise LlmProviderError("GMS Gemini returned invalid JSON")
        raise last_error

    async def _attempt_once(
        self,
        system_prompt: str,
        user_prompt: str,
    ) -> dict[str, Any]:
        headers = {
            "x-goog-api-key": self.api_key,
            "Content-Type": "application/json",
        }
        body = {
            "systemInstruction": {
                "parts": [{"text": system_prompt}],
            },
            "contents": [
                {
                    "role": "user",
                    "parts": [{"text": user_prompt}],
                }
            ],
            "generationConfig": {
                "temperature": 0.2,
            },
        }
        if self.json_mode:
            body["generationConfig"]["responseMimeType"] = "application/json"
        if self.response_schema is not None and self.structured_output:
            body["generationConfig"]["responseSchema"] = self.response_schema

        logger.info(
            "Calling GMS Gemini generateContent model=%s",
            self.model,
        )
        try:
            async with httpx.AsyncClient(timeout=_timeouts()) as client:
                response = await client.post(
                    self._endpoint(),
                    headers=headers,
                    json=body,
                )
        except httpx.TimeoutException as exc:
            raise LlmProviderError("GMS Gemini timeout") from exc
        except httpx.HTTPError as exc:
            raise LlmProviderError(f"GMS Gemini connection failed: {exc}") from exc

        if response.status_code >= 500:
            raise LlmProviderError(
                f"GMS Gemini server error (status={response.status_code})",
                status_code=response.status_code,
            )
        if not 200 <= response.status_code < 300:
            raise LlmProviderError(
                f"GMS Gemini client error (status={response.status_code})",
                status_code=response.status_code,
            )

        try:
            payload = response.json()
        except ValueError as exc:
            decode_error = _decode_error_from(exc)
            raise _RetryableJsonError(
                "response-json",
                text_len=len(response.content),
                decode_msg=decode_error.msg if decode_error else type(exc).__name__,
                decode_pos=decode_error.pos if decode_error else None,
            ) from exc
        if not isinstance(payload, dict):
            raise _RetryableJsonError(
                "json-root",
                text_len=len(response.content),
                decode_msg="JSON root must be an object",
            )

        candidates = payload.get("candidates")
        if not isinstance(candidates, list) or not candidates:
            prompt_feedback = payload.get("promptFeedback")
            block_reason = (
                prompt_feedback.get("blockReason")
                if isinstance(prompt_feedback, dict)
                else None
            )
            raise LlmProviderError(
                "GMS Gemini returned no candidates "
                f"(blockReason={block_reason})"
            )

        first_candidate = candidates[0] if isinstance(candidates[0], dict) else {}
        finish_reason = first_candidate.get("finishReason")
        content = first_candidate.get("content")
        content = content if isinstance(content, dict) else {}
        parts = content.get("parts")
        parts = parts if isinstance(parts, list) else []

        if finish_reason in _NON_RETRYABLE_FINISH_REASONS:
            raise LlmProviderError(
                "GMS Gemini generation blocked "
                f"(finishReason={finish_reason}, parts={len(parts)})"
            )
        if finish_reason == "MAX_TOKENS":
            raise LlmProviderError(
                "GMS Gemini returned truncated content "
                f"(finishReason={finish_reason}, parts={len(parts)})"
            )
        if finish_reason not in {None, "STOP"}:
            raise LlmProviderError(
                "GMS Gemini generation incomplete "
                f"(finishReason={finish_reason}, parts={len(parts)})"
            )

        chunks: list[str] = []
        for part in parts:
            if not isinstance(part, dict) or part.get("thought") is True:
                continue
            text = part.get("text")
            if isinstance(text, str) and text.strip():
                chunks.append(text)

        answer_text = "".join(chunks)
        if not answer_text.strip():
            if finish_reason == "STOP":
                raise _RetryableJsonError(
                    "empty-text",
                    finish_reason=finish_reason,
                    part_count=len(parts),
                    text_len=0,
                    decode_msg="No text part",
                )
            raise LlmProviderError(
                "GMS Gemini returned no text part "
                f"(finishReason={finish_reason}, "
                f"parts={len(parts)})"
            )

        try:
            parsed, _ = _parse_with_brace_repair(answer_text)
            return parsed
        except LlmProviderError as exc:
            decode_error = _decode_error_from(exc)
            raise _RetryableJsonError(
                "answer-json",
                finish_reason=finish_reason,
                part_count=len(parts),
                text_len=len(answer_text),
                decode_msg=decode_error.msg if decode_error else str(exc),
                decode_pos=decode_error.pos if decode_error else None,
            ) from exc


class AnthropicMessagesProvider(LlmProvider):
    """Anthropic Messages REST adapter for the SSAFY GMS proxy."""

    def __init__(
        self,
        *,
        endpoint_url: str | None = None,
        base_url: str | None = None,
        api_key: str | None = None,
        model: str | None = None,
        anthropic_version: str = "2023-06-01",
        max_tokens: int = 4096,
        temperature: float | None = None,
        response_schema: dict[str, Any] | None = None,
    ) -> None:
        self.endpoint_url = (
            endpoint_url.strip() if endpoint_url is not None else None
        )
        self.base_url = (
            base_url or os.getenv("AI_API_BASE_URL", "")
        ).rstrip("/")
        self.api_key = api_key or os.getenv("AI_API_KEY", "")
        self.model = model or os.getenv("AI_MODEL", "")
        self.anthropic_version = anthropic_version.strip()
        if max_tokens <= 0:
            raise ValueError("max_tokens must be positive")
        self.max_tokens = max_tokens
        if temperature is not None and not 0 <= temperature <= 1:
            raise ValueError("temperature must be between 0 and 1")
        self.temperature = temperature

    def is_configured(self) -> bool:
        return bool(
            (self.endpoint_url or self.base_url)
            and self.api_key
            and self.model
            and self.anthropic_version
        )

    def _endpoint(self) -> str:
        if self.endpoint_url:
            return self.endpoint_url
        return f"{self.base_url}/v1/messages"

    async def complete_json(
        self,
        system_prompt: str,
        user_prompt: str,
    ) -> dict[str, Any]:
        if not self.is_configured():
            raise LlmProviderError("GMS Anthropic provider is not configured")

        headers = {
            "x-api-key": self.api_key,
            "anthropic-version": self.anthropic_version,
            "Content-Type": "application/json",
        }
        body = {
            "model": self.model,
            "max_tokens": self.max_tokens,
            "system": system_prompt,
            "messages": [
                {"role": "user", "content": user_prompt},
            ],
        }
        if self.temperature is not None:
            body["temperature"] = self.temperature
        try:
            async with httpx.AsyncClient(timeout=_timeouts()) as client:
                response = await client.post(
                    self._endpoint(),
                    headers=headers,
                    json=body,
                )
        except httpx.TimeoutException as exc:
            raise LlmProviderError("GMS Anthropic timeout") from exc
        except httpx.HTTPError as exc:
            raise LlmProviderError(
                "GMS Anthropic connection failed"
            ) from exc

        if response.status_code >= 500:
            raise LlmProviderError(
                f"GMS Anthropic server error (status={response.status_code})",
                status_code=response.status_code,
            )
        if not 200 <= response.status_code < 300:
            raise LlmProviderError(
                f"GMS Anthropic client error (status={response.status_code})",
                status_code=response.status_code,
            )

        try:
            payload = response.json()
        except ValueError as exc:
            raise LlmProviderError(
                "GMS Anthropic returned invalid response JSON"
            ) from exc

        if not isinstance(payload, dict):
            raise LlmProviderError(
                "GMS Anthropic response root must be an object"
            )

        content = payload.get("content")
        if not isinstance(content, list):
            raise LlmProviderError(
                "GMS Anthropic returned invalid content"
            )

        chunks: list[str] = []
        for block in content:
            if not isinstance(block, dict) or block.get("type") != "text":
                continue
            text = block.get("text")
            if isinstance(text, str) and text.strip():
                chunks.append(text)

        stop_reason = payload.get("stop_reason")
        if stop_reason in {"max_tokens", "model_context_window_exceeded"}:
            raise LlmProviderError(
                "GMS Anthropic returned truncated content "
                f"(stop_reason={stop_reason})"
            )
        if stop_reason not in {"end_turn", "stop_sequence"}:
            raise LlmProviderError(
                "GMS Anthropic generation incomplete "
                f"(stop_reason={stop_reason})"
            )

        answer_text = "".join(chunks)
        if not answer_text.strip():
            raise LlmProviderError("GMS Anthropic returned empty content")
        try:
            return _extract_json_object(answer_text)
        except LlmProviderError as exc:
            raise LlmProviderError(
                "GMS Anthropic returned invalid JSON"
            ) from exc


class FakeLlmProvider(LlmProvider):
    """Deterministic provider for unit tests."""

    def __init__(self, payload: dict[str, Any] | None = None, error: Exception | None = None):
        self.payload = payload
        self.error = error
        self.last_system: str | None = None
        self.last_user: str | None = None

    async def complete_json(self, system_prompt: str, user_prompt: str) -> dict[str, Any]:
        self.last_system = system_prompt
        self.last_user = user_prompt
        if self.error is not None:
            raise self.error
        if self.payload is None:
            raise LlmProviderError("Fake provider has no payload")
        return self.payload


def create_llm_provider(
    response_schema: dict[str, Any] | None = None,
    *,
    purpose: str | None = None,
) -> LlmProvider:
    """Select provider from {purpose}_AI_PROVIDER, falling back to AI_PROVIDER."""
    name = (_purpose_env("AI_PROVIDER", purpose) or "gms-gemini").lower()
    provider_args = {
        "base_url": _purpose_env("AI_API_BASE_URL", purpose),
        "api_key": _purpose_env("AI_API_KEY", purpose),
        "model": _purpose_env("AI_MODEL", purpose),
        "response_schema": response_schema,
    }
    if name in {"gms-gemini", "gemini", "gms"}:
        provider = GmsGeminiProvider(**provider_args)
        logger.info("Selected LLM provider=gms-gemini configured=%s", provider.is_configured())
        return provider
    if name in {"openai-compatible", "openai"}:
        provider = OpenAiCompatibleProvider(**provider_args)
        logger.info(
            "Selected LLM provider=openai-compatible configured=%s",
            provider.is_configured(),
        )
        return provider
    raise LlmProviderError(f"Unsupported AI_PROVIDER: {name}")


def _purpose_env(name: str, purpose: str | None) -> str:
    """Return a non-blank purpose override, or the global environment value."""
    if purpose:
        scoped = os.getenv(f"{purpose}_{name}")
        if scoped is not None and scoped.strip():
            return scoped.strip()
    return (os.getenv(name) or "").strip()
