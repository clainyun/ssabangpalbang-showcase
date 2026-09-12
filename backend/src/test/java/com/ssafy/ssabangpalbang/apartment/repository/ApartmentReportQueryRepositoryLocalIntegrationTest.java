package com.ssafy.ssabangpalbang.apartment.repository;

import com.ssafy.ssabangpalbang.quality.Be033PostgresContainerFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ApartmentReportQueryRepositoryLocalIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            Be033PostgresContainerFactory.create();

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private ApartmentReportQueryRepository repository;
    @Autowired private EntityManager entityManager;

    @Test
    @Transactional
    void 유효한_스터디의_DONE_리포트만_정렬하고_찜과_카운트를_일치시킨다() {
        long apartmentId = insertApartment();
        long memberId = insertMember();
        long includedStudy = insertStudy(apartmentId, memberId, "COMPLETED", null);
        long pendingStudy = insertStudy(apartmentId, memberId, "COMPLETED", null);
        long deletedStudy = insertStudy(
                apartmentId, memberId, "COMPLETED", Instant.now());
        long canceledStudy = insertStudy(apartmentId, memberId, "CANCELED", null);
        long includedReport = insertReport(
                apartmentId, includedStudy, "DONE", Instant.now());
        insertReport(apartmentId, pendingStudy, "PENDING", Instant.now());
        insertReport(apartmentId, deletedStudy, "DONE", Instant.now());
        insertReport(apartmentId, canceledStudy, "DONE", Instant.now());
        favorite(memberId, includedReport);

        var page = repository.findPublicReports(apartmentId, memberId, 0, 20);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.reportId()).isEqualTo(includedReport);
            assertThat(row.favoritedByMe()).isTrue();
        });
        assertThat(repository.countByApartmentId(apartmentId))
                .isEqualTo(page.getTotalElements());
    }

    private long insertApartment() {
        return ((Number) entityManager.createNativeQuery("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES (:code, '리포트 테스트', 127.0, 37.5) RETURNING id
                """).setParameter("code", UUID.randomUUID().toString())
                .getSingleResult()).longValue();
    }

    private long insertMember() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        return ((Number) entityManager.createNativeQuery("""
                INSERT INTO member (email, nickname, selected_character_id, status)
                VALUES (:email, :nickname, 'PALBANG', 'ACTIVE') RETURNING id
                """).setParameter("email", suffix + "@test.local")
                .setParameter("nickname", suffix.substring(0, 20))
                .getSingleResult()).longValue();
    }

    private long insertStudy(
            long apartmentId, long memberId, String status, Instant deletedAt
    ) {
        return ((Number) entityManager.createNativeQuery("""
                INSERT INTO study (
                    apartment_id, leader_id, title, goal, capacity, status, deleted_at
                ) VALUES (
                    :apartmentId, :memberId, :title, '목표', 5, :status, :deletedAt
                ) RETURNING id
                """).setParameter("apartmentId", apartmentId)
                .setParameter("memberId", memberId)
                .setParameter("title", UUID.randomUUID().toString())
                .setParameter("status", status)
                .setParameter("deletedAt", deletedAt)
                .getSingleResult()).longValue();
    }

    private long insertReport(
            long apartmentId, long studyId, String status, Instant completedAt
    ) {
        long sessionId = insertFieldSession(studyId);
        return ((Number) entityManager.createNativeQuery("""
                INSERT INTO report (
                    study_id, apartment_id, field_session_id,
                    status, result_json, completed_at
                ) VALUES (
                    :studyId, :apartmentId, :sessionId,
                    :status, CAST(:json AS jsonb), :completedAt
                ) RETURNING id
                """).setParameter("studyId", studyId)
                .setParameter("apartmentId", apartmentId)
                .setParameter("sessionId", sessionId)
                .setParameter("status", status)
                .setParameter("json", "{\"title\":\"리포트\"}")
                .setParameter("completedAt", completedAt)
                .getSingleResult()).longValue();
    }

    private long insertFieldSession(long studyId) {
        return ((Number) entityManager.createNativeQuery("""
                INSERT INTO field_session (study_id, status, ended_at)
                VALUES (:studyId, 'ENDED', now())
                RETURNING id
                """)
                .setParameter("studyId", studyId)
                .getSingleResult()).longValue();
    }

    private void favorite(long memberId, long reportId) {
        entityManager.createNativeQuery("""
                INSERT INTO report_favorite (member_id, report_id)
                VALUES (:memberId, :reportId)
                """).setParameter("memberId", memberId)
                .setParameter("reportId", reportId)
                .executeUpdate();
    }
}
