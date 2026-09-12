package com.ssafy.ssabangpalbang.study.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Sql(statements = {
        "DROP TABLE IF EXISTS schedule",
        "DROP TABLE IF EXISTS study_member",
        "DROP TABLE IF EXISTS study",
        "DROP TABLE IF EXISTS apartment",
        "CREATE TABLE apartment (id BIGINT PRIMARY KEY, name VARCHAR(200) NOT NULL)",
        "CREATE TABLE study (id BIGINT PRIMARY KEY, apartment_id BIGINT NOT NULL, leader_id BIGINT NOT NULL, title VARCHAR(200), status VARCHAR(20) NOT NULL, deleted_at TIMESTAMP WITH TIME ZONE)",
        "CREATE TABLE study_member (id BIGINT PRIMARY KEY, study_id BIGINT NOT NULL, member_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL)",
        "CREATE TABLE schedule (id BIGINT PRIMARY KEY, study_id BIGINT NOT NULL, start_at TIMESTAMP WITH TIME ZONE NOT NULL, status VARCHAR(20) NOT NULL)"
})
@Sql(
        statements = {
                "DROP TABLE IF EXISTS schedule",
                "DROP TABLE IF EXISTS study_member",
                "DROP TABLE IF EXISTS study",
                "DROP TABLE IF EXISTS apartment"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class ScheduleRepositoryTest {

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 월_범위와_참여_상태를_필터링하고_시각과_ID순으로_반환한다() {
        insertApartment(100L, "리더 아파트");
        insertApartment(101L, "참여자 아파트");
        insertApartment(102L, "제외 아파트");

        insertStudy(10L, 100L, 1L, "리더 스터디", "IN_PROGRESS", null);
        insertStudy(11L, 101L, 2L, "참여자 스터디", "CLOSED", null);
        insertStudy(12L, 102L, 2L, "탈퇴 스터디", "CLOSED", null);
        insertStudy(13L, 102L, 1L, "취소 스터디", "CANCELED", null);
        insertStudy(
                14L,
                102L,
                1L,
                "삭제 스터디",
                "CLOSED",
                OffsetDateTime.parse("2026-07-01T00:00:00+09:00")
        );

        insertStudyMember(1000L, 11L, 1L, "ACTIVE");
        insertStudyMember(1001L, 12L, 1L, "REMOVED");

        insertSchedule(1L, 10L, "2026-07-01T00:00:00+09:00", "SCHEDULED");
        insertSchedule(2L, 11L, "2026-07-15T19:00:00+09:00", "COMPLETED");
        insertSchedule(3L, 10L, "2026-07-15T19:00:00+09:00", "SCHEDULED");
        insertSchedule(4L, 10L, "2026-08-01T00:00:00+09:00", "SCHEDULED");
        insertSchedule(5L, 10L, "2026-07-20T10:00:00+09:00", "CANCELED");
        insertSchedule(6L, 12L, "2026-07-21T10:00:00+09:00", "SCHEDULED");
        insertSchedule(7L, 13L, "2026-07-22T10:00:00+09:00", "SCHEDULED");
        insertSchedule(8L, 14L, "2026-07-23T10:00:00+09:00", "SCHEDULED");

        List<VisitCalendarRow> rows = scheduleRepository.findVisitCalendar(
                1L,
                Instant.parse("2026-06-30T15:00:00Z"),
                Instant.parse("2026-07-31T15:00:00Z")
        );

        assertThat(rows)
                .extracting(VisitCalendarRow::getScheduleId)
                .containsExactly(1L, 2L, 3L);
        assertThat(rows.get(0).getStudyTitle()).isEqualTo("리더 스터디");
        assertThat(rows.get(1).getApartmentName()).isEqualTo("참여자 아파트");
        assertThat(rows.get(1).getScheduleStatus()).isEqualTo("COMPLETED");
    }

    private void insertApartment(Long id, String name) {
        jdbcTemplate.update(
                "INSERT INTO apartment (id, name) VALUES (?, ?)",
                id,
                name
        );
    }

    private void insertStudy(
            Long id,
            Long apartmentId,
            Long leaderId,
            String title,
            String status,
            OffsetDateTime deletedAt
    ) {
        jdbcTemplate.update(
                "INSERT INTO study (id, apartment_id, leader_id, title, status, deleted_at) VALUES (?, ?, ?, ?, ?, ?)",
                id,
                apartmentId,
                leaderId,
                title,
                status,
                deletedAt
        );
    }

    private void insertStudyMember(
            Long id,
            Long studyId,
            Long memberId,
            String status
    ) {
        jdbcTemplate.update(
                "INSERT INTO study_member (id, study_id, member_id, status) VALUES (?, ?, ?, ?)",
                id,
                studyId,
                memberId,
                status
        );
    }

    private void insertSchedule(
            Long id,
            Long studyId,
            String startAt,
            String status
    ) {
        jdbcTemplate.update(
                "INSERT INTO schedule (id, study_id, start_at, status) VALUES (?, ?, ?, ?)",
                id,
                studyId,
                OffsetDateTime.parse(startAt),
                status
        );
    }
}
