"""Audio download and normalization adapters for STT processing."""

from __future__ import annotations

import subprocess
from abc import ABC, abstractmethod
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse

import httpx

from app.config import SttSettings


class AudioStoreError(Exception):
    """Base error raised by audio object stores."""


class AudioNotFoundError(AudioStoreError):
    """The requested audio object no longer exists."""


class AudioDownloadError(AudioStoreError):
    """The audio object could not be downloaded due to a transient failure."""


class AudioDecodeError(Exception):
    """FFmpeg could not decode the uploaded audio."""


class AudioStore(ABC):
    @abstractmethod
    def download(self, object_key: str, destination: Path) -> None:
        """Download one private audio object to a local temporary path."""


class MediaGatewayAudioStore(AudioStore):
    """Obtain a fresh signed URL from the internal gateway before each download."""

    _MAX_STT_AUDIO_BYTES = 50 * 1024 * 1024

    def __init__(
        self,
        *,
        base_url: str,
        internal_token: str,
        gateway_timeout_seconds: int = 15,
        download_timeout_seconds: int = 120,
        client: httpx.Client | None = None,
    ) -> None:
        if not base_url:
            raise ValueError(
                "MEDIA_GATEWAY_BASE_URL is required for the media gateway audio store"
            )
        if not internal_token:
            raise ValueError(
                "MEDIA_GATEWAY_INTERNAL_TOKEN is required for the media gateway audio store"
            )
        self._base_url = base_url.rstrip("/")
        self._internal_token = internal_token
        self._gateway_timeout_seconds = gateway_timeout_seconds
        self._download_timeout_seconds = download_timeout_seconds
        self._client = client or httpx.Client(follow_redirects=True)
        self._owns_client = client is None

    def download(self, object_key: str, destination: Path) -> None:
        if not object_key:
            raise AudioNotFoundError("Audio object key is empty")
        download_url = self._issue_download_url(object_key)
        try:
            with self._client.stream(
                "GET",
                download_url,
                timeout=self._download_timeout_seconds,
            ) as response:
                if response.status_code == 404:
                    raise AudioNotFoundError("Audio object was not found")
                response.raise_for_status()
                content_length = response.headers.get("Content-Length")
                if content_length is not None:
                    try:
                        declared_bytes = int(content_length)
                    except ValueError as exc:
                        raise AudioDownloadError(
                            "Audio object returned an invalid size"
                        ) from exc
                    if declared_bytes > self._MAX_STT_AUDIO_BYTES:
                        raise AudioDownloadError(
                            "Audio object exceeds the maximum size"
                        )
                downloaded_bytes = 0
                with destination.open("wb") as output:
                    for chunk in response.iter_bytes():
                        downloaded_bytes += len(chunk)
                        if downloaded_bytes > self._MAX_STT_AUDIO_BYTES:
                            raise AudioDownloadError(
                                "Audio object exceeds the maximum size"
                            )
                        output.write(chunk)
        except AudioNotFoundError:
            raise
        except AudioDownloadError:
            destination.unlink(missing_ok=True)
            raise
        except (httpx.HTTPError, OSError) as exc:
            destination.unlink(missing_ok=True)
            raise AudioDownloadError("Audio object download failed") from exc

    def close(self) -> None:
        if self._owns_client:
            self._client.close()

    def _issue_download_url(self, object_key: str) -> str:
        try:
            response = self._client.post(
                f"{self._base_url}/media/download-url",
                headers={
                    "Authorization": f"Bearer {self._internal_token}",
                    "Content-Type": "application/json",
                },
                json={"fileUsage": "STT_AUDIO", "s3Key": object_key},
                timeout=self._gateway_timeout_seconds,
            )
            if response.status_code == 404:
                raise AudioNotFoundError("Audio object was not found")
            response.raise_for_status()
            payload = response.json()
            if not isinstance(payload, dict):
                raise AudioDownloadError(
                    "Media gateway returned an invalid response"
                )
            if (
                payload.get("fileUsage") != "STT_AUDIO"
                or payload.get("s3Key") != object_key
            ):
                raise AudioDownloadError(
                    "Media gateway returned a mismatched audio object"
                )
            expires_at = payload.get("expiresAt")
            if not isinstance(expires_at, str):
                raise AudioDownloadError(
                    "Media gateway returned no URL expiration"
                )
            try:
                expiration = datetime.fromisoformat(
                    expires_at.replace("Z", "+00:00")
                )
            except ValueError as exc:
                raise AudioDownloadError(
                    "Media gateway returned an invalid URL expiration"
                ) from exc
            if (
                expiration.tzinfo is None
                or expiration <= datetime.now(timezone.utc)
            ):
                raise AudioDownloadError(
                    "Media gateway returned an expired download URL"
                )
            download_url = payload.get("downloadUrl")
            if not isinstance(download_url, str) or not download_url:
                raise AudioDownloadError(
                    "Media gateway returned no download URL"
                )
            parsed_url = urlparse(download_url)
            if parsed_url.scheme != "https" or not parsed_url.hostname:
                raise AudioDownloadError(
                    "Media gateway returned an unsafe download URL"
                )
            return download_url
        except AudioNotFoundError:
            raise
        except AudioDownloadError:
            raise
        except (httpx.HTTPError, ValueError, AttributeError) as exc:
            raise AudioDownloadError(
                "Media gateway download URL request failed"
            ) from exc


class FakeAudioStore(AudioStore):
    """Deterministic store used by unit tests and local Kafka E2E."""

    def __init__(self, payload: bytes = b"fake-audio") -> None:
        self.payload = payload
        self.download_count = 0

    def download(self, object_key: str, destination: Path) -> None:
        self.download_count += 1
        destination.write_bytes(self.payload)


class AudioNormalizer(ABC):
    @abstractmethod
    def normalize(self, source: Path, destination: Path) -> Path:
        """Return a 16 kHz mono audio path suitable for transcription."""


class FfmpegAudioNormalizer(AudioNormalizer):
    def __init__(self, *, binary: str = "ffmpeg", timeout_seconds: int = 120) -> None:
        self._binary = binary
        self._timeout_seconds = timeout_seconds

    def normalize(self, source: Path, destination: Path) -> Path:
        command = [
            self._binary,
            "-nostdin",
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            str(source),
            "-vn",
            "-ac",
            "1",
            "-ar",
            "16000",
            str(destination),
        ]
        try:
            completed = subprocess.run(
                command,
                check=False,
                capture_output=True,
                timeout=self._timeout_seconds,
            )
        except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
            raise AudioDecodeError("Audio normalization failed") from exc
        if completed.returncode != 0 or not destination.is_file():
            raise AudioDecodeError("Audio normalization failed")
        return destination


class PassthroughAudioNormalizer(AudioNormalizer):
    """Skip FFmpeg for deterministic fake-provider tests."""

    def normalize(self, source: Path, destination: Path) -> Path:
        return source


def create_audio_store(settings: SttSettings) -> AudioStore:
    if settings.audio_store_provider == "media-gateway":
        return MediaGatewayAudioStore(
            base_url=settings.media_gateway_base_url,
            internal_token=settings.media_gateway_internal_token,
            gateway_timeout_seconds=settings.media_gateway_timeout_seconds,
            download_timeout_seconds=settings.media_download_timeout_seconds,
        )
    if settings.audio_store_provider == "fake":
        return FakeAudioStore()
    raise ValueError(
        f"Unsupported AUDIO_STORE_PROVIDER: {settings.audio_store_provider}"
    )


def create_audio_normalizer(settings: SttSettings) -> AudioNormalizer:
    name = settings.audio_normalizer
    if name == "auto":
        name = "passthrough" if settings.provider == "fake" else "ffmpeg"
    if name == "ffmpeg":
        return FfmpegAudioNormalizer(
            binary=settings.ffmpeg_binary,
            timeout_seconds=settings.ffmpeg_timeout_seconds,
        )
    if name == "passthrough":
        return PassthroughAudioNormalizer()
    raise ValueError(f"Unsupported STT_AUDIO_NORMALIZER: {settings.audio_normalizer}")
