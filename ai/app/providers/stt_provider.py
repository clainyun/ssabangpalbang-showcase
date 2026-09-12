"""STT provider port and OpenAI/faster-whisper/fake implementations."""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
import multiprocessing
from multiprocessing.connection import Connection
from pathlib import Path
import secrets
import threading
from typing import Any

import httpx

from app.config import SttSettings


class SttProviderError(Exception):
    """The STT model could not complete the transcription."""

    def __init__(
        self,
        message: str,
        *,
        code: str = "MODEL_UNAVAILABLE",
        retryable: bool = True,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.retryable = retryable


@dataclass(frozen=True)
class Transcription:
    text: str


class SttProvider(ABC):
    def warmup(self) -> None:
        """Load model resources before the Kafka consumer accepts work."""

    @abstractmethod
    def transcribe(self, audio_path: Path, language: str) -> Transcription:
        """Synchronously transcribe audio; callers must run this off the event loop."""

    def abort(self) -> None:
        """Force the current inference to stop, if the provider supports it."""

    def close(self) -> None:
        """Release provider resources."""

    def is_ready(self) -> bool:
        """Return whether this provider can accept inference work."""
        return True


class FasterWhisperProvider(SttProvider):
    """Lazy-loading faster-whisper adapter.

    Model construction can download weights, so production should pre-provision the
    configured model in a persistent Hugging Face cache.
    """

    def __init__(
        self,
        *,
        model: str,
        device: str,
        compute_type: str,
    ) -> None:
        self._model_name = model
        self._device = device
        self._compute_type = compute_type
        self._model: Any | None = None

    def _get_model(self) -> Any:
        if self._model is not None:
            return self._model
        try:
            from faster_whisper import WhisperModel

            self._model = WhisperModel(
                self._model_name,
                device=self._device,
                compute_type=self._compute_type,
            )
        except Exception as exc:
            raise SttProviderError("STT model is unavailable") from exc
        return self._model

    def warmup(self) -> None:
        self._get_model()

    def transcribe(self, audio_path: Path, language: str) -> Transcription:
        model = self._get_model()
        try:
            segments, _ = model.transcribe(
                str(audio_path),
                language=language,
                vad_filter=True,
            )
            text = " ".join(segment.text.strip() for segment in segments if segment.text)
        except Exception as exc:
            raise SttProviderError("STT transcription failed") from exc
        return Transcription(text=text.strip())


class OpenAIWhisperProvider(SttProvider):
    """Synchronous adapter for OpenAI's file transcription endpoint."""

    def __init__(
        self,
        *,
        api_key: str,
        model: str = "whisper-1",
        base_url: str = "https://api.openai.com/v1",
        timeout_seconds: int = 120,
        client: httpx.Client | None = None,
    ) -> None:
        if not api_key:
            raise ValueError("OpenAI STT API key is required")
        if not model:
            raise ValueError("OpenAI STT model is required")
        self._api_key = api_key
        self._model = model
        self._base_url = base_url.rstrip("/")
        self._client = client or httpx.Client(timeout=timeout_seconds)
        self._owns_client = client is None
        self._state_lock = threading.Lock()
        self._closed = False

    def warmup(self) -> None:
        """Validate local readiness without making a billable API request."""
        self._ensure_open()

    @property
    def model(self) -> str:
        """Expose the configured model for deployment diagnostics."""
        return self._model

    def matches_configuration(
        self,
        *,
        api_key: str,
        model: str,
        base_url: str,
    ) -> bool:
        """Confirm the factory preserved deployment-critical STT settings."""
        return (
            secrets.compare_digest(self._api_key, api_key)
            and self._model == model
            and self._base_url == base_url.rstrip("/")
        )

    def transcribe(self, audio_path: Path, language: str) -> Transcription:
        self._ensure_open()
        try:
            with audio_path.open("rb") as audio_file:
                response = self._client.post(
                    f"{self._base_url}/audio/transcriptions",
                    headers={"Authorization": f"Bearer {self._api_key}"},
                    data={
                        "model": self._model,
                        "language": language,
                        "response_format": "json",
                    },
                    files={
                        "file": (
                            audio_path.name,
                            audio_file,
                            "audio/wav",
                        )
                    },
                )
        except httpx.TimeoutException as exc:
            raise SttProviderError(
                "OpenAI STT request timed out",
                code="TRANSCRIPTION_TIMEOUT",
                retryable=True,
            ) from exc
        except httpx.HTTPError as exc:
            raise SttProviderError(
                "OpenAI STT request failed",
                code="MODEL_UNAVAILABLE",
                retryable=True,
            ) from exc
        except OSError as exc:
            raise SttProviderError(
                "STT audio could not be read",
                code="AUDIO_DECODE_FAILED",
                retryable=False,
            ) from exc

        self._raise_for_status(response.status_code)
        try:
            payload = response.json()
        except ValueError as exc:
            raise SttProviderError(
                "OpenAI STT returned an invalid response",
                code="PROVIDER_INVALID_RESPONSE",
                retryable=True,
            ) from exc
        if not isinstance(payload, dict) or not isinstance(payload.get("text"), str):
            raise SttProviderError(
                "OpenAI STT response did not contain text",
                code="PROVIDER_INVALID_RESPONSE",
                retryable=True,
            )
        return Transcription(text=payload["text"])

    def close(self) -> None:
        with self._state_lock:
            if self._closed:
                return
            self._closed = True
        if self._owns_client:
            self._client.close()

    def is_ready(self) -> bool:
        with self._state_lock:
            return not self._closed

    def _ensure_open(self) -> None:
        if not self.is_ready():
            raise SttProviderError(
                "OpenAI STT provider is closed",
                code="SERVICE_UNAVAILABLE",
                retryable=True,
            )

    @staticmethod
    def _raise_for_status(status_code: int) -> None:
        if 200 <= status_code < 300:
            return
        if status_code in {401, 403}:
            raise SttProviderError(
                "OpenAI STT authentication failed",
                code="PROVIDER_AUTH_FAILED",
                retryable=False,
            )
        if status_code in {408, 409, 429} or status_code >= 500:
            code = (
                "PROVIDER_RATE_LIMITED"
                if status_code == 429
                else "MODEL_UNAVAILABLE"
            )
            raise SttProviderError(
                "OpenAI STT is temporarily unavailable",
                code=code,
                retryable=True,
            )
        raise SttProviderError(
            "OpenAI STT rejected the request",
            code="PROVIDER_REQUEST_REJECTED",
            retryable=False,
        )


class FakeSttProvider(SttProvider):
    """Deterministic provider used by unit tests and local Kafka E2E."""

    def __init__(
        self,
        text: str = "테스트 음성 변환 결과입니다.",
        error: Exception | None = None,
    ) -> None:
        self.text = text
        self.error = error
        self.call_count = 0
        self.last_language: str | None = None

    def transcribe(self, audio_path: Path, language: str) -> Transcription:
        self.call_count += 1
        self.last_language = language
        if self.error is not None:
            raise self.error
        return Transcription(text=self.text)


@dataclass(frozen=True)
class _ChildProviderConfig:
    model: str
    device: str
    compute_type: str


def _stt_provider_process_main(
    connection: Connection,
    config: _ChildProviderConfig,
) -> None:
    """Own the native Whisper runtime in a replaceable child process."""
    try:
        provider = FasterWhisperProvider(
            model=config.model,
            device=config.device,
            compute_type=config.compute_type,
        )
        provider.warmup()
        connection.send(("ready", None))
        while True:
            command = connection.recv()
            if not command or command[0] == "stop":
                return
            if command[0] != "transcribe":
                connection.send(("error", "Unsupported STT process command"))
                continue
            _, audio_path, language = command
            try:
                transcription = provider.transcribe(
                    Path(audio_path),
                    language,
                )
                connection.send(("result", transcription.text))
            except SttProviderError:
                connection.send(("error", "STT transcription failed"))
            except Exception:
                connection.send(("error", "STT child process failed"))
    except EOFError:
        return
    except Exception:
        try:
            connection.send(("startup_error", "STT model is unavailable"))
        except (BrokenPipeError, EOFError, OSError):
            pass
    finally:
        connection.close()


class ProcessIsolatedSttProvider(SttProvider):
    """Persistent Whisper child process that can be killed on a hard timeout."""

    def __init__(
        self,
        *,
        model: str,
        device: str,
        compute_type: str,
        warmup_timeout_seconds: int,
        _process_target: Any = _stt_provider_process_main,
    ) -> None:
        self._config = _ChildProviderConfig(
            model=model,
            device=device,
            compute_type=compute_type,
        )
        self._warmup_timeout_seconds = warmup_timeout_seconds
        self._process_target = _process_target
        self._context = multiprocessing.get_context("spawn")
        self._state_lock = threading.Lock()
        self._command_lock = threading.Lock()
        self._process: Any | None = None
        self._connection: Connection | None = None
        self._ready = False
        self._closed = False

    def warmup(self) -> None:
        with self._command_lock:
            self._ensure_open()
            if self.is_ready():
                return
            self.abort()
            parent_connection, child_connection = self._context.Pipe()
            process = self._context.Process(
                target=self._process_target,
                args=(child_connection, self._config),
                name="stt-whisper",
                daemon=True,
            )
            try:
                process.start()
            except Exception as exc:
                parent_connection.close()
                child_connection.close()
                raise SttProviderError(
                    "STT model process could not be started"
                ) from exc
            child_connection.close()
            with self._state_lock:
                closed = self._closed
                if not closed:
                    self._process = process
                    self._connection = parent_connection
            if closed:
                parent_connection.close()
                process.terminate()
                process.join(timeout=2)
                raise SttProviderError("STT provider is closed")

            if not parent_connection.poll(self._warmup_timeout_seconds):
                self.abort()
                raise SttProviderError("STT model warm-up timed out")
            try:
                response, detail = parent_connection.recv()
            except (EOFError, OSError) as exc:
                self.abort()
                raise SttProviderError("STT model process exited during warm-up") from exc
            if response != "ready":
                self.abort()
                raise SttProviderError(detail or "STT model warm-up failed")
            with self._state_lock:
                if (
                    not self._closed
                    and self._process is process
                    and process.is_alive()
                ):
                    self._ready = True
            if not self.is_ready():
                self.abort()
                raise SttProviderError("STT model process is unavailable")

    def transcribe(self, audio_path: Path, language: str) -> Transcription:
        self.warmup()
        with self._command_lock:
            with self._state_lock:
                process = self._process
                connection = self._connection
            if process is None or connection is None or not process.is_alive():
                self.abort()
                raise SttProviderError("STT model process is unavailable")
            try:
                connection.send(("transcribe", str(audio_path), language))
                response, detail = connection.recv()
            except (BrokenPipeError, EOFError, OSError) as exc:
                self.abort()
                raise SttProviderError("STT model process exited") from exc
            if response != "result":
                raise SttProviderError(detail or "STT transcription failed")
            return Transcription(text=detail)

    def abort(self) -> None:
        self._dispose(graceful=False)

    def close(self) -> None:
        with self._state_lock:
            self._closed = True
        self._dispose(graceful=True)

    def is_ready(self) -> bool:
        with self._state_lock:
            return bool(
                self._ready
                and self._process is not None
                and self._process.is_alive()
            )

    def _dispose(self, *, graceful: bool) -> None:
        with self._state_lock:
            process = self._process
            connection = self._connection
            self._process = None
            self._connection = None
            self._ready = False

        if connection is not None and graceful:
            try:
                connection.send(("stop",))
            except (BrokenPipeError, EOFError, OSError):
                pass
        if process is not None:
            if graceful:
                process.join(timeout=2)
            if process.is_alive():
                process.terminate()
                process.join(timeout=2)
            if process.is_alive() and hasattr(process, "kill"):
                process.kill()
                process.join(timeout=2)
        if connection is not None:
            connection.close()

    def _ensure_open(self) -> None:
        with self._state_lock:
            if self._closed:
                raise SttProviderError("STT provider is closed")


def create_stt_provider(settings: SttSettings) -> SttProvider:
    if settings.provider == "openai":
        return OpenAIWhisperProvider(
            api_key=settings.openai_api_key,
            model=settings.model,
            base_url=settings.openai_base_url,
            timeout_seconds=settings.openai_timeout_seconds,
        )
    if settings.provider == "faster-whisper":
        return ProcessIsolatedSttProvider(
            model=settings.model,
            device=settings.device,
            compute_type=settings.compute_type,
            warmup_timeout_seconds=settings.warmup_timeout_seconds,
        )
    if settings.provider == "fake":
        return FakeSttProvider(text=settings.fake_text)
    raise ValueError(f"Unsupported STT_PROVIDER: {settings.provider}")
