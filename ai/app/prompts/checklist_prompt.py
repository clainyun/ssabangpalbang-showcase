"""Checklist generation prompt builder. Preserves priorities order."""

from __future__ import annotations

from app.schemas.checklist import ChecklistPersonalizationInput


SYSTEM_PROMPT = """당신은 한국 아파트 임장(현장 방문) 체크리스트 전문가입니다.
회원의 온보딩 선호와 대상 아파트·스터디 정보를 바탕으로
현장에서 확인할 체크리스트 항목을 JSON으로만 반환하세요.

규칙:
- 응답은 JSON object 하나만 출력합니다. 설명 문장은 금지합니다.
- 스키마: {"items":[{"category":string,"title":string,"subtitle":string|null,"displayOrder":number,"example":string}]}
- category는 1~30자, title은 비어 있을 수 없습니다.
- displayOrder는 1 이상이며 항목마다 유일해야 합니다.
- 항목 수는 8~12개 권장입니다.
- priorities 배열 순서는 사용자 중요도 순서이므로 앞쪽 항목에 더 반영하세요.
- memberPurpose는 회원 개인 목적, studyPurpose는 이번 스터디 공통 목적입니다.
- example은 각 항목마다 반드시 채웁니다. 회원이 현장에서 그 항목을 확인한 뒤 남길 법한
  한 줄 예시 메모로, 1인칭·구체적이며 40자 내외로 작성합니다(그 항목에 딱 맞아야 하며,
  다른 항목에 써도 될 만큼 일반적이면 안 됩니다).
  예: title "역까지 실제 도보 시간" → example "주출입구에서 3번 출구까지 걸어보니 7분 걸렸어요."
"""


def build_user_prompt(personalization: ChecklistPersonalizationInput) -> str:
    member = personalization.member
    apartment = personalization.apartment
    study = personalization.study
    priorities = ", ".join(member.priorities)
    return f"""회원 온보딩:
- memberPurpose: {member.memberPurpose}
- maritalStatus: {member.maritalStatus}
- hasVehicle: {member.hasVehicle}
- hasChildren: {member.hasChildren}
- priorities(중요도 순서): {priorities}
- ageGroup: {member.ageGroup}

대상 아파트:
- apartmentId: {apartment.apartmentId}
- name: {apartment.name}
- address: {apartment.address}
- districtName: {apartment.districtName}
- dongName: {apartment.dongName}
- householdCount: {apartment.householdCount}
- completionYearMonth: {apartment.completionYearMonth}
- parkingSpaceCount: {apartment.parkingSpaceCount}

스터디:
- studyId: {study.studyId}
- studyPurpose: {study.studyPurpose}
- goal: {study.goal}

위 정보를 반영한 임장 체크리스트 JSON을 생성하세요.
"""
