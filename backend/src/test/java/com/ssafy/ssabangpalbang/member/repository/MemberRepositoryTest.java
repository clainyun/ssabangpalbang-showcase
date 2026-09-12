package com.ssafy.ssabangpalbang.member.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Sql(statements = {
        "DROP TABLE IF EXISTS report",
        "DROP TABLE IF EXISTS study_member",
        "DROP TABLE IF EXISTS study",
        "DROP TABLE IF EXISTS follow",
        "DROP TABLE IF EXISTS member_preference",
        "CREATE TABLE member_preference (member_id BIGINT PRIMARY KEY)",
        "CREATE TABLE study (id BIGINT PRIMARY KEY, leader_id BIGINT NOT NULL, deleted_at TIMESTAMP WITH TIME ZONE)",
        "CREATE TABLE study_member (id BIGINT PRIMARY KEY, study_id BIGINT NOT NULL, member_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL)",
        "CREATE TABLE report (id BIGINT PRIMARY KEY, study_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL)",
        "CREATE TABLE follow (id BIGINT PRIMARY KEY, follower_id BIGINT NOT NULL, following_id BIGINT NOT NULL)"
})
@Sql(
        statements = {
                "DROP TABLE IF EXISTS report",
                "DROP TABLE IF EXISTS study_member",
                "DROP TABLE IF EXISTS study",
                "DROP TABLE IF EXISTS follow",
                "DROP TABLE IF EXISTS member_preference"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 회원_선호정보_존재_여부를_조회한다() {
        assertThat(memberRepository.existsPreferenceByMemberId(1L))
                .isFalse();

        jdbcTemplate.update(
                "INSERT INTO member_preference (member_id) VALUES (?)",
                1L
        );

        assertThat(memberRepository.existsPreferenceByMemberId(1L))
                .isTrue();
    }

    @Test
    void 내_프로필_요약수를_접근_가능한_데이터로_집계한다() {
        jdbcTemplate.update(
                "INSERT INTO study (id, leader_id, deleted_at) VALUES (?, ?, ?)",
                10L,
                1L,
                null
        );
        jdbcTemplate.update(
                "INSERT INTO study (id, leader_id, deleted_at) VALUES (?, ?, ?)",
                11L,
                2L,
                null
        );
        jdbcTemplate.update(
                "INSERT INTO study (id, leader_id, deleted_at) VALUES (?, ?, ?)",
                12L,
                2L,
                null
        );
        jdbcTemplate.update(
                "INSERT INTO study (id, leader_id, deleted_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                13L,
                1L
        );
        jdbcTemplate.update(
                "INSERT INTO study_member (id, study_id, member_id, status) VALUES (?, ?, ?, ?)",
                100L,
                11L,
                1L,
                "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO study_member (id, study_id, member_id, status) VALUES (?, ?, ?, ?)",
                101L,
                12L,
                1L,
                "REMOVED"
        );
        jdbcTemplate.update(
                "INSERT INTO report (id, study_id, status) VALUES (?, ?, ?)",
                200L,
                10L,
                "DONE"
        );
        jdbcTemplate.update(
                "INSERT INTO report (id, study_id, status) VALUES (?, ?, ?)",
                201L,
                11L,
                "DONE"
        );
        jdbcTemplate.update(
                "INSERT INTO report (id, study_id, status) VALUES (?, ?, ?)",
                202L,
                11L,
                "PROCESSING"
        );
        jdbcTemplate.update(
                "INSERT INTO report (id, study_id, status) VALUES (?, ?, ?)",
                203L,
                12L,
                "DONE"
        );
        jdbcTemplate.update(
                "INSERT INTO report (id, study_id, status) VALUES (?, ?, ?)",
                204L,
                13L,
                "DONE"
        );
        jdbcTemplate.update(
                "INSERT INTO follow (id, follower_id, following_id) VALUES (?, ?, ?)",
                300L,
                1L,
                2L
        );
        jdbcTemplate.update(
                "INSERT INTO follow (id, follower_id, following_id) VALUES (?, ?, ?)",
                301L,
                1L,
                3L
        );
        jdbcTemplate.update(
                "INSERT INTO follow (id, follower_id, following_id) VALUES (?, ?, ?)",
                302L,
                2L,
                1L
        );

        assertThat(memberRepository.countAccessibleStudies(1L))
                .isEqualTo(2L);
        assertThat(memberRepository.countAccessibleDoneReports(1L))
                .isEqualTo(2L);
        assertThat(memberRepository.countFollowing(1L)).isEqualTo(2L);
    }
}
