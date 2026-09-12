package com.ssafy.ssabangpalbang.home.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(
        named = "RUN_LOCAL_INFRA_TESTS",
        matches = "true"
)
class HomeQueryRepositoryLocalIntegrationTest {

    @Autowired
    private HomeQueryRepository homeQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 다음_임장_카드를_PostgreSQL에서_조회한다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        Long memberId = insertMember(suffix);
        Long apartmentId = insertApartment(suffix);
        Long visitStudyId = insertStudy(
                apartmentId,
                memberId,
                "CLOSED",
                "다음 임장 " + suffix
        );
        insertActiveMember(visitStudyId, memberId);
        insertSchedule(
                visitStudyId,
                Instant.parse("2030-01-03T06:00:00Z")
        );

        Instant now = Instant.parse("2029-12-31T15:00:00Z");
        List<HomeNextVisitRow> nextVisits = homeQueryRepository.findNextVisit(
                memberId,
                now,
                PageRequest.of(0, 1)
        );
        assertThat(nextVisits).singleElement().satisfies(row -> {
            assertThat(row.getStudyId()).isEqualTo(visitStudyId);
            assertThat(row.getApartmentName())
                    .startsWith("홈아파트");
            assertThat(row.getMeetingPlace()).isEqualTo("시청역 1번 출구");
            assertThat(row.getCurrentMemberCount()).isEqualTo(1L);
        });
    }

    @Test
    void 지난_날짜_임장은_진행_중이어도_제외하고_미래_임장을_반환한다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        Long memberId = insertMember(suffix);
        Long apartmentId = insertApartment(suffix);
        Long inProgressStudyId = insertStudy(
                apartmentId,
                memberId,
                "IN_PROGRESS",
                "진행 중 임장 " + suffix
        );
        Long futureStudyId = insertStudy(
                apartmentId,
                memberId,
                "CLOSED",
                "미래 임장 " + suffix
        );
        insertActiveMember(inProgressStudyId, memberId);
        insertActiveMember(futureStudyId, memberId);
        insertSchedule(
                inProgressStudyId,
                Instant.parse("2030-01-01T06:00:00Z")
        );
        insertSchedule(
                futureStudyId,
                Instant.parse("2030-01-03T06:00:00Z")
        );

        // 조회 기준일 0시(오늘)보다 이전 날짜인 지난 임장은 진행 중이어도 노출하지 않고,
        // 당일 이후의 미래 임장만 노출한다.
        List<HomeNextVisitRow> nextVisits = homeQueryRepository.findNextVisit(
                memberId,
                Instant.parse("2030-01-02T00:00:00Z"),
                PageRequest.of(0, 1)
        );

        assertThat(nextVisits).singleElement().satisfies(row ->
                assertThat(row.getStudyId()).isEqualTo(futureStudyId)
        );
    }

    @Test
    void 종료된_임장은_예정_일정이_남아있어도_제외한다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        Long memberId = insertMember(suffix);
        Long apartmentId = insertApartment(suffix);
        Long endedStudyId = insertStudy(
                apartmentId,
                memberId,
                "IN_PROGRESS",
                "종료된 임장 " + suffix
        );
        insertActiveMember(endedStudyId, memberId);
        // 당일 이후의 SCHEDULED 일정이 남아있는 상황.
        insertSchedule(
                endedStudyId,
                Instant.parse("2030-01-03T06:00:00Z")
        );
        // 다만 해당 스터디의 임장 세션은 이미 종료(ENDED)됨.
        insertEndedFieldSession(endedStudyId);

        List<HomeNextVisitRow> nextVisits = homeQueryRepository.findNextVisit(
                memberId,
                Instant.parse("2030-01-02T00:00:00Z"),
                PageRequest.of(0, 1)
        );

        assertThat(nextVisits).isEmpty();
    }

    @Test
    void 지난_미시작_일정은_조회하지_않는다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        Long memberId = insertMember(suffix);
        Long apartmentId = insertApartment(suffix);
        Long closedStudyId = insertStudy(
                apartmentId,
                memberId,
                "CLOSED",
                "지난 임장 " + suffix
        );
        insertActiveMember(closedStudyId, memberId);
        insertSchedule(
                closedStudyId,
                Instant.parse("2030-01-01T06:00:00Z")
        );

        List<HomeNextVisitRow> nextVisits = homeQueryRepository.findNextVisit(
                memberId,
                Instant.parse("2030-01-02T06:00:00Z"),
                PageRequest.of(0, 1)
        );

        assertThat(nextVisits).isEmpty();
    }

    private Long insertMember(String suffix) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO member (
                            email, password_hash, nickname, status
                        )
                        VALUES (?, 'encoded-password', ?, 'ACTIVE')
                        RETURNING id
                        """,
                Long.class,
                "home-" + suffix + "@example.com",
                "홈회원" + suffix
        );
    }

    private Long insertApartment(String suffix) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO apartment (
                            complex_code, name, address, district_name,
                            longitude, latitude
                        )
                        VALUES (?, ?, '서울 중구 테스트로 1', '중구',
                                126.9780, 37.5665)
                        RETURNING id
                        """,
                Long.class,
                "home-" + suffix,
                "홈아파트" + suffix
        );
    }

    private Long insertStudy(
            Long apartmentId,
            Long leaderId,
            String status,
            String title
    ) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO study (
                            apartment_id, leader_id, title, goal,
                            capacity, status
                        )
                        VALUES (?, ?, ?, '홈 검증', 4, ?)
                        RETURNING id
                        """,
                Long.class,
                apartmentId,
                leaderId,
                title,
                status
        );
    }

    private void insertActiveMember(Long studyId, Long memberId) {
        jdbcTemplate.update(
                """
                        INSERT INTO study_member (
                            study_id, member_id, role, status
                        )
                        VALUES (?, ?, 'LEADER', 'ACTIVE')
                        """,
                studyId,
                memberId
        );
    }

    private void insertEndedFieldSession(Long studyId) {
        jdbcTemplate.update(
                """
                        INSERT INTO field_session (
                            study_id, status, started_at, ended_at, end_reason
                        )
                        VALUES (?, 'ENDED', ?, ?, 'ALL_ENDED')
                        """,
                studyId,
                Timestamp.from(Instant.parse("2030-01-01T06:00:00Z")),
                Timestamp.from(Instant.parse("2030-01-01T08:00:00Z"))
        );
    }

    private Long insertSchedule(Long studyId, Instant startAt) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO schedule (
                            study_id, start_at, end_at,
                            meeting_place, status
                        )
                        VALUES (?, ?, ?, '시청역 1번 출구', 'SCHEDULED')
                        RETURNING id
                        """,
                Long.class,
                studyId,
                Timestamp.from(startAt),
                Timestamp.from(startAt.plus(3, ChronoUnit.HOURS))
        );
    }

}
