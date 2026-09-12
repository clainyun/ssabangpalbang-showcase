package com.ssafy.ssabangpalbang.apartment.repository;

import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentBoundsCondition;
import com.ssafy.ssabangpalbang.quality.Be033PostgresContainerFactory;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ApartmentBoundsQueryRepositoryLocalIntegrationTest {

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

    @Autowired
    private ApartmentBoundsQueryRepository repository;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ApartmentReportQueryRepository reportRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void 거래가_여러_건이어도_아파트당_최신_정상_거래_한_행만_반환한다() {
        long apartmentId = insertApartment(37.5000, 127.0500);
        LocalDate dealDate = LocalDate.of(2026, 6, 4);
        insertTransaction(apartmentId, dealDate, Instant.parse("2026-07-01T00:00:01Z"), 1, false);
        insertTransaction(apartmentId, dealDate, Instant.parse("2026-07-01T00:00:02Z"), 2, false);
        insertTransaction(apartmentId, dealDate, Instant.parse("2026-07-01T00:00:03Z"), 3, false);
        insertTransaction(
                apartmentId,
                LocalDate.of(2026, 7, 1),
                Instant.parse("2026-07-02T00:00:00Z"),
                99,
                true
        );

        var rows = repository.findApartments(
                ApartmentBoundsCondition.of(37.4999, 127.0499, 37.5001, 127.0501)
        );

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.apartmentId()).isEqualTo(apartmentId);
            assertThat(row.dealDate()).isEqualTo(dealDate);
            assertThat(row.price()).isEqualTo(100003L);
        });
        assertThat(rows).extracting(ApartmentBoundsRow::apartmentId).doesNotHaveDuplicates();
    }

    @Test
    @Transactional
    void BETWEEN은_경계선_위의_아파트를_포함한다() {
        long apartmentId = insertApartment(37.5000, 127.0500);

        var rows = repository.findApartments(
                ApartmentBoundsCondition.of(37.5000, 127.0500, 37.5100, 127.0600)
        );

        assertThat(rows).extracting(ApartmentBoundsRow::apartmentId).contains(apartmentId);
    }

    @Test
    @Transactional
    void 취소된_거래만_있으면_최근_거래는_null이다() {
        long apartmentId = insertApartment(37.5000, 127.0500);
        insertTransaction(
                apartmentId,
                LocalDate.of(2026, 7, 1),
                Instant.parse("2026-07-02T00:00:00Z"),
                1,
                true
        );

        var rows = repository.findApartments(
                ApartmentBoundsCondition.of(37.4999, 127.0499, 37.5001, 127.0501)
        );

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.apartmentId()).isEqualTo(apartmentId);
            assertThat(row.price()).isNull();
            assertThat(row.exclusiveArea()).isNull();
            assertThat(row.dealDate()).isNull();
        });
    }

    @Test
    @Transactional
    void 최대_500건으로_화면_중심에_가까운_아파트를_선택한다() {
        long centerId = insertApartment(37.5000, 127.0500);
        long farthestId = insertApartment(37.4000, 126.9500);
        insertApartmentsNearCenter(499);

        var rows = repository.findApartments(
                ApartmentBoundsCondition.of(37.4000, 126.9500, 37.6000, 127.1500)
        );

        assertThat(rows).hasSize(500);
        assertThat(rows).extracting(ApartmentBoundsRow::apartmentId)
                .contains(centerId)
                .doesNotContain(farthestId);
    }

    @Test
    @Transactional
    void 실행계획은_좌표와_최근거래_인덱스를_사용한다() {
        long apartmentId = insertApartment(37.5000, 127.0500);
        insertApartmentsOutsideBounds(2000);
        insertTransactions(apartmentId, 1000);
        entityManager.createNativeQuery("ANALYZE apartment").executeUpdate();
        entityManager.createNativeQuery("ANALYZE apartment_transaction").executeUpdate();

        List<?> planRows = entityManager.createNativeQuery("""
                        EXPLAIN (ANALYZE, BUFFERS)
                        SELECT a.id, a.name, a.address, a.latitude, a.longitude,
                               t.price, t.exclusive_area, t.deal_date
                        FROM (
                            SELECT id, name, address, latitude, longitude
                            FROM apartment
                            WHERE latitude BETWEEN 37.4999 AND 37.5001
                              AND longitude BETWEEN 127.0499 AND 127.0501
                            ORDER BY
                                (latitude - 37.5) * (latitude - 37.5) / (0.0002 * 0.0002)
                              + (longitude - 127.05) * (longitude - 127.05)
                                    / (0.0002 * 0.0002)
                            LIMIT 500
                        ) a
                        LEFT JOIN LATERAL (
                            SELECT tx.price, tx.exclusive_area, tx.deal_date
                            FROM apartment_transaction tx
                            WHERE tx.apartment_id = a.id
                              AND tx.is_canceled = false
                            ORDER BY tx.deal_date DESC, tx.collected_at DESC, tx.id DESC
                            LIMIT 1
                        ) t ON TRUE
                        ORDER BY a.id ASC
                        """).getResultList();
        String plan = planRows.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("\n"));

        assertThat(plan).contains("idx_apartment_coord", "idx_transaction_recent");
        assertThat(plan).doesNotContain("Seq Scan on apartment ");
    }

    @Test
    @Transactional
    void 모집_스터디와_완료_리포트_집계는_실제_목록_건수와_일치한다() {
        long leaderId = insertMember();
        long participantId = insertMember();
        long recruitingApartmentId = insertApartment(37.51, 127.01);
        long ignoredApartmentId = insertApartment(37.52, 127.02);
        long noScheduleApartmentId = insertApartment(37.53, 127.03);
        long fullApartmentId = insertApartment(37.54, 127.04);
        long reportApartmentId = insertApartment(37.55, 127.05);

        long recruitingOne = insertStudy(recruitingApartmentId, leaderId, "RECRUITING", 5);
        long recruitingTwo = insertStudy(recruitingApartmentId, leaderId, "RECRUITING", 5);
        insertSchedule(recruitingOne);
        insertSchedule(recruitingTwo);

        insertStudy(ignoredApartmentId, leaderId, "CLOSED", 5);
        insertStudy(ignoredApartmentId, leaderId, "COMPLETED", 5);
        insertStudy(ignoredApartmentId, leaderId, "CANCELED", 5);

        insertStudy(noScheduleApartmentId, leaderId, "RECRUITING", 5);

        long fullStudyId = insertStudy(fullApartmentId, leaderId, "RECRUITING", 1);
        insertStudyMember(fullStudyId, participantId);

        long doneStudyOne = insertStudy(reportApartmentId, leaderId, "COMPLETED", 5);
        long doneStudyTwo = insertStudy(reportApartmentId, leaderId, "COMPLETED", 5);
        long pendingStudy = insertStudy(reportApartmentId, leaderId, "COMPLETED", 5);
        insertReport(reportApartmentId, doneStudyOne, "DONE");
        insertReport(reportApartmentId, doneStudyTwo, "DONE");
        insertReport(reportApartmentId, pendingStudy, "PENDING");

        List<Long> apartmentIds = List.of(
                recruitingApartmentId,
                ignoredApartmentId,
                noScheduleApartmentId,
                fullApartmentId,
                reportApartmentId
        );
        Map<Long, Long> studyCounts = studyRepository
                .countRecruitingByApartmentIds(apartmentIds).stream()
                .collect(Collectors.toMap(
                        StudyRepository.ApartmentStudyCountRow::getApartmentId,
                        StudyRepository.ApartmentStudyCountRow::getCount
                ));
        Map<Long, Long> reportCounts = reportRepository.countByApartmentIds(apartmentIds).stream()
                .collect(Collectors.toMap(ApartmentCountRow::apartmentId, ApartmentCountRow::count));

        assertThat(studyCounts.get(recruitingApartmentId)).isEqualTo(2L);
        assertThat(studyCounts.getOrDefault(ignoredApartmentId, 0L)).isZero();
        assertThat(studyCounts.get(noScheduleApartmentId)).isEqualTo(1L);
        assertThat(studyCounts.getOrDefault(fullApartmentId, 0L)).isZero();
        assertThat(reportCounts.get(reportApartmentId)).isEqualTo(2L);
    }

    private long insertApartment(double latitude, double longitude) {
        return ((Number) entityManager.createNativeQuery("""
                        INSERT INTO apartment (complex_code, name, longitude, latitude)
                        VALUES (:complexCode, 'bounds 테스트', :longitude, :latitude)
                        RETURNING id
                        """)
                .setParameter("complexCode", UUID.randomUUID().toString())
                .setParameter("longitude", longitude)
                .setParameter("latitude", latitude)
                .getSingleResult()).longValue();
    }

    private void insertApartmentsNearCenter(int count) {
        entityManager.createNativeQuery("""
                        INSERT INTO apartment (complex_code, name, longitude, latitude)
                        SELECT 'near-' || :suffix || '-' || value,
                               '중심 인근',
                               127.05 + value * 0.000001,
                               37.5 + value * 0.000001
                        FROM generate_series(1, :count) value
                        """)
                .setParameter("suffix", UUID.randomUUID().toString())
                .setParameter("count", count)
                .executeUpdate();
    }

    private void insertApartmentsOutsideBounds(int count) {
        entityManager.createNativeQuery("""
                        INSERT INTO apartment (complex_code, name, longitude, latitude)
                        SELECT 'outside-' || :suffix || '-' || value,
                               '경계 밖',
                               126.0 + value * 0.000001,
                               36.0 + value * 0.000001
                        FROM generate_series(1, :count) value
                        """)
                .setParameter("suffix", UUID.randomUUID().toString())
                .setParameter("count", count)
                .executeUpdate();
    }

    private void insertTransactions(long apartmentId, int count) {
        entityManager.createNativeQuery("""
                        INSERT INTO apartment_transaction (
                            apartment_id, deal_date, exclusive_area, price,
                            is_canceled, dedup_key, collected_at
                        )
                        SELECT :apartmentId,
                               DATE '2026-06-01' + (value % 20)::int,
                               84.95,
                               100000 + value,
                               false,
                               'plan-' || :suffix || '-' || value,
                               TIMESTAMPTZ '2026-07-01T00:00:00Z'
                                   + value * INTERVAL '1 second'
                        FROM generate_series(1, :count) value
                        """)
                .setParameter("apartmentId", apartmentId)
                .setParameter("suffix", UUID.randomUUID().toString())
                .setParameter("count", count)
                .executeUpdate();
    }

    private void insertTransaction(
            long apartmentId,
            LocalDate dealDate,
            Instant collectedAt,
            int sequence,
            boolean canceled
    ) {
        entityManager.createNativeQuery("""
                        INSERT INTO apartment_transaction (
                            apartment_id, deal_date, exclusive_area, price,
                            is_canceled, dedup_key, collected_at
                        )
                        VALUES (
                            :apartmentId, :dealDate, 84.95, :price,
                            :canceled, :dedupKey, :collectedAt
                        )
                        """)
                .setParameter("apartmentId", apartmentId)
                .setParameter("dealDate", dealDate)
                .setParameter("price", 100000L + sequence)
                .setParameter("dedupKey", UUID.randomUUID().toString())
                .setParameter("collectedAt", collectedAt)
                .setParameter("canceled", canceled)
                .executeUpdate();
    }

    private long insertMember() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        return ((Number) entityManager.createNativeQuery("""
                        INSERT INTO member (email, nickname, selected_character_id, status)
                        VALUES (:email, :nickname, 'PALBANG', 'ACTIVE')
                        RETURNING id
                        """)
                .setParameter("email", suffix + "@test.local")
                .setParameter("nickname", suffix.substring(0, 20))
                .getSingleResult()).longValue();
    }

    private long insertStudy(
            long apartmentId,
            long memberId,
            String status,
            int capacity
    ) {
        return ((Number) entityManager.createNativeQuery("""
                        INSERT INTO study (
                            apartment_id, leader_id, title, goal, capacity, status
                        )
                        VALUES (
                            :apartmentId, :memberId, '집계 테스트',
                            '테스트 목표', :capacity, :status
                        )
                        RETURNING id
                        """)
                .setParameter("apartmentId", apartmentId)
                .setParameter("memberId", memberId)
                .setParameter("capacity", capacity)
                .setParameter("status", status)
                .getSingleResult()).longValue();
    }

    private void insertSchedule(long studyId) {
        entityManager.createNativeQuery("""
                        INSERT INTO schedule (study_id, start_at, status)
                        VALUES (:studyId, now() + INTERVAL '7 days', 'SCHEDULED')
                        """)
                .setParameter("studyId", studyId)
                .executeUpdate();
    }

    private void insertStudyMember(long studyId, long memberId) {
        entityManager.createNativeQuery("""
                        INSERT INTO study_member (study_id, member_id, status)
                        VALUES (:studyId, :memberId, 'ACTIVE')
                        """)
                .setParameter("studyId", studyId)
                .setParameter("memberId", memberId)
                .executeUpdate();
    }

    private void insertReport(long apartmentId, long studyId, String status) {
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
                        )
                        VALUES (
                            :studyId, :apartmentId, :sessionId,
                            :status, now()
                        )
                        """)
                .setParameter("studyId", studyId)
                .setParameter("apartmentId", apartmentId)
                .setParameter("sessionId", sessionId)
                .setParameter("status", status)
                .executeUpdate();
    }
}
