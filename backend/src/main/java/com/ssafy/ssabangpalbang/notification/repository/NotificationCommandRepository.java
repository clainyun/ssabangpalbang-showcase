package com.ssafy.ssabangpalbang.notification.repository;

import com.ssafy.ssabangpalbang.notification.entity.Notification;

import java.util.Optional;

public interface NotificationCommandRepository {

    /**
     * idempotency_key가 이미 존재하면 아무것도 하지 않고 빈 값을 반환하고,
     * 새로 삽입되면 생성된 알림 id를 반환한다.
     *
     * <p>{@code INSERT ... ON CONFLICT (idempotency_key) DO NOTHING}을 사용하므로
     * 호출자의 트랜잭션에 참여해도 UNIQUE 충돌이 트랜잭션을 abort시키지 않는다.
     */
    Optional<Long> insertIfAbsent(Notification notification);
}
