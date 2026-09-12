package com.ssafy.ssabangpalbang.chat.repository;

import com.ssafy.ssabangpalbang.chat.domain.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * (sender_id, client_message_id) partial unique index와 채팅 이력·안읽은 수
 * native query를 실제 PostgreSQL(현재 프로젝트가 사용하는 pgvector+postgis 이미지) 위에서 검증한다.
 *
 * <p>H2로 약화하지 않고 {@code infra/postgres/Dockerfile}(로컬 docker-compose와 동일한 이미지)을
 * Testcontainers로 빌드해 실행한다. Docker daemon이 필요하므로 기본 {@code test} 태스크에서는
 * {@code excludeTags 'postgres'}로 제외되고, 로컬에서 {@code ./gradlew postgresTest}로만 실행한다.</p>
 */
@Tag("postgres")
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ChatMessagePostgresConstraintTest {

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
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long studyId;
    private Long senderId;
    private Long otherSenderId;
    private Long imageFileId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO apartment (complex_code, name, longitude, latitude) "
                        + "VALUES ('TEST-COMPLEX', '테스트 아파트', 127.0, 37.5)"
        );
        Long apartmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM apartment WHERE complex_code = 'TEST-COMPLEX'", Long.class
        );

        senderId = insertMember("sender@example.com", "sender-nick");
        otherSenderId = insertMember("other@example.com", "other-nick");

        jdbcTemplate.update(
                "INSERT INTO study (apartment_id, leader_id, goal, capacity) "
                        + "VALUES (?, ?, 'goal', 5)",
                apartmentId, senderId
        );
        studyId = jdbcTemplate.queryForObject(
                "SELECT id FROM study WHERE leader_id = ?", Long.class, senderId
        );

        jdbcTemplate.update(
                "INSERT INTO file_meta (owner_id, study_id, file_usage, s3_key, upload_status) "
                        + "VALUES (?, ?, 'CHAT_IMAGE', 'chat/test-image.jpg', 'COMPLETED')",
                otherSenderId, studyId
        );
        imageFileId = jdbcTemplate.queryForObject(
                "SELECT id FROM file_meta WHERE s3_key = 'chat/test-image.jpg'", Long.class
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
    void 동일_발신자의_동일_clientMessageId는_유일성_제약을_위반한다() {
        chatMessageRepository.saveAndFlush(
                ChatMessage.createText(studyId, senderId, "첫 번째 전송", "dup-key")
        );

        assertThatThrownBy(() -> chatMessageRepository.saveAndFlush(
                ChatMessage.createText(studyId, senderId, "재전송(같은 clientMessageId)", "dup-key")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_발신자가_같은_clientMessageId를_사용하면_모두_저장된다() {
        ChatMessage first = chatMessageRepository.saveAndFlush(
                ChatMessage.createText(studyId, senderId, "발신자1", "shared-key")
        );
        ChatMessage second = chatMessageRepository.saveAndFlush(
                ChatMessage.createText(studyId, otherSenderId, "발신자2", "shared-key")
        );

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void SYSTEM_메시지는_senderId와_clientMessageId가_없어도_여러_건_저장된다() {
        ChatMessage first = chatMessageRepository.saveAndFlush(
                ChatMessage.createSystem(studyId, "일정이 변경되었습니다.")
        );
        ChatMessage second = chatMessageRepository.saveAndFlush(
                ChatMessage.createSystem(studyId, "멤버가 변경되었습니다.")
        );

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getSenderId()).isNull();
        assertThat(first.getClientMessageId()).isNull();
    }

    @Test
    void findHistoryPage는_id_역순으로_커서_기반_페이지를_조회한다() {
        ChatMessage m1 = chatMessageRepository.saveAndFlush(
                ChatMessage.createText(studyId, senderId, "메시지1", "k1")
        );
        ChatMessage m2 = chatMessageRepository.saveAndFlush(
                ChatMessage.createSystem(studyId, "시스템 메시지")
        );
        ChatMessage m3 = chatMessageRepository.saveAndFlush(
                ChatMessage.createImage(studyId, otherSenderId, imageFileId, "k2")
        );

        List<ChatMessageHistoryRow> firstPage =
                chatMessageRepository.findHistoryPage(studyId, null, 2);
        assertThat(firstPage).extracting(ChatMessageHistoryRow::getMessageId)
                .containsExactly(m3.getId(), m2.getId());

        List<ChatMessageHistoryRow> secondPage =
                chatMessageRepository.findHistoryPage(studyId, m2.getId(), 2);
        assertThat(secondPage).extracting(ChatMessageHistoryRow::getMessageId)
                .containsExactly(m1.getId());

        ChatMessageHistoryRow systemRow = firstPage.stream()
                .filter(row -> "SYSTEM".equals(row.getMessageType()))
                .findFirst().orElseThrow();
        assertThat(systemRow.getSenderId()).isNull();
        assertThat(systemRow.getSenderNickname()).isNull();
    }

    @Test
    void countUnreadExcludingSender는_본인_메시지를_제외하고_SYSTEM은_포함한다() {
        chatMessageRepository.saveAndFlush(
                ChatMessage.createText(studyId, senderId, "내가 보낸 메시지", "k1")
        );
        chatMessageRepository.saveAndFlush(
                ChatMessage.createText(studyId, otherSenderId, "상대가 보낸 메시지", "k2")
        );
        chatMessageRepository.saveAndFlush(
                ChatMessage.createSystem(studyId, "시스템 메시지")
        );

        long unreadForSender = chatMessageRepository.countUnreadExcludingSender(
                studyId, senderId, null
        );

        // 본인이 보낸 메시지 1건은 제외하고, 상대 메시지 1건 + SYSTEM 1건 = 2건
        assertThat(unreadForSender).isEqualTo(2L);
    }

    @Test
    void countUnreadExcludingSender는_lastReadAt_이후_메시지만_계산한다() {
        chatMessageRepository.saveAndFlush(
                ChatMessage.createText(studyId, otherSenderId, "읽음 처리 전 메시지", "k1")
        );
        Instant lastReadAt = Instant.now().plusMillis(50);
        sleepBriefly();
        chatMessageRepository.saveAndFlush(
                ChatMessage.createText(studyId, otherSenderId, "읽음 처리 후 메시지", "k2")
        );

        long unreadCount = chatMessageRepository.countUnreadExcludingSender(
                studyId, senderId, lastReadAt
        );

        assertThat(unreadCount).isEqualTo(1L);
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
