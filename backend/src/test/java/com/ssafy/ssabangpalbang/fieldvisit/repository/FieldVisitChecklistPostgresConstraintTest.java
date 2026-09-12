package com.ssafy.ssabangpalbang.fieldvisit.repository;

import com.ssafy.ssabangpalbang.fieldvisit.domain.Checklist;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistGenerationProgress;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistGenerationProgressStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistGenerationStage;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI-002 체크리스트 도메인(field_session, field_participant, checklist,
 * checklist_item, checklist_answer)의 UNIQUE·CHECK 제약을 실제 PostgreSQL 위에서
 * 검증한다.
 *
 * <p>{@link com.ssafy.ssabangpalbang.chat.repository.ChatMessagePostgresConstraintTest}와
 * 동일한 방식으로 {@code infra/postgres/Dockerfile}을 Testcontainers로 빌드해 실행한다.
 * Docker daemon이 필요하므로 기본 {@code test} 태스크에서는 제외되고, 로컬에서
 * {@code ./gradlew postgresTest}로만 실행한다.</p>
 *
 * <p>{@code field_session}·{@code field_participant}는 AI-002·BE-015가 쓰기를
 * 하지 않으므로(각 Repository가 조회 전용) 이 테스트에서도 JdbcTemplate으로
 * 직접 INSERT해 DB 제약만 검증한다. {@code checklist}·{@code checklist_item}은
 * AI-002가 쓰기를 하므로 Repository를 통해 검증한다.</p>
 */
@Tag("postgres")
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class FieldVisitChecklistPostgresConstraintTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FieldSessionRepository fieldSessionRepository;

    @Autowired
    private FieldParticipantRepository fieldParticipantRepository;

    @Autowired
    private ChecklistRepository checklistRepository;

    @Autowired
    private ChecklistItemRepository checklistItemRepository;

    @Autowired
    private ChecklistAnswerRepository checklistAnswerRepository;

    @Autowired
    private ChecklistGenerationProgressRepository generationProgressRepository;

    private Long studyId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO apartment (complex_code, name, longitude, latitude) "
                        + "VALUES ('FV-TEST-COMPLEX', '체크리스트 테스트 아파트', 127.0, 37.5)"
        );
        Long apartmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM apartment WHERE complex_code = 'FV-TEST-COMPLEX'", Long.class
        );

        memberId = insertMember("fieldvisit-leader@example.com", "fv-leader");

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

    private Long insertFieldSession(Long studyId) {
        jdbcTemplate.update(
                "INSERT INTO field_session (study_id, status) VALUES (?, 'IN_PROGRESS')",
                studyId
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM field_session WHERE study_id = ?", Long.class, studyId
        );
    }

    private Long insertFieldParticipant(Long sessionId, Long memberId) {
        jdbcTemplate.update(
                "INSERT INTO field_participant (session_id, member_id, status) "
                        + "VALUES (?, ?, 'IN_PROGRESS')",
                sessionId, memberId
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM field_participant WHERE session_id = ? AND member_id = ?",
                Long.class, sessionId, memberId
        );
    }

    @Test
    void field_session은_study_id당_한_건만_존재한다() {
        insertFieldSession(studyId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO field_session (study_id, status) VALUES (?, 'IN_PROGRESS')",
                studyId
        )).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void FieldSessionRepository는_studyId로_세션을_조회한다() {
        Long sessionId = insertFieldSession(studyId);

        FieldSession session = fieldSessionRepository.findByStudyId(studyId).orElseThrow();

        assertThat(session.getId()).isEqualTo(sessionId);
        assertThat(session.getStatus().name()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void field_participant는_session_member_조합당_한_건만_존재한다() {
        Long sessionId = insertFieldSession(studyId);
        insertFieldParticipant(sessionId, memberId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO field_participant (session_id, member_id, status) "
                        + "VALUES (?, ?, 'IN_PROGRESS')",
                sessionId, memberId
        )).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void FieldParticipantRepository는_session과_member로_참여자를_조회한다() {
        Long sessionId = insertFieldSession(studyId);
        Long participantId = insertFieldParticipant(sessionId, memberId);

        FieldParticipant participant = fieldParticipantRepository
                .findBySessionIdAndMemberId(sessionId, memberId)
                .orElseThrow();

        assertThat(participant.getId()).isEqualTo(participantId);
    }

    @Test
    void checklist는_session_member_조합당_한_건만_저장된다() {
        Long sessionId = insertFieldSession(studyId);
        insertFieldParticipant(sessionId, memberId);

        checklistRepository.saveAndFlush(Checklist.create(sessionId, memberId, false));

        assertThatThrownBy(() -> checklistRepository.saveAndFlush(
                Checklist.create(sessionId, memberId, false)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ChecklistRepository는_session과_member로_체크리스트를_조회한다() {
        Long sessionId = insertFieldSession(studyId);
        insertFieldParticipant(sessionId, memberId);
        Checklist saved = checklistRepository.saveAndFlush(
                Checklist.create(sessionId, memberId, false)
        );

        Checklist found = checklistRepository
                .findBySessionIdAndMemberId(sessionId, memberId)
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getGeneratedAt()).isNotNull();
    }

    @Test
    void checklist_item은_checklist_내에서_display_order가_유일해야_한다() {
        Long sessionId = insertFieldSession(studyId);
        insertFieldParticipant(sessionId, memberId);
        Checklist checklist = checklistRepository.saveAndFlush(
                Checklist.create(sessionId, memberId, false)
        );

        checklistItemRepository.saveAndFlush(
                ChecklistItem.create(checklist.getId(), "교통", "역까지 거리", null, 1, null)
        );

        assertThatThrownBy(() -> checklistItemRepository.saveAndFlush(
                ChecklistItem.create(checklist.getId(), "소음", "도로 소음", null, 1, null)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checklist_item의_title이_공백뿐이면_DB_CHECK_제약을_위반한다() {
        Long sessionId = insertFieldSession(studyId);
        insertFieldParticipant(sessionId, memberId);
        Checklist checklist = checklistRepository.saveAndFlush(
                Checklist.create(sessionId, memberId, false)
        );

        // ChecklistItem.create()는 Java 레벨에서 이미 공백 title을 막지만,
        // DB CHECK 제약이 실제로 존재하는지도 독립적으로 검증한다.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO checklist_item "
                        + "(checklist_id, category, title, display_order) "
                        + "VALUES (?, '교통', '   ', 1)",
                checklist.getId()
        )).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void ChecklistItemRepository는_displayOrder_오름차순으로_조회한다() {
        Long sessionId = insertFieldSession(studyId);
        insertFieldParticipant(sessionId, memberId);
        Checklist checklist = checklistRepository.saveAndFlush(
                Checklist.create(sessionId, memberId, false)
        );
        checklistItemRepository.saveAndFlush(
                ChecklistItem.create(checklist.getId(), "교통", "두 번째", null, 2, null)
        );
        checklistItemRepository.saveAndFlush(
                ChecklistItem.create(checklist.getId(), "교통", "첫 번째", null, 1, null)
        );

        List<ChecklistItem> items = checklistItemRepository
                .findByChecklistIdOrderByDisplayOrderAsc(checklist.getId());

        assertThat(items).extracting(ChecklistItem::getTitle)
                .containsExactly("첫 번째", "두 번째");
    }

    @Test
    void checklist_answer는_checklist_item당_한_건만_존재한다() {
        Long sessionId = insertFieldSession(studyId);
        insertFieldParticipant(sessionId, memberId);
        Checklist checklist = checklistRepository.saveAndFlush(
                Checklist.create(sessionId, memberId, false)
        );
        ChecklistItem item = checklistItemRepository.saveAndFlush(
                ChecklistItem.create(checklist.getId(), "교통", "역까지 거리", null, 1, null)
        );
        insertChecklistAnswer(item.getId());

        assertThatThrownBy(() -> insertChecklistAnswer(item.getId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void ChecklistAnswerRepository는_항목ID_목록으로_답변을_일괄_조회한다() {
        Long sessionId = insertFieldSession(studyId);
        insertFieldParticipant(sessionId, memberId);
        Checklist checklist = checklistRepository.saveAndFlush(
                Checklist.create(sessionId, memberId, false)
        );
        ChecklistItem item1 = checklistItemRepository.saveAndFlush(
                ChecklistItem.create(checklist.getId(), "교통", "역까지 거리", null, 1, null)
        );
        ChecklistItem item2 = checklistItemRepository.saveAndFlush(
                ChecklistItem.create(checklist.getId(), "교통", "혼잡도", null, 2, null)
        );
        insertChecklistAnswer(item1.getId());
        // item2는 아직 답변 행이 없는 상태(생성 직후 기본값 처리 대상)를 재현한다.

        List<com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistAnswer> answers =
                checklistAnswerRepository.findByChecklistItemIdIn(
                        List.of(item1.getId(), item2.getId())
                );

        assertThat(answers).hasSize(1);
        assertThat(answers.get(0).getChecklistItemId()).isEqualTo(item1.getId());
    }

    @Test
    void generationProgressIsIsolatedByAttemptId() {
        Long sessionId = insertFieldSession(studyId);

        generationProgressRepository.restart(
                sessionId,
                memberId,
                "attempt-a",
                ChecklistGenerationStage.PREPARING.name(),
                ChecklistGenerationStage.PREPARING.progressRate(),
                ChecklistGenerationStage.PREPARING.message()
        );
        generationProgressRepository.advance(
                sessionId,
                memberId,
                "attempt-a",
                ChecklistGenerationProgressStatus.IN_PROGRESS.name(),
                ChecklistGenerationStage.AI_GENERATION.name(),
                ChecklistGenerationStage.AI_GENERATION.progressRate(),
                ChecklistGenerationStage.AI_GENERATION.message()
        );
        generationProgressRepository.restart(
                sessionId,
                memberId,
                "attempt-b",
                ChecklistGenerationStage.PREPARING.name(),
                ChecklistGenerationStage.PREPARING.progressRate(),
                ChecklistGenerationStage.PREPARING.message()
        );
        generationProgressRepository.fail(
                sessionId,
                memberId,
                "attempt-b",
                ChecklistGenerationStage.PREPARING.name(),
                ChecklistGenerationStage.PREPARING.progressRate(),
                ChecklistGenerationStage.PREPARING.message(),
                "생성에 실패했어요."
        );

        ChecklistGenerationProgress attemptA = generationProgressRepository
                .findBySessionIdAndMemberIdAndAttemptId(sessionId, memberId, "attempt-a")
                .orElseThrow();
        ChecklistGenerationProgress attemptB = generationProgressRepository
                .findBySessionIdAndMemberIdAndAttemptId(sessionId, memberId, "attempt-b")
                .orElseThrow();

        assertThat(attemptA.getStatus())
                .isEqualTo(ChecklistGenerationProgressStatus.IN_PROGRESS);
        assertThat(attemptA.getProgressRate())
                .isEqualTo(ChecklistGenerationStage.AI_GENERATION.progressRate());
        assertThat(attemptB.getStatus())
                .isEqualTo(ChecklistGenerationProgressStatus.FAILED);
    }

    @Test
    void generationProgressDoesNotRegressOrFailAfterCompletion() {
        Long sessionId = insertFieldSession(studyId);
        String attemptId = "attempt-monotonic";

        generationProgressRepository.restart(
                sessionId,
                memberId,
                attemptId,
                ChecklistGenerationStage.PREPARING.name(),
                ChecklistGenerationStage.PREPARING.progressRate(),
                ChecklistGenerationStage.PREPARING.message()
        );
        generationProgressRepository.advance(
                sessionId,
                memberId,
                attemptId,
                ChecklistGenerationProgressStatus.IN_PROGRESS.name(),
                ChecklistGenerationStage.RESULT_SAVING.name(),
                ChecklistGenerationStage.RESULT_SAVING.progressRate(),
                ChecklistGenerationStage.RESULT_SAVING.message()
        );
        generationProgressRepository.advance(
                sessionId,
                memberId,
                attemptId,
                ChecklistGenerationProgressStatus.IN_PROGRESS.name(),
                ChecklistGenerationStage.PERSONALIZATION.name(),
                ChecklistGenerationStage.PERSONALIZATION.progressRate(),
                ChecklistGenerationStage.PERSONALIZATION.message()
        );
        generationProgressRepository.advance(
                sessionId,
                memberId,
                attemptId,
                ChecklistGenerationProgressStatus.DONE.name(),
                ChecklistGenerationStage.COMPLETED.name(),
                ChecklistGenerationStage.COMPLETED.progressRate(),
                ChecklistGenerationStage.COMPLETED.message()
        );
        generationProgressRepository.fail(
                sessionId,
                memberId,
                attemptId,
                ChecklistGenerationStage.PREPARING.name(),
                ChecklistGenerationStage.PREPARING.progressRate(),
                ChecklistGenerationStage.PREPARING.message(),
                "늦게 도착한 실패"
        );

        ChecklistGenerationProgress progress = generationProgressRepository
                .findBySessionIdAndMemberIdAndAttemptId(sessionId, memberId, attemptId)
                .orElseThrow();

        assertThat(progress.getStatus()).isEqualTo(ChecklistGenerationProgressStatus.DONE);
        assertThat(progress.getProgressRate()).isEqualTo(100);
    }

    private void insertChecklistAnswer(Long checklistItemId) {
        jdbcTemplate.update(
                "INSERT INTO checklist_answer (checklist_item_id, is_completed, updated_at) "
                        + "VALUES (?, false, ?)",
                checklistItemId, Timestamp.from(Instant.now())
        );
    }
}
