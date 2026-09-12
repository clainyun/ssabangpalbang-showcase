package com.ssafy.ssabangpalbang.fieldvisit.client;

import java.util.List;

/**
 * FastAPI → Spring 체크리스트 생성 응답의 내부 계약이다.
 *
 * <p>이 레코드가 성공적으로 역직렬화됐다고 해서 바로 신뢰하지 않는다.
 * {@link ChecklistAiResponseValidator}가 items 존재·title 공백·displayOrder
 * 중복 등 추가 불변식을 한 번 더 검증한 뒤에만 저장에 사용한다(FastAPI 쪽
 * Pydantic 검증과 별개로 Spring 쪽에서도 방어적으로 검증한다).</p>
 */
public record ChecklistAiGenerateResponse(
        List<Item> items
) {

    public record Item(
            String category,
            String title,
            String subtitle,
            Integer displayOrder,
            // 항목별 예시 메모. FastAPI가 채워 보내며, 누락 시 null 이어도 계약을 깨지 않는다.
            String example
    ) {
    }
}
