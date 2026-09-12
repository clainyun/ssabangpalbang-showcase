package com.ssafy.ssabangpalbang.chatbot.service;

import com.ssafy.ssabangpalbang.chatbot.client.ChatbotAiAnswerResponse;
import com.ssafy.ssabangpalbang.chatbot.client.ChatbotAiClient;
import com.ssafy.ssabangpalbang.chatbot.client.ChatbotAiException;
import com.ssafy.ssabangpalbang.chatbot.integration.ChatbotAnswerRequestedEvent;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 답변 저장이 <b>실제로 커밋되는지</b> 검증한다.
 *
 * <p>회귀 방지용이다. 트랜잭션 경계를 워커 자신에게 되돌리면(자기 호출) 프록시를
 * 거치지 않아 변경 감지가 사라지고, 단위 테스트는 통과하는데 DB에는 PENDING이
 * 그대로 남는다. 그 상황을 잡는 유일한 테스트다.
 */
@Tag("postgres")
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers
class ChatbotAnswerWriterPostgresTest {

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
    private ChatbotAnswerWriter writer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ChatbotAiClient chatbotAiClient;

    private Long conversationId;
    private Long assistantMessageId;

    @BeforeEach
    void setUp() {
        cleanup();
        seed();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private ChatbotAnswerRequestedEvent event() {
        return new ChatbotAnswerRequestedEvent(
                conversationId, assistantMessageId, 1L, "테스트 아파트", "교통 어때요?"
        );
    }

    private Map<String, Object> assistantRow() {
        return jdbcTemplate.queryForMap(
                """
                        SELECT status, content, basis_type, basis_label,
                               sources_json::text AS sources_json,
                               fail_reason, completed_at
                        FROM chatbot_message WHERE id = ?
                        """,
                assistantMessageId
        );
    }

    @Test
    void 성공_응답이_DB에_COMPLETED로_커밋된다() {
        when(chatbotAiClient.generateAnswer(any())).thenReturn(
                new ChatbotAiAnswerResponse(
                        "REPORT", "리포트 기반", "옥수역까지 도보 8분입니다.",
                        List.of(new ChatbotAiAnswerResponse.Source(
                                "REPORT", 48L, 48L, "테스트 아파트 임장 리포트",
                                null, null, "2026-07-25T16:00:00+09:00"
                        )),
                        0.72, false
                )
        );

        writer.generate(event());

        Map<String, Object> row = assistantRow();
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat(row.get("content")).isEqualTo("옥수역까지 도보 8분입니다.");
        assertThat(row.get("basis_type")).isEqualTo("REPORT");
        assertThat(row.get("basis_label")).isEqualTo("리포트 기반");
        // postgres 가 jsonb 를 정규화해 저장하므로 공백·키 순서가 달라진다.
        assertThat((String) row.get("sources_json"))
                .contains("\"reportId\"")
                .contains("48")
                .contains("\"sourceType\"")
                .contains("REPORT");
        assertThat(row.get("completed_at")).isNotNull();
    }

    @Test
    void AI_실패가_DB에_FAILED로_커밋된다() {
        when(chatbotAiClient.generateAnswer(any()))
                .thenThrow(new ChatbotAiException("AI 서비스에 연결하지 못했습니다."));

        writer.generate(event());

        Map<String, Object> row = assistantRow();
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat((String) row.get("fail_reason")).isNotBlank();
        assertThat(row.get("completed_at")).isNotNull();
    }

    @Test
    void markFailed도_DB에_커밋된다() {
        writer.markFailed(assistantMessageId);

        assertThat(assistantRow().get("status")).isEqualTo("FAILED");
    }

    @Test
    void 완료_후_대화의_lastMessageAt이_커밋된다() {
        when(chatbotAiClient.generateAnswer(any())).thenReturn(
                new ChatbotAiAnswerResponse(
                        "WEB", "웹 기반", "공개 자료 기준입니다.",
                        List.of(), null, false
                )
        );

        writer.generate(event());

        Object lastMessageAt = jdbcTemplate.queryForObject(
                "SELECT last_message_at FROM chatbot_conversation WHERE id = ?",
                Object.class,
                conversationId
        );
        assertThat(lastMessageAt).isNotNull();
    }

    private void seed() {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12);
        Long memberId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO member
                            (email, password_hash, nickname, status)
                        VALUES (?, 'hash', ?, 'ACTIVE')
                        RETURNING id
                        """,
                Long.class,
                "writer-" + suffix + "@example.com",
                "작성자" + suffix
        );
        Long apartmentId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO apartment
                            (complex_code, name, longitude, latitude)
                        VALUES (?, '테스트 아파트', 127.0, 37.0)
                        RETURNING id
                        """,
                Long.class,
                "W-" + suffix
        );
        conversationId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO chatbot_conversation (member_id, apartment_id)
                        VALUES (?, ?) RETURNING id
                        """,
                Long.class,
                memberId,
                apartmentId
        );
        assistantMessageId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO chatbot_message (conversation_id, role, status)
                        VALUES (?, 'ASSISTANT', 'PENDING') RETURNING id
                        """,
                Long.class,
                conversationId
        );
    }

    private void cleanup() {
        jdbcTemplate.update(
                "DELETE FROM chatbot_message WHERE conversation_id IN "
                        + "(SELECT id FROM chatbot_conversation)"
        );
        jdbcTemplate.update("DELETE FROM chatbot_conversation");
        jdbcTemplate.update(
                "DELETE FROM apartment WHERE complex_code LIKE 'W-%'"
        );
        jdbcTemplate.update(
                "DELETE FROM member WHERE email LIKE 'writer-%@example.com'"
        );
    }
}
