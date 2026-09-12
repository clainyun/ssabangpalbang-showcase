"""Unified report/web evidence prompt builder for chatbot answers."""

from __future__ import annotations

from collections.abc import Sequence

from app.providers.web_search_provider import WebSearchResult, strip_html
from app.rag.search import ApartmentProfile, ReportChunkHit


SYSTEM_PROMPT = """당신은 한국 아파트 정보를 근거에 따라 답하는 도우미입니다.

규칙:
- 응답은 JSON object 하나만 출력합니다. 스키마: {"answer": string, "usedSources": number[], "usedProfile": string[]}
- <근거> 안의 내용과 [아파트 기본 정보]만 사용합니다. 추측하거나 일반 상식을 덧붙이지 않습니다.
- 답변에 실제로 사용한 근거 번호만 usedSources에 넣습니다.
- 사용하지 않은 근거의 내용은 답변에 언급하지 않습니다.
- 근거를 요약하지 말고 질문에만 답합니다.
- 임장 리포트 근거로 답할 수 있으면 리포트만 사용합니다.
- 리포트로 답할 수 없을 때만 웹 근거를 사용합니다.
- 한 답변에서 리포트와 웹을 함께 인용하지 않습니다.
- 근거가 부족하면 지어내지 말고 확인되지 않았다고 답하고 usedSources를 빈 배열로 둡니다.
- 아파트 기본 정보만으로 답할 수 있으면 그것으로 답하고 usedSources를 빈 배열로 둡니다.
- [아파트 기본 정보]에서 실제로 사용한 항목의 이름만 usedProfile에 넣습니다. 사용하지 않았으면 빈 배열로 둡니다.
- 쓸 수 있는 이름은 address, district_name, dong_name, household_count, completion_year_month, parking_space_count뿐입니다.
- 웹 근거로 시세·교통·개발 정보를 답할 때는 기준 시점과 불확실성을 함께 밝힙니다.
- 질문의 단지 자체를 특정할 수 없으면 같은 구·동의 정보로 답할 수 있습니다. 그때는 그 정보가 단지가 아니라 어느 지역 기준인지 답변에 밝힙니다.
- 한국어로 답합니다. 근거 하나로 충분하면 한 문장으로 끝내고, 길어도 5문장을 넘기지 않습니다.
"""

SEARCH_QUERY_SYSTEM_PROMPT = """당신은 한국 아파트 웹 검색 질의를 만드는 도우미입니다.

규칙:
- JSON object 하나만 출력합니다. 스키마: {"query": string}
- 질문의 핵심 명사와 주제어만 남기고 3~8개 단어의 검색 키워드로 만듭니다.
- 그 뭐야, 뭐있나, 확인해봐, 알려줘, 어때 같은 구어체 조사·감탄사·요청어는 버립니다.
- 구·동 지역명은 항상 포함합니다.
- 단지 단위 주제(주차, 관리비, 세대, 동 배치, 단지 시설)는 아파트명을 포함합니다.
- 지역 단위 주제(재개발, 재건축, 학군, 교통 인프라, 상권, 개발 호재)는 아파트명을 제외합니다.
- 주제가 애매하면 아파트명을 포함합니다.
- 질문에 없는 연도, 가격 등 내용을 지어내지 않습니다.
- 문장이 아닌 한국어 검색 키워드만 만듭니다.
"""


def render_apartment_profile(profile: ApartmentProfile | None) -> str:
    if profile is None:
        return ""

    lines = ["[아파트 기본 정보]", profile.name]
    if profile.address is not None:
        lines.append(f"주소: {profile.address}")
    region = " ".join(
        value
        for value in (profile.district_name, profile.dong_name)
        if value is not None
    )
    if region:
        lines.append(f"지역: {region}")
    if profile.household_count is not None:
        lines.append(f"세대수: {profile.household_count}세대")
    if profile.completion_year_month is not None:
        lines.append(f"준공: {profile.completion_year_month}")
    if profile.parking_space_count is not None:
        lines.append(f"주차: {profile.parking_space_count}대")
    return "\n".join(lines)


def build_user_prompt(
    question: str,
    profile: ApartmentProfile | None,
    report_hits: Sequence[ReportChunkHit],
    web_results: Sequence[WebSearchResult],
) -> str:
    evidence: list[str] = []
    for index, hit in enumerate(report_hits, start=1):
        evidence.append(f"[근거 {index}] (임장 리포트)\n{hit.content}")

    offset = len(report_hits)
    for index, result in enumerate(web_results, start=offset + 1):
        evidence.append(
            f"[근거 {index}] (웹) {strip_html(result.title)} "
            f"({result.domain})\n{strip_html(result.snippet)}"
        )

    sections = []
    profile_block = render_apartment_profile(profile)
    if profile_block:
        sections.append(profile_block)
    evidence_block = "\n\n".join(evidence)
    sections.append(f"<근거>\n{evidence_block}\n</근거>")
    sections.append(f"<질문>\n{question.strip()}\n</질문>")
    return "\n\n".join(sections)


def build_search_query_prompt(
    question: str,
    apartment_name: str | None,
    district_name: str | None,
    dong_name: str | None,
) -> str:
    """Render the profile context used only for search-query rewriting."""
    return "\n".join(
        (
            f"아파트명: {apartment_name or ''}",
            f"구: {district_name or ''}",
            f"동: {dong_name or ''}",
            f"질문: {question.strip()}",
        )
    )


def build_search_query(
    apartment_name: str | None,
    district_name: str | None,
    dong_name: str | None,
    question: str,
) -> str:
    """Put the apartment name first and omit values already present in the question."""
    normalized_question = " ".join(question.split())
    compact_question = "".join(normalized_question.split())
    parts: list[str] = []
    for value in (apartment_name, district_name, dong_name):
        if value is None:
            continue
        normalized_value = " ".join(value.split())
        if not normalized_value:
            continue
        if "".join(normalized_value.split()) in compact_question:
            continue
        parts.append(normalized_value)
    if normalized_question:
        parts.append(normalized_question)
    return " ".join(parts)
