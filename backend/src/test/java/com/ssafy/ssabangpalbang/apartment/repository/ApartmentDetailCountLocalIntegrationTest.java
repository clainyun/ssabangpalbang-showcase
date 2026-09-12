package com.ssafy.ssabangpalbang.apartment.repository;

import com.ssafy.ssabangpalbang.quality.Be033PostgresContainerFactory;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ApartmentDetailCountLocalIntegrationTest {

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

    @Autowired private StudyRepository studyRepository;
    @Autowired private ApartmentReportQueryRepository reportRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @Transactional
    void 상세_bounds_목록의_스터디와_리포트_건수가_모두_일치한다() {
        long apartmentId = insertApartment();
        long memberId = insertMember();
        insertStudy(apartmentId, memberId, "RECRUITING");
        insertStudy(apartmentId, memberId, "RECRUITING");
        long completedStudy = insertStudy(apartmentId, memberId, "COMPLETED");
        insertReport(apartmentId, completedStudy);

        long studyDetail = studyRepository
                .countRecruitingByApartmentId(apartmentId);
        long studyList = studyRepository.findRecruitingOrderByScheduleAsc(
                apartmentId, PageRequest.of(0, 20)).getTotalElements();
        long studyBounds = studyRepository
                .countRecruitingByApartmentIds(List.of(apartmentId))
                .get(0).getCount();
        long reportDetail = reportRepository.countByApartmentId(apartmentId);
        long reportList = reportRepository.findPublicReports(
                apartmentId, memberId, 0, 20).getTotalElements();
        long reportBounds = reportRepository
                .countByApartmentIds(List.of(apartmentId)).get(0).count();

        assertThat(studyDetail).isEqualTo(studyList).isEqualTo(studyBounds);
        assertThat(reportDetail).isEqualTo(reportList).isEqualTo(reportBounds);
    }

    private long insertApartment() {
        return ((Number) entityManager.createNativeQuery("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES (:code, '상세 테스트', 127.0, 37.5) RETURNING id
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

    private long insertStudy(long apartmentId, long memberId, String status) {
        return ((Number) entityManager.createNativeQuery("""
                INSERT INTO study (
                    apartment_id, leader_id, title, goal, capacity, status
                ) VALUES (
                    :apartmentId, :memberId, :title, '목표', 5, :status
                ) RETURNING id
                """).setParameter("apartmentId", apartmentId)
                .setParameter("memberId", memberId)
                .setParameter("title", UUID.randomUUID().toString())
                .setParameter("status", status)
                .getSingleResult()).longValue();
    }

    private void insertReport(long apartmentId, long studyId) {
        long sessionId = ((Number) entityManager.createNativeQuery("""
                INSERT INTO field_session (study_id, status, ended_at)
                VALUES (:studyId, 'ENDED', now())
                RETURNING id
                """)
                .setParameter("studyId", studyId)
                .getSingleResult()).longValue();
        entityManager.createNativeQuery("""
                INSERT INTO report (
                    study_id, apartment_id, field_session_id,
                    status, completed_at
                ) VALUES (
                    :studyId, :apartmentId, :sessionId, 'DONE', now()
                )
                """).setParameter("studyId", studyId)
                .setParameter("apartmentId", apartmentId)
                .setParameter("sessionId", sessionId)
                .executeUpdate();
    }
}
