package com.ssafy.ssabangpalbang.notification.repository;

import com.ssafy.ssabangpalbang.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationCommandRepositoryImpl implements NotificationCommandRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<Long> insertIfAbsent(Notification notification) {
        // is_read, read_at은 스키마 기본값(FALSE, NULL)을 사용한다.
        // H2(MODE=PostgreSQL)가 RETURNING과 충돌 대상 지정
        // ON CONFLICT (col)을 지원하지 않아, 대상 없는 ON CONFLICT DO NOTHING을
        // 쓰고 삽입 행 수로 성공을 판정한 뒤 UNIQUE 키로 id를 조회한다.
        // 이 INSERT에서 충돌 가능한 유일한 제약은 idempotency_key UNIQUE다(id는 생성값).
        int inserted = jdbcTemplate.update(
                """
                        INSERT INTO notification (
                            recipient_id,
                            actor_id,
                            category,
                            type,
                            target_screen,
                            target_id,
                            target_sub_id,
                            title,
                            body,
                            idempotency_key,
                            sent_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """,
                notification.getRecipientId(),
                notification.getActorId(),
                notification.getCategory().name(),
                notification.getType(),
                notification.getTargetScreen(),
                notification.getTargetId(),
                notification.getTargetSubId(),
                notification.getTitle(),
                notification.getBody(),
                notification.getIdempotencyKey(),
                notification.getSentAt()
        );
        if (inserted == 0) {
            return Optional.empty();
        }
        return Optional.of(jdbcTemplate.queryForObject(
                """
                        SELECT id
                        FROM notification
                        WHERE idempotency_key = ?
                        """,
                Long.class,
                notification.getIdempotencyKey()
        ));
    }
}
