package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;

/**
 * GET 체크리스트 조회처럼 읽기 전용 API에서 사용하는 관대한(lenient) 상태다.
 *
 * <p>세션이 아직 시작되지 않았거나 본인이 아직 참여하지 않은 경우에도 예외를
 * 던지지 않고 {@code session}/{@code participant}를 {@code null}로 담아 반환한다
 * (docs/API.md: "세션이 아직 시작되지 않았으면 오류 대신 checklist = null을
 * 반환할 수 있다").</p>
 */
public record FieldVisitReadStatus(
        FieldSession session,
        FieldParticipant participant,
        boolean readOnly
) {

    public boolean isSessionStarted() {
        return session != null;
    }

    public Long sessionId() {
        return session == null ? null : session.getId();
    }

    public String participantStatusName() {
        return participant == null ? null : participant.getStatus().name();
    }
}
