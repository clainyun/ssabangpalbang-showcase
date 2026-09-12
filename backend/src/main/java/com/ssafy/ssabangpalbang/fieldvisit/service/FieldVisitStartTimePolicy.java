package com.ssafy.ssabangpalbang.fieldvisit.service;

import java.time.Instant;
import java.util.Objects;

/**
 * 임장 시작 가능 시각 정책.
 *
 * <p>POST start, GET field-visit canStart, StudyDetail canStartFieldVisit가
 * 동일한 경계({@code now >= startAt})를 쓰도록 공통화한다.</p>
 */
public final class FieldVisitStartTimePolicy {

    private FieldVisitStartTimePolicy() {
    }

    public static boolean isStartAtReached(Instant now, Instant startAt) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(startAt, "startAt");
        return !now.isBefore(startAt);
    }
}
