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
        "DROP TABLE IF EXISTS study",
        "CREATE TABLE study ("
                + "id BIGINT PRIMARY KEY, "
                + "leader_id BIGINT NOT NULL, "
                + "title VARCHAR(200), "
                + "status VARCHAR(20) NOT NULL, "
                + "deleted_at TIMESTAMP WITH TIME ZONE)",
        "CREATE TABLE schedule ("
                + "id BIGINT PRIMARY KEY, "
                + "study_id BIGINT NOT NULL, "
                + "start_at TIMESTAMP NOT NULL, "
                + "meeting_place VARCHAR(200), "
                + "status VARCHAR(20) NOT NULL)"
})
class ScheduleReminderQueryTest {

    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    @Autowired
    private ScheduleRepository scheduleRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void Q1_startAt이_now와_같으면_제외한다() {
        insertStudy(10L, "RECRUITING", null);
        insertSchedule(1L, 10L, NOW, "SCHEDULED", "옥수역");

        assertThat(find()).isEmpty();
    }

    @Test
    void Q2_startAt이_now_더하기_24시간이면_포함한다() {
        insertStudy(10L, "RECRUITING", null);
        insertSchedule(
                1L,
                10L,
                NOW.plusSeconds(24 * 3600),
                "SCHEDULED",
                "옥수역"
        );

        assertThat(find()).hasSize(1);
    }

    @Test
    void Q3_startAt_ASC_ID_ASC로_정렬한다() {
        insertStudy(10L, "IN_PROGRESS", null);
        insertSchedule(3L, 10L, NOW.plusSeconds(7200), "SCHEDULED", "C");
        insertSchedule(2L, 10L, NOW.plusSeconds(3600), "SCHEDULED", "B");
        insertSchedule(1L, 10L, NOW.plusSeconds(3600), "SCHEDULED", "A");

        assertThat(find())
                .extracting(ScheduleReminderRow::getScheduleId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void Q4_프로젝션_6개_필드를_모두_채운다() {
        insertStudy(10L, "CLOSED", null);
        insertSchedule(7L, 10L, NOW.plusSeconds(3600), "SCHEDULED", "옥수역");

        ScheduleReminderRow row = find().get(0);

        assertThat(row.getScheduleId()).isEqualTo(7L);
        assertThat(row.getStudyId()).isEqualTo(10L);
        assertThat(row.getStudyTitle()).isEqualTo("검증 스터디");
        assertThat(row.getLeaderId()).isEqualTo(1L);
        assertThat(row.getStartAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(row.getMeetingPlace()).isEqualTo("옥수역");
    }

    @Test
    void Q5_meetingPlace가_null이어도_조회한다() {
        insertStudy(10L, "RECRUITING", null);
        insertSchedule(7L, 10L, NOW.plusSeconds(3600), "SCHEDULED", null);

        assertThat(find().get(0).getMeetingPlace()).isNull();
    }

    private List<ScheduleReminderRow> find() {
        return scheduleRepository.findRemindableSchedules(
                NOW,
                NOW.plusSeconds(24 * 3600)
        );
    }

    private void insertStudy(
            Long studyId,
            String status,
            OffsetDateTime deletedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO study (
                    id, leader_id, title, status, deleted_at
                ) VALUES (?, 1, '검증 스터디', ?, ?)
                """,
                studyId,
                status,
                deletedAt
        );
    }

    private void insertSchedule(
            Long scheduleId,
            Long studyId,
            Instant startAt,
            String status,
            String meetingPlace
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO schedule (
                    id, study_id, start_at, status, meeting_place
                ) VALUES (?, ?, ?, ?, ?)
                """,
                scheduleId,
                studyId,
                startAt,
                status,
                meetingPlace
        );
    }
}
