package com.ssafy.ssabangpalbang.chatbot.service;

import com.ssafy.ssabangpalbang.chatbot.client.ChatbotAiClient;
import com.ssafy.ssabangpalbang.chatbot.client.ChatbotAiException;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 같은 대화에 질문이 동시에 오면 답변이 2건 생기지 않는지 검증한다.
 * 비관적 잠금이 없으면 두 요청이 모두 existsInProgress=false 를 읽는다.
 * 단위 테스트로는 증명되지 않는다.
 */
@Tag("postgres")
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers
class ChatbotAskConcurrencyPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = buildContainer();

    private static PostgreSQLContainer<?> buildContainer() {
        ImageFromDockerfile image = new ImageFromDockerfile(
                "ssabangpalbang-postgres-test", false
        ).withDockerfile(Path.of("..", "infra", "postgres", "Dockerfile"));
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
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver"
        );
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> 6379);
        registry.add("spring.kafka.bootstrap-servers", () -> "127.0.0.1:9092");
    }

    @Autowired
    private ChatbotAskService askService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 워커가 실제로 AI를 부르지 않게 막는다. */
    @MockitoBean
    private ChatbotAiClient chatbotAiClient;

    private Long memberId;
    private Long apartmentId;
    private Long conversationId;
    private Long otherConversationId;

    @BeforeEach
    void setUp() {
        cleanup();
        seed();
        // 답변이 곧바로 끝나면 두 번째 질문이 409를 안 만난다.
        // 동시 요청 구간 동안 답변이 진행 중인 상태를 유지시킨다.
        when(chatbotAiClient.generateAnswer(any())).thenAnswer(invocation -> {
            Thread.sleep(3_000);
            throw new ChatbotAiException("테스트용 지연 후 실패");
        });
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void c1_같은_대화에_동시_질문하면_하나만_성공하고_PENDING이_1건이다()
            throws Exception {
        List<AtomicReference<Throwable>> errors = runConcurrently(
                conversationId, conversationId
        );

        long failures = errors.stream()
                .filter(error -> error.get() != null)
                .count();
        assertThat(failures)
                .as("하나는 409로 거절돼야 한다")
                .isEqualTo(1);
        Throwable rejected = errors.stream()
                .map(AtomicReference::get)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();
        assertThat(rejected).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) rejected).getErrorCode())
                .isEqualTo(ErrorCode.CHATBOT_RESPONSE_IN_PROGRESS);

        assertThat(pendingCount(conversationId))
                .as("ASSISTANT PENDING 행은 정확히 1건이어야 한다")
                .isEqualTo(1);
        assertThat(userCount(conversationId)).isEqualTo(1);
    }

    @Test
    void c2_다른_대화에_동시_질문하면_둘_다_성공한다() throws Exception {
        List<AtomicReference<Throwable>> errors = runConcurrently(
                conversationId, otherConversationId
        );

        assertThat(errors.stream().allMatch(error -> error.get() == null))
                .as("서로 다른 대화는 경합하지 않는다")
                .isTrue();
        assertThat(pendingCount(conversationId)).isEqualTo(1);
        assertThat(pendingCount(otherConversationId)).isEqualTo(1);
    }

    private List<AtomicReference<Throwable>> runConcurrently(
            Long firstConversationId,
            Long secondConversationId
    ) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        AtomicReference<Throwable> secondError = new AtomicReference<>();

        Future<?> first = pool.submit(
                () -> ask(start, firstConversationId, "교통 어때요?", firstError)
        );
        Future<?> second = pool.submit(
                () -> ask(start, secondConversationId, "주차 어때요?", secondError)
        );

        start.countDown();
        first.get(30, TimeUnit.SECONDS);
        second.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        return List.of(firstError, secondError);
    }

    private void ask(
            CountDownLatch start,
            Long targetConversationId,
            String content,
            AtomicReference<Throwable> error
    ) {
        try {
            start.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            error.set(exception);
            return;
        }
        try {
            askService.ask(
                    apartmentId, targetConversationId, memberId, content
            );
        } catch (Throwable throwable) {
            error.set(throwable);
        }
    }

    private int pendingCount(Long targetConversationId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT count(*) FROM chatbot_message
                        WHERE conversation_id = ?
                          AND role = 'ASSISTANT'
                          AND status IN ('PENDING', 'PROCESSING')
                        """,
                Integer.class,
                targetConversationId
        );
        return count == null ? 0 : count;
    }

    private int userCount(Long targetConversationId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT count(*) FROM chatbot_message
                        WHERE conversation_id = ? AND role = 'USER'
                        """,
                Integer.class,
                targetConversationId
        );
        return count == null ? 0 : count;
    }

    private void seed() {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12);
        memberId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO member
                            (email, password_hash, nickname, status)
                        VALUES (?, 'hash', ?, 'ACTIVE')
                        RETURNING id
                        """,
                Long.class,
                "ask-" + suffix + "@example.com",
                "회원" + suffix
        );
        apartmentId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO apartment
                            (complex_code, name, longitude, latitude)
                        VALUES (?, '동시성 테스트 아파트', 127.0, 37.0)
                        RETURNING id
                        """,
                Long.class,
                "C-" + suffix
        );
        conversationId = insertConversation();
        otherConversationId = insertConversation();
    }

    private Long insertConversation() {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO chatbot_conversation
                            (member_id, apartment_id)
                        VALUES (?, ?)
                        RETURNING id
                        """,
                Long.class,
                memberId,
                apartmentId
        );
    }

    private void cleanup() {
        jdbcTemplate.update(
                "DELETE FROM chatbot_message WHERE conversation_id IN "
                        + "(SELECT id FROM chatbot_conversation)"
        );
        jdbcTemplate.update("DELETE FROM chatbot_conversation");
        jdbcTemplate.update(
                "DELETE FROM apartment WHERE complex_code LIKE 'C-%'"
        );
        jdbcTemplate.update(
                "DELETE FROM member WHERE email LIKE 'ask-%@example.com'"
        );
    }
}
