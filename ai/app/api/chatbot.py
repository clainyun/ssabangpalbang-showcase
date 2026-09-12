"""Internal chatbot answer route (AI-009).

HTTP for now; the final deployment has the GPU host consume Kafka instead.
Only this module knows about transport — the service stays reusable either way.
"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, HTTPException

from app.providers.llm_provider import create_llm_provider
from app.rag.config import RagSettings, is_rag_enabled
from app.rag.db import connect
from app.rag.embedder import get_shared_embedder
from app.rag.settings_chatbot import ChatbotSettings
from app.schemas.chatbot import (
    CHATBOT_ANSWER_RESPONSE_SCHEMA,
    CHATBOT_SEARCH_QUERY_SCHEMA,
    ChatbotAnswerRequest,
    ChatbotAnswerResponse,
)
from app.services.chatbot_answer_service import (
    ChatbotAnswerError,
    ChatbotAnswerService,
)

router = APIRouter(prefix="/internal/v1/chatbot", tags=["chatbot"])


def _build_service() -> ChatbotAnswerService:
    rag_settings = RagSettings.from_env()
    rewrite_provider = create_llm_provider(
        CHATBOT_SEARCH_QUERY_SCHEMA,
        purpose="CHATBOT",
    )
    answer_provider = create_llm_provider(
        CHATBOT_ANSWER_RESPONSE_SCHEMA,
        purpose="CHATBOT",
    )
    return ChatbotAnswerService(
        provider=answer_provider,
        rewrite_provider=rewrite_provider,
        embedder=get_shared_embedder(rag_settings),
        connection_factory=lambda: connect(rag_settings),
        settings=ChatbotSettings.from_env(),
    )


@router.post("/answers", response_model=ChatbotAnswerResponse)
async def generate_answer(
    request: ChatbotAnswerRequest,
) -> ChatbotAnswerResponse:
    if not is_rag_enabled():
        raise HTTPException(
            status_code=503,
            detail={
                "code": "RAG_DISABLED",
                "message": "Database-backed chatbot answers are disabled",
            },
        )

    try:
        service = _build_service()
    except ChatbotAnswerError as exc:
        raise _failure(exc) from exc
    except Exception as exc:
        raise HTTPException(
            status_code=502,
            detail={"code": "RAG_DB_FAILED", "message": str(exc)},
        ) from exc

    try:
        return await service.answer(request)
    except ChatbotAnswerError as exc:
        # Spring treats any 5xx as technical AI failure → fallback.
        raise _failure(exc) from exc


def _failure(exc: ChatbotAnswerError) -> HTTPException:
    return HTTPException(
        status_code=502,
        detail={"code": exc.code, "message": exc.message},
    )
