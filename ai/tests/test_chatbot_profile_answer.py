"""Profile-grounded answer signal tests for chatbot slice D."""

from datetime import datetime, timezone

import pytest

from app.providers.web_search_provider import WebSearchResult
from app.rag.search import ApartmentProfile, ReportChunkHit
from app.rag.settings_chatbot import ChatbotSettings, WebSearchSettings
from app.schemas.chatbot import ChatbotAnswerRequest
from app.services.chatbot_answer_service import (
    ChatbotAnswerError,
    ChatbotAnswerService,
    _valid_unique_profile_fields,
)


NOW = datetime(2026, 8, 12, tzinfo=timezone.utc)
PROFILE = ApartmentProfile(
    name="도곡렉슬",
    address=None,
    district_name="강남구",
    dong_name="도곡동",
    household_count=3002,
    completion_year_month=None,
    parking_space_count=4435,
)
HIT = ReportChunkHit(83, "리포트 근거", NOW, 0.81)
WEB = WebSearchResult(
    title="도곡동 재개발",
    snippet="강남구 도곡동 재개발 정보",
    url="https://example.com/redevelopment",
    domain="example.com",
    retrieved_at=NOW,
)


@pytest.mark.parametrize(
    ("reported", "expected"),
    [
        (
            ["household_count", "parking_space_count"],
            ["household_count", "parking_space_count"],
        ),
        (["completion_year_month"], []),
        (["school_district"], []),
        (["name"], []),
        (["name", "household_count"], ["household_count"]),
        (
            ["household_count", "household_count"],
            ["household_count"],
        ),
        (["HOUSEHOLD_COUNT"], []),
        ([], []),
    ],
)
def test_valid_unique_profile_fields(reported, expected) -> None:
    assert _valid_unique_profile_fields(reported, PROFILE) == expected


def test_profile_fields_are_invalid_without_profile() -> None:
    assert _valid_unique_profile_fields(["household_count"], None) == []


def test_blank_profile_string_is_not_a_valid_value() -> None:
    profile = ApartmentProfile(
        name="도곡렉슬",
        address=" ",
        district_name="강남구",
        dong_name="도곡동",
        household_count=3002,
        completion_year_month=None,
        parking_space_count=4435,
    )

    assert _valid_unique_profile_fields(["address"], profile) == []


class SequenceProvider:
    def __init__(self, *payloads: dict) -> None:
        self.payloads = list(payloads)
        self.call_count = 0

    async def complete_json(self, system_prompt: str, user_prompt: str) -> dict:
        payload = self.payloads[self.call_count]
        self.call_count += 1
        return payload


class RewriteProvider:
    def __init__(self) -> None:
        self.call_count = 0

    async def complete_json(self, system_prompt: str, user_prompt: str) -> dict:
        self.call_count += 1
        return {"query": "강남구 도곡동 재개발"}


class WebProvider:
    def __init__(self, results: list[WebSearchResult]) -> None:
        self.results = results
        self.call_count = 0

    async def search(self, query: str, top_k: int) -> list[WebSearchResult]:
        self.call_count += 1
        return self.results


def _request() -> ChatbotAnswerRequest:
    return ChatbotAnswerRequest(
        apartmentId=224,
        apartmentName="도곡렉슬",
        question="여기 몇 세대야?",
    )


def _service(
    *payloads: dict,
    profile: ApartmentProfile | None = PROFILE,
    has_reports: bool = True,
    hits: list[ReportChunkHit] | None = None,
    web_results: list[WebSearchResult] | None = None,
    lazy_enabled: bool = True,
) -> tuple[
    ChatbotAnswerService,
    SequenceProvider,
    RewriteProvider,
    WebProvider,
    dict[str, int],
]:
    answer = SequenceProvider(*payloads)
    rewrite = RewriteProvider()
    web = WebProvider(web_results or [])
    calls = {"connection": 0, "retrieve": 0}

    def connection_factory():
        calls["connection"] += 1
        raise AssertionError("stubbed request must not open a DB connection")

    service = ChatbotAnswerService(
        provider=answer,
        rewrite_provider=rewrite,
        embedder=object(),
        connection_factory=connection_factory,
        settings=ChatbotSettings(
            lazy_web_enabled=lazy_enabled,
            web_result_filter_enabled=False,
        ),
        web_search_provider=web,
        web_settings=WebSearchSettings(),
    )
    service._fetch_profile = lambda request: (profile, has_reports)

    def retrieve(request: ChatbotAnswerRequest) -> list[ReportChunkHit]:
        calls["retrieve"] += 1
        return list(hits or [HIT])

    service._retrieve = retrieve
    return service, answer, rewrite, web, calls


@pytest.mark.asyncio
async def test_valid_profile_signal_returns_first_none_response() -> None:
    service, answer, rewrite, web, calls = _service(
        {
            "answer": "도곡렉슬은 3002세대입니다.",
            "usedSources": [],
            "usedProfile": ["household_count"],
        }
    )

    response = await service.answer(_request())

    assert response.answer == "도곡렉슬은 3002세대입니다."
    assert response.basisType == "NONE"
    assert response.sources == []
    assert answer.call_count == 1
    assert rewrite.call_count == 0
    assert web.call_count == 0
    assert calls == {"connection": 0, "retrieve": 1}


@pytest.mark.asyncio
async def test_empty_profile_signal_keeps_lazy_web_path() -> None:
    service, answer, rewrite, web, _ = _service(
        {
            "answer": "리포트에서는 확인되지 않습니다.",
            "usedSources": [],
            "usedProfile": [],
        },
        {
            "answer": "웹에서 확인했습니다.",
            "usedSources": [2],
            "usedProfile": [],
        },
        web_results=[WEB],
    )

    response = await service.answer(_request())

    assert response.basisType == "WEB"
    assert answer.call_count == 2
    assert rewrite.call_count == 1
    assert web.call_count == 1


@pytest.mark.asyncio
async def test_false_profile_signal_falls_through_without_error() -> None:
    service, answer, rewrite, web, _ = _service(
        {
            "answer": "학교 정보는 확인되지 않습니다.",
            "usedSources": [],
            "usedProfile": ["school_district"],
        },
        {
            "answer": "웹에서 확인했습니다.",
            "usedSources": [2],
            "usedProfile": [],
        },
        web_results=[WEB],
    )

    response = await service.answer(_request())

    assert response.basisType == "WEB"
    assert answer.call_count == 2
    assert rewrite.call_count == 1
    assert web.call_count == 1


@pytest.mark.asyncio
async def test_report_citation_takes_priority_over_profile_signal() -> None:
    service, answer, rewrite, web, _ = _service(
        {
            "answer": "리포트와 기본 정보로 답했습니다.",
            "usedSources": [1],
            "usedProfile": ["household_count"],
        }
    )

    response = await service.answer(_request())

    assert response.basisType == "REPORT"
    assert answer.call_count == 1
    assert rewrite.call_count == 0
    assert web.call_count == 0


@pytest.mark.asyncio
async def test_missing_used_profile_defaults_to_lazy_web_path() -> None:
    service, answer, rewrite, web, _ = _service(
        {"answer": "확인되지 않습니다.", "usedSources": []},
        {
            "answer": "웹에서 확인했습니다.",
            "usedSources": [2],
            "usedProfile": [],
        },
        web_results=[WEB],
    )

    response = await service.answer(_request())

    assert response.basisType == "WEB"
    assert answer.call_count == 2
    assert rewrite.call_count == 1
    assert web.call_count == 1


@pytest.mark.asyncio
async def test_non_array_used_profile_is_schema_validation_failed() -> None:
    service, _, _, _, _ = _service(
        {
            "answer": "3002세대입니다.",
            "usedSources": [],
            "usedProfile": "세대수",
        }
    )

    with pytest.raises(ChatbotAnswerError) as exc:
        await service.answer(_request())

    assert exc.value.code == "SCHEMA_VALIDATION_FAILED"


@pytest.mark.asyncio
async def test_no_report_documents_do_not_use_profile_signal_branch() -> None:
    service, answer, rewrite, web, calls = _service(
        {
            "answer": "웹 근거 답변입니다.",
            "usedSources": [1],
            "usedProfile": ["household_count"],
        },
        has_reports=False,
        hits=[],
        web_results=[WEB],
    )

    response = await service.answer(_request())

    assert response.basisType == "WEB"
    assert calls["retrieve"] == 0
    assert answer.call_count == 1
    assert rewrite.call_count == 1
    assert web.call_count == 1


@pytest.mark.asyncio
async def test_lazy_disabled_keeps_eager_search_path() -> None:
    service, answer, rewrite, web, _ = _service(
        {
            "answer": "프로필로 답했습니다.",
            "usedSources": [],
            "usedProfile": ["household_count"],
        },
        lazy_enabled=False,
        web_results=[WEB],
    )

    response = await service.answer(_request())

    assert response.basisType == "NONE"
    assert answer.call_count == 1
    assert rewrite.call_count == 1
    assert web.call_count == 1


@pytest.mark.asyncio
async def test_profile_signal_log_contains_only_valid_field_names(caplog) -> None:
    answer_text = "도곡렉슬은 3002세대입니다."
    service, _, _, _, _ = _service(
        {
            "answer": answer_text,
            "usedSources": [],
            "usedProfile": ["name", "household_count", "school_district"],
        }
    )

    with caplog.at_level("INFO"):
        await service.answer(_request())

    assert "household_count" in caplog.text
    assert "school_district" not in caplog.text
    assert answer_text not in caplog.text
    assert "3002" not in caplog.text


class DatabaseCursor:
    def __init__(self, index: int) -> None:
        self.index = index
        self.sql = ""

    def __enter__(self) -> "DatabaseCursor":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def execute(self, sql: str, params: dict) -> None:
        self.sql = sql

    def fetchone(self) -> tuple:
        if "SELECT EXISTS" in self.sql:
            return (True,)
        return (
            PROFILE.name,
            PROFILE.address,
            PROFILE.district_name,
            PROFILE.dong_name,
            PROFILE.household_count,
            PROFILE.completion_year_month,
            PROFILE.parking_space_count,
        )

    def fetchall(self) -> list[tuple]:
        return [(HIT.source_id, HIT.content, HIT.source_at, HIT.similarity)]


class DatabaseConnection:
    def __init__(self) -> None:
        self.cursor_count = 0
        self.closed = False

    def cursor(self) -> DatabaseCursor:
        cursor = DatabaseCursor(self.cursor_count)
        self.cursor_count += 1
        return cursor

    def close(self) -> None:
        self.closed = True


class Embedder:
    def encode_query(self, question: str) -> list[float]:
        return [0.1]


@pytest.mark.asyncio
async def test_profile_signal_opens_no_additional_db_connection() -> None:
    connections: list[DatabaseConnection] = []

    def connection_factory() -> DatabaseConnection:
        connection = DatabaseConnection()
        connections.append(connection)
        return connection

    service = ChatbotAnswerService(
        provider=SequenceProvider(
            {
                "answer": "도곡렉슬은 3002세대입니다.",
                "usedSources": [],
                "usedProfile": ["household_count"],
            }
        ),
        embedder=Embedder(),
        connection_factory=connection_factory,
        settings=ChatbotSettings(),
        web_search_provider=WebProvider([]),
        web_settings=WebSearchSettings(),
    )

    response = await service.answer(_request())

    assert response.basisType == "NONE"
    assert len(connections) == 2
    assert [connection.cursor_count for connection in connections] == [2, 1]
    assert all(connection.closed for connection in connections)
