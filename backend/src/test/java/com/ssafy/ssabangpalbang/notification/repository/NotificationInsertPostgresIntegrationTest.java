package com.ssafy.ssabangpalbang.notification.repository;

import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.entity.NotificationCategory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * idempotency_key UNIQUE 충돌이 호출자의 비즈니스 트랜잭션을
 * abort시키지 않는지(ON CONFLICT DO NOTHING) 실제 PostgreSQL로 검증한다.
 * H2는 {@code ON CONFLICT ... RETURNING} 문법을 증명하지 못한다.
 */
@Tag("postgres")
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(NotificationCommandRepositoryImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationInsertPostgresIntegrationTest {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = buildContainer();

    private static PostgreSQLContainer<?> buildContainer() {
        ImageFromDockerfile image = new ImageFromDockerfile(
                "ssabangpalbang-notification-insert-test",
                false
        ).withDockerfile(Path.of(
                "..",
                "infra",
                "postgres",
                "Dockerfile"
        ));
        String imageId = image.get();
        return new PostgreSQLContainer<>(
                DockerImageName.parse(imageId)
                        .asCompatibleSubstituteFor("postgres")
        )
                .withDatabaseName("ssabangpalbang_test")
                .withUsername("test")
                .withPassword("test");
    }

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );
        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
        registry.add(
                "spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver"
        );
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration"
        );
    }

    @Autowired
    private NotificationCommandRepository notificationCommandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 같은_멱등키_재삽입은_행을_늘리지_않고_빈_값을_반환한다() {
        Long memberId = insertMember();
        String idempotencyKey = uniqueKey();

        Long firstId = notificationCommandRepository
                .insertIfAbsent(notification(memberId, idempotencyKey))
                .orElseThrow();
        assertThat(notificationCommandRepository
                .insertIfAbsent(notification(memberId, idempotencyKey))
        ).isEmpty();

        assertThat(firstId).isNotNull();
        assertThat(notificationCount(idempotencyKey)).isEqualTo(1L);
    }

    @Test
    void 멱등키_충돌이_비즈니스_트랜잭션을_abort시키지_않는다() {
        Long memberId = insertMember();
        String idempotencyKey = uniqueKey();
        notificationCommandRepository
                .insertIfAbsent(notification(memberId, idempotencyKey))
                .orElseThrow();
        String newNickname = "tx-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            // 비즈니스 쓰기(회원 닉네임 변경)와 같은 트랜잭션에서 충돌 삽입이 일어난다.
            jdbcTemplate.update(
                    "UPDATE member SET nickname = ? WHERE id = ?",
                    newNickname,
                    memberId
            );
            assertThat(notificationCommandRepository
                    .insertIfAbsent(notification(memberId, idempotencyKey))
            ).isEmpty();
            // 종전 saveAndFlush + catch 방식이라면 이 시점에 트랜잭션이
            // abort 상태여서 아래 추가 쓰기와 커밋이 모두 실패했다.
            jdbcTemplate.update(
                    "UPDATE member SET service_notification_agreed = TRUE WHERE id = ?",
                    memberId
            );
        });

        assertThat(jdbcTemplate.queryForObject(
                "SELECT nickname FROM member WHERE id = ?",
                String.class,
                memberId
        )).isEqualTo(newNickname);
        assertThat(notificationCount(idempotencyKey)).isEqualTo(1L);
    }

    @Test
    void 이후_실패는_삽입된_알림도_함께_롤백한다() {
        Long memberId = insertMember();
        String idempotencyKey = uniqueKey();
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    notificationCommandRepository
                            .insertIfAbsent(notification(memberId, idempotencyKey))
                            .orElseThrow();
                    throw new IllegalStateException("business failure");
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(notificationCount(idempotencyKey)).isZero();
    }

    private Notification notification(Long memberId, String idempotencyKey) {
        return Notification.create(
                memberId,
                null,
                NotificationCategory.STUDY,
                "STUDY_CANCELED",
                "STUDY_DETAIL",
                10L,
                null,
                "스터디가 취소되었습니다.",
                "'검증 스터디' 스터디가 취소되었습니다.",
                idempotencyKey,
                OffsetDateTime.now(SEOUL_ZONE_ID)
        );
    }

    private String uniqueKey() {
        return "STUDY_CANCELED:test:" + UUID.randomUUID();
    }

    private Long insertMember() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO member (email, nickname)
                        VALUES (?, ?)
                        RETURNING id
                        """,
                Long.class,
                "notification-" + suffix + "@example.com",
                "noti-" + suffix
        );
    }

    private long notificationCount(String idempotencyKey) {
        Long count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM notification
                        WHERE idempotency_key = ?
                        """,
                Long.class,
                idempotencyKey
        );
        return count == null ? 0L : count;
    }
}
