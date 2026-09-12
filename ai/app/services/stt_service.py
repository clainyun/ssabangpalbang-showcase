"""STT orchestration: private audio download, normalization, and transcription."""

from __future__ import annotations

import asyncio
import contextlib
import re
import tempfile
import threading
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from app.providers.audio_store import (
    AudioDecodeError,
    AudioDownloadError,
    AudioNormalizer,
    AudioNotFoundError,
    AudioStore,
)
from app.providers.stt_provider import SttProvider, SttProviderError
from app.schemas.stt import SttRequest


class SttProcessingError(Exception):
    """Safe, contract-ready STT failure."""

    def __init__(self, code: str, reason: str, *, retryable: bool) -> None:
        super().__init__(reason)
        self.code = code
        self.reason = reason[:500]
        self.retryable = retryable


class SttService:
    def __init__(
        self,
        *,
        audio_store: AudioStore,
        normalizer: AudioNormalizer,
        provider: SttProvider,
        timeout_seconds: int = 600,
        max_concurrency: int = 1,
    ) -> None:
        self._audio_store = audio_store
        self._normalizer = normalizer
        self._provider = provider
        self._timeout_seconds = timeout_seconds
        self._closed = threading.Event()
        self._executor = ThreadPoolExecutor(
            max_workers=max_concurrency,
            thread_name_prefix="stt-worker",
        )

    async def transcribe(self, request: SttRequest) -> str:
        """Run all blocking media/model work outside FastAPI's event loop."""
        if self._closed.is_set():
            raise SttProcessingError(
                "SERVICE_UNAVAILABLE",
                "STT service is shutting down.",
                retryable=True,
            )
        if not request.contentType.lower().startswith("audio/"):
            raise SttProcessingError(
                "UNSUPPORTED_AUDIO",
                "지원하지 않는 음성 형식입니다.",
                retryable=False,
            )

        loop = asyncio.get_running_loop()
        cancelled = threading.Event()
        future = loop.run_in_executor(
            self._executor,
            self._transcribe_sync,
            request,
            cancelled,
        )
        try:
            return await asyncio.wait_for(future, timeout=self._timeout_seconds)
        except asyncio.CancelledError:
            cancelled.set()
            with contextlib.suppress(Exception):
                await asyncio.to_thread(self._provider.abort)
            raise
        except TimeoutError as exc:
            cancelled.set()
            with contextlib.suppress(Exception):
                await asyncio.to_thread(self._provider.abort)
            raise SttProcessingError(
                "TRANSCRIPTION_TIMEOUT",
                "음성 변환 시간이 제한을 초과했습니다.",
                retryable=True,
            ) from exc
        except SttProcessingError:
            raise
        except Exception as exc:
            raise SttProcessingError(
                "INTERNAL_ERROR",
                "음성 변환 중 일시적인 오류가 발생했습니다.",
                retryable=True,
            ) from exc

    def _transcribe_sync(
        self,
        request: SttRequest,
        cancelled: threading.Event,
    ) -> str:
        suffix = self._safe_suffix(request.objectKey)
        try:
            self._raise_if_cancelled(cancelled)
            with tempfile.TemporaryDirectory(prefix="stt-") as directory:
                workdir = Path(directory)
                source = workdir / f"source{suffix}"
                normalized = workdir / "normalized.wav"
                self._audio_store.download(request.objectKey, source)
                self._raise_if_cancelled(cancelled)
                audio_path = self._normalizer.normalize(source, normalized)
                self._raise_if_cancelled(cancelled)
                language = request.language.split("-", 1)[0].lower()
                result = self._provider.transcribe(audio_path, language)
                self._raise_if_cancelled(cancelled)
                text = re.sub(r"\s+", " ", result.text).strip()
                if not text:
                    raise SttProcessingError(
                        "EMPTY_TRANSCRIPT",
                        "음성에서 인식 가능한 발화를 찾지 못했습니다.",
                        retryable=False,
                    )
                return text
        except AudioNotFoundError as exc:
            raise SttProcessingError(
                "AUDIO_NOT_FOUND",
                "음성 원본을 찾을 수 없습니다.",
                retryable=False,
            ) from exc
        except AudioDownloadError as exc:
            raise SttProcessingError(
                "AUDIO_DOWNLOAD_FAILED",
                "음성 원본을 불러오지 못했습니다.",
                retryable=True,
            ) from exc
        except AudioDecodeError as exc:
            raise SttProcessingError(
                "AUDIO_DECODE_FAILED",
                "음성 파일을 해석할 수 없습니다.",
                retryable=False,
            ) from exc
        except SttProviderError as exc:
            raise SttProcessingError(
                exc.code,
                "음성 변환 서비스를 사용할 수 없습니다.",
                retryable=exc.retryable,
            ) from exc

    def _raise_if_cancelled(self, cancelled: threading.Event) -> None:
        if cancelled.is_set() or self._closed.is_set():
            raise SttProcessingError(
                "TRANSCRIPTION_ABORTED",
                "STT processing was stopped.",
                retryable=True,
            )

    @staticmethod
    def _safe_suffix(object_key: str) -> str:
        suffix = Path(object_key).suffix.lower()
        if re.fullmatch(r"\.[a-z0-9]{1,10}", suffix):
            return suffix
        return ".audio"

    def warmup(self) -> None:
        self._provider.warmup()

    def is_ready(self) -> bool:
        return self._provider.is_ready()

    def close(self) -> None:
        self._closed.set()
        self._executor.shutdown(wait=False, cancel_futures=True)
        self._provider.close()
