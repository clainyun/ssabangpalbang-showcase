"""Raw, single-attempt GMS client used only by the controlled benchmark.

Production adapters intentionally make provider-specific parsing choices.  The
benchmark needs the opposite: retain only the model text and apply one parser
after all three protocols have been normalized to the same outcome taxonomy.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Literal

import httpx

from app.providers.gms_model_suite import (
    ANTHROPIC_MODEL,
    GEMINI_MODEL,
    OPENAI_MODEL,
    GmsModelSuiteSettings,
)


TransportOutcome = Literal[
    "transport", "rate-limit", "policy", "incomplete", "success"
]


@dataclass(frozen=True)
class RawCompletion:
    outcome: TransportOutcome
    text: str | None = None
    status_code: int | None = None


class GmsRawClient:
    """Call the three supported GMS protocols without JSON repair or retry."""

    def __init__(
        self,
        settings: GmsModelSuiteSettings,
        *,
        temperature: float,
        timeout_seconds: float = 40.0,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        settings.validate()
        if not 0 <= temperature <= 1:
            raise ValueError("temperature must be between 0 and 1")
        if timeout_seconds <= 0:
            raise ValueError("timeout_seconds must be positive")
        self._settings = settings
        self._temperature = temperature
        self._timeout = httpx.Timeout(timeout_seconds)
        self._transport = transport

    async def complete_raw(
        self,
        model: str,
        system_prompt: str,
        user_prompt: str,
    ) -> RawCompletion:
        if model == GEMINI_MODEL:
            url, headers, body = self._gemini_request(system_prompt, user_prompt)
            decoder = self._decode_gemini
        elif model == OPENAI_MODEL:
            url, headers, body = self._openai_request(system_prompt, user_prompt)
            decoder = self._decode_openai
        elif model == ANTHROPIC_MODEL:
            url, headers, body = self._anthropic_request(system_prompt, user_prompt)
            decoder = self._decode_anthropic
        else:
            raise ValueError("unsupported official model id")

        try:
            async with httpx.AsyncClient(
                timeout=self._timeout,
                transport=self._transport,
            ) as client:
                response = await client.post(url, headers=headers, json=body)
        except (httpx.TimeoutException, httpx.HTTPError):
            return RawCompletion("transport")

        if response.status_code == 429:
            return RawCompletion("rate-limit", status_code=429)
        if response.status_code >= 400:
            return RawCompletion("transport", status_code=response.status_code)
        try:
            payload = response.json()
        except (json.JSONDecodeError, ValueError):
            return RawCompletion("transport", status_code=response.status_code)
        if not isinstance(payload, dict):
            return RawCompletion("transport", status_code=response.status_code)
        return decoder(payload)

    def _gemini_request(self, system: str, user: str):
        return (
            self._settings.gemini_endpoint_url,
            {
                "x-goog-api-key": self._settings.api_key,
                "Content-Type": "application/json",
            },
            {
                "systemInstruction": {"parts": [{"text": system}]},
                "contents": [{"role": "user", "parts": [{"text": user}]}],
                "generationConfig": {"temperature": self._temperature},
            },
        )

    def _openai_request(self, system: str, user: str):
        return (
            self._settings.openai_endpoint_url,
            {
                "Authorization": f"Bearer {self._settings.api_key}",
                "Content-Type": "application/json",
            },
            {
                "model": self._settings.openai_model,
                "temperature": self._temperature,
                "messages": [
                    {"role": "developer", "content": system},
                    {"role": "user", "content": user},
                ],
            },
        )

    def _anthropic_request(self, system: str, user: str):
        return (
            self._settings.anthropic_endpoint_url,
            {
                "x-api-key": self._settings.api_key,
                "anthropic-version": self._settings.anthropic_version,
                "Content-Type": "application/json",
            },
            {
                "model": self._settings.anthropic_model,
                "max_tokens": self._settings.anthropic_max_tokens,
                "temperature": self._temperature,
                "system": system,
                "messages": [{"role": "user", "content": user}],
            },
        )

    @staticmethod
    def _decode_gemini(payload: dict) -> RawCompletion:
        candidates = payload.get("candidates")
        if not isinstance(candidates, list) or not candidates:
            feedback = payload.get("promptFeedback")
            if isinstance(feedback, dict) and feedback.get("blockReason"):
                return RawCompletion("policy")
            return RawCompletion("incomplete")
        candidate = candidates[0] if isinstance(candidates[0], dict) else {}
        finish = candidate.get("finishReason")
        if finish in {
            "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT",
            "LANGUAGE", "SPII", "MALFORMED_RESPONSE", "ESCALATION",
        }:
            return RawCompletion("policy")
        if finish != "STOP":
            return RawCompletion("incomplete")
        content = candidate.get("content")
        parts = content.get("parts") if isinstance(content, dict) else None
        if not isinstance(parts, list):
            return RawCompletion("incomplete")
        chunks = [
            part["text"]
            for part in parts
            if isinstance(part, dict)
            and part.get("thought") is not True
            and isinstance(part.get("text"), str)
        ]
        text = "".join(chunks)
        return RawCompletion("success", text=text) if text.strip() else RawCompletion("incomplete")

    @staticmethod
    def _decode_openai(payload: dict) -> RawCompletion:
        choices = payload.get("choices")
        if not isinstance(choices, list) or not choices:
            return RawCompletion("incomplete")
        choice = choices[0] if isinstance(choices[0], dict) else {}
        finish = choice.get("finish_reason")
        if finish == "content_filter":
            return RawCompletion("policy")
        if finish != "stop":
            return RawCompletion("incomplete")
        message = choice.get("message")
        text = message.get("content") if isinstance(message, dict) else None
        return RawCompletion("success", text=text) if isinstance(text, str) and text.strip() else RawCompletion("incomplete")

    @staticmethod
    def _decode_anthropic(payload: dict) -> RawCompletion:
        stop = payload.get("stop_reason")
        if stop in {"refusal", "content_filtered"}:
            return RawCompletion("policy")
        if stop not in {"end_turn", "stop_sequence"}:
            return RawCompletion("incomplete")
        content = payload.get("content")
        if not isinstance(content, list):
            return RawCompletion("incomplete")
        chunks = [
            block["text"]
            for block in content
            if isinstance(block, dict)
            and block.get("type") == "text"
            and isinstance(block.get("text"), str)
        ]
        text = "".join(chunks)
        return RawCompletion("success", text=text) if text.strip() else RawCompletion("incomplete")
