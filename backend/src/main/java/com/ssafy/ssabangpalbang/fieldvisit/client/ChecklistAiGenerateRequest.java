package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistPersonalizationInput;

/**
 * Spring → FastAPI 체크리스트 생성 요청의 내부 계약이다.
 *
 * <p>필드 범위는 사용자 확정 사항으로 승인된 "3. AI 입력 데이터 범위"를 그대로
 * 따른다. 실제 URI·타임아웃·재시도·LLM 공급자는 이 커밋에서 정하지 않는다
 * (별도 승인 대상).</p>
 */
public record ChecklistAiGenerateRequest(
        ChecklistPersonalizationInput personalization
) {
}
