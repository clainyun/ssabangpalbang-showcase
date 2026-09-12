package com.ssafy.ssabangpalbang.chat.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * chat_read_status의 원자적 upsert(INSERT ... ON CONFLICT (study_id, member_id)
 * DO UPDATE ... GREATEST(...))를 실제 PostgreSQL 위에서 검증한다.
 *
 * <p>H2로 약화하지 않고 {@code infra/postgres/Dockerfile}(로컬 docker-compose와 동일한 이미지)을
 * Testcontainers로 빌드해 실행한다. Docker daemon이 필요하므로 기본 {@code test} 태스크에서는
 * {@code excludeTags 'postgres'}로 제외되고, 로컬에서 {@code ./gradlew postgresTest}로만 실행한다.</p>
 *
 * <p>chat_read_status 스키마(V1)는 변경하지 않는다. last_read_message_id 컬럼을
 * 추가하지 않고, 기존 UNIQUE(study_id, member_id) 제약만을 대상으로
 * upsert 쿼리의 원자성과 GREATEST 동작을 검증한다.</p>
 */
@Tag("postgres")
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ChatReadStatusPostgresUpsertTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = buildContainer();

    private static PostgreSQLContainer<?> buildContainer() {
        ImageFromDockerfile image = new ImageFromDockerfile(
                "ssabangpalbang-postgres-test", false
        ).withDockerfile(Path.of("..", "infra", "postgres", "Dockerfile"));
        String imageId = image.get();
        return new PostgreSQLContainer<>(
                DockerImageName.parse(imageId).asCompatibleSubstituteFor("postgres")
        )
                .withDatabaseName("ssabangpalbang_test")
                .withUsername("test")
                .withPassword("test");
    }

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private ChatReadStatusRepository chatReadStatusRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long studyId;
    private Long memberId;

    /**
     * 매 테스트마다 UUID 기반의 고유한 apartment.complex_code / member.email /
     * member.nickname을 사용한다. 동시성 테스트(아래)는 기본 테스트 트랜잭션을
     * {@code Propagation.NOT_SUPPORTED}로 비활성화하므로 그 안에서 생성되는
     * 데이터는 롤백되지 않고 실제로 커밋된다. 값을 고정 리터럴로 두면 이후
     * 실행되는 다른 테스트의 동일 리터럴 INSERT가 UNIQUE 제약을 위반할 수 있으므로,
     * 모든 테스트가 매번 새 UUID로 완전히 독립된 행을 만들도록 한다.
     */
    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        String complexCode = "RS-" + suffix;
        jdbcTemplate.update(
                "INSERT INTO apartment (complex_code, name, longitude, latitude) "
                        + "VALUES (?, '테스트 아파트', 127.0, 37.5)",
                complexCode
        );
        Long apartmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM apartment WHERE complex_code = ?", Long.class, complexCode
        );

        memberId = insertMember(
                "rs-" + suffix + "@example.com",
                "rs-" + suffix.substring(0, 8)
        );

        jdbcTemplate.update(
                "INSERT INTO study (apartment_id, leader_id, goal, capacity) "
                        + "VALUES (?, ?, 'goal', 5)",
                apartmentId, memberId
        );
        studyId = jdbcTemplate.queryForObject(
                "SELECT id FROM study WHERE leader_id = ?", Long.class, memberId
        );
    }

    private Long insertMember(String email, String nickname) {
        jdbcTemplate.update(
                "INSERT INTO member (email, nickname, age_group_public_agreed, "
                        + "service_notification_agreed, ad_notification_agreed) "
                        + "VALUES (?, ?, false, true, false)",
                email, nickname
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM member WHERE email = ?", Long.class, email
        );
    }

    @Test
    void 읽음_상태가_없을_때_upsert하면_행이_1개_생성된다() {
        Instant lastReadAt = Instant.parse("2026-07-22T04:40:00Z");

        chatReadStatusRepository.upsertLastReadAt(studyId, memberId, lastReadAt);

        assertThat(countRows(studyId, memberId)).isEqualTo(1L);
        assertThat(readLastReadAt(studyId, memberId)).isEqualTo(lastReadAt);
    }

    @Test
    void 같은_요청을_반복해도_예외_없이_행이_1개만_유지된다() {
        Instant lastReadAt = Instant.parse("2026-07-22T04:40:00Z");

        chatReadStatusRepository.upsertLastReadAt(studyId, memberId, lastReadAt);
        chatReadStatusRepository.upsertLastReadAt(studyId, memberId, lastReadAt);
        chatReadStatusRepository.upsertLastReadAt(studyId, memberId, lastReadAt);

        assertThat(countRows(studyId, memberId)).isEqualTo(1L);
        assertThat(readLastReadAt(studyId, memberId)).isEqualTo(lastReadAt);
    }

    @Test
    void 더_최신_시각_저장_후_더_과거_시각으로_요청해도_최신_시각이_유지된다() {
        Instant newer = Instant.parse("2026-07-22T10:00:00Z");
        Instant older = Instant.parse("2026-07-22T04:40:00Z");

        chatReadStatusRepository.upsertLastReadAt(studyId, memberId, newer);
        chatReadStatusRepository.upsertLastReadAt(studyId, memberId, older);

        assertThat(countRows(studyId, memberId)).isEqualTo(1L);
        assertThat(readLastReadAt(studyId, memberId)).isEqualTo(newer);
    }

    @Test
    void 더_과거_시각_저장_후_더_최신_시각을_요청하면_최신_시각으로_갱신된다() {
        Instant older = Instant.parse("2026-07-22T04:40:00Z");
        Instant newer = Instant.parse("2026-07-22T10:00:00Z");

        chatReadStatusRepository.upsertLastReadAt(studyId, memberId, older);
        chatReadStatusRepository.upsertLastReadAt(studyId, memberId, newer);

        assertThat(countRows(studyId, memberId)).isEqualTo(1L);
        assertThat(readLastReadAt(studyId, memberId)).isEqualTo(newer);
    }

    /**
     * 동일 (studyId, memberId)에 대한 두 upsert 요청을 서로 다른 스레드·커넥션·
     * 트랜잭션에서 동시에 실행해도 UNIQUE 위반 예외 없이 모두 끝나고, 행이 1개만
     * 유지되며, 최종 값이 두 요청 중 더 늦은 시각인지 검증한다.
     *
     * <p>{@code @DataJpaTest}는 기본적으로 각 테스트를 하나의 트랜잭션으로 감싸고
     * 종료 후 롤백한다. 이 방식으로는 두 스레드가 같은(main 스레드에 바인딩된)
     * 트랜잭션·커넥션을 공유하게 되어 진짜 동시 접근을 재현할 수 없다. 이 테스트만
     * {@code Propagation.NOT_SUPPORTED}로 그 기본 트랜잭션을 비활성화해, 아래 두
     * 스레드가 각자 독립된 커넥션과 트랜잭션으로 실제 동시 upsert를 실행하게 한다.</p>
     *
     * <p>커스텀 {@code @Modifying} 쿼리 메서드는 (운영 코드의 {@code ChatRestService}처럼)
     * 스스로를 감싸는 트랜잭션이 없으면 {@code TransactionRequiredException}이 발생하므로,
     * 각 스레드에서 {@link TransactionTemplate}으로 독립된 트랜잭션을 직접 열어 그 안에서
     * upsert를 실행한다.</p>
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 동시_upsert_요청도_오류_없이_끝나고_행이_1개만_유지된다() throws Exception {
        Instant earlier = Instant.parse("2026-07-22T04:40:00Z");
        Instant later = Instant.parse("2026-07-22T10:00:00Z");
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> {
                readyLatch.countDown();
                await(startLatch);
                transactionTemplate.executeWithoutResult(status ->
                        chatReadStatusRepository.upsertLastReadAt(studyId, memberId, earlier));
            });
            Future<?> second = executor.submit(() -> {
                readyLatch.countDown();
                await(startLatch);
                transactionTemplate.executeWithoutResult(status ->
                        chatReadStatusRepository.upsertLastReadAt(studyId, memberId, later));
            });

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            // get()이 예외를 던지면(예: UNIQUE 위반) 테스트가 즉시 실패한다.
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        assertThat(countRows(studyId, memberId)).isEqualTo(1L);
        assertThat(readLastReadAt(studyId, memberId)).isEqualTo(later);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private long countRows(Long studyId, Long memberId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_read_status WHERE study_id = ? AND member_id = ?",
                Long.class, studyId, memberId
        );
        return count == null ? 0L : count;
    }

    private Instant readLastReadAt(Long studyId, Long memberId) {
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT last_read_at FROM chat_read_status WHERE study_id = ? AND member_id = ?",
                Timestamp.class, studyId, memberId
        );
        return timestamp.toInstant();
    }
}
