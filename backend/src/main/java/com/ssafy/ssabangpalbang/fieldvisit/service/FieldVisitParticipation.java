package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;

/**
 * "진행 중" 여부까지 엄격하게 검증된 임장 세션·참여자 조합이다.
 *
 * <p>체크리스트 생성(POST generate)처럼 쓰기 API에서 사용한다. 이 레코드가
 * 만들어졌다는 것 자체가 세션과 참여자가 모두 {@code IN_PROGRESS}임을 의미한다.</p>
 */
public record FieldVisitParticipation(
        FieldSession session,
        FieldParticipant participant
) {
}
