"""Prompt builder for checklist itemCode selection (AI-002-1)."""

from __future__ import annotations

import json

from app.schemas.checklist_select import ChecklistAiSelectRequest

SYSTEM_PROMPT = """당신은 한국 아파트 임장 체크리스트 선별 전문가입니다.
서버가 제공한 shortlist 후보 안에서만 최종 문항을 고르세요.

규칙:
- 응답은 JSON object 하나만 출력합니다. 설명 문장은 금지합니다.
- 스키마: {"itemCodes":[string,...]}
- itemCodes 배열 순서가 최종 표시 순서입니다.
- shortlist에 없는 itemCode를 만들거나 추가하지 마세요.
- 질문 제목·설명·카테고리를 작성하거나 수정하지 마세요.
- itemCode 중복 금지.
- 최소 20개, 최대 30개(약 25개 권장).
- targetItemCount는 목표 힌트이며, 20~30개 범위 안에서 선택하세요.
- 여러 category와 사용자 우선순위가 균형 있게 포함되도록 고르세요.
- isCommonCore=true인 후보를 최소 1개 반드시 포함하세요.
- categoryCode가 SUM인 후보를 최소 1개 반드시 포함하세요.
- 위 commonCore·SUM 조건은 각각 모두 충족해야 하며, 둘 중 하나만 만족해서는 안 됩니다.
"""


def build_select_user_prompt(request: ChecklistAiSelectRequest) -> str:
    compact_shortlist = [
        {
            "itemCode": item.itemCode,
            "categoryCode": item.categoryCode,
            "title": item.title,
            "priorityTags": item.priorityTags,
            "conditionTags": item.conditionTags,
            "serverScore": item.serverScore,
            "isCommonCore": item.isCommonCore,
        }
        for item in request.shortlist
    ]
    return (
        "선택 조건:\n"
        f"- selectionVersion: {request.selectionVersion}\n"
        f"- targetItemCount: {request.targetItemCount}\n"
        f"- mappedPurpose: {request.mappedPurpose}\n"
        f"- selectedPriorities: {', '.join(request.selectedPriorities)}\n\n"
        "shortlist(JSON):\n"
        f"{json.dumps(compact_shortlist, ensure_ascii=False)}\n\n"
        "위 shortlist에서 itemCode만 골라 20~30개(약 25개 권장) JSON으로 반환하세요.\n"
    )
