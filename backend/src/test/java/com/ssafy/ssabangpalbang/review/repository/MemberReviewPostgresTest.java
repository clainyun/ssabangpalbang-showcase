package com.ssafy.ssabangpalbang.review.repository;

import com.ssafy.ssabangpalbang.review.domain.MemberReview;
import com.ssafy.ssabangpalbang.review.repository.projection.MemberReviewTagCountRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
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

@Tag("postgres")
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers
class MemberReviewPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = buildContainer();

    private static PostgreSQLContainer<?> buildContainer() {
        ImageFromDockerfile image = new ImageFromDockerfile(
                "ssabangpalbang-postgres-test",
                false
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
        registry.add(
                "spring.kafka.bootstrap-servers",
                () -> "127.0.0.1:9092"
        );
    }

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MemberReviewRepository memberReviewRepository;
    @Autowired MemberReviewTagRepository memberReviewTagRepository;

    private Long studyId;
    private Long reviewerId;
    private Long revieweeId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM member_review_tag");
        jdbcTemplate.update("DELETE FROM member_review");
        jdbcTemplate.update("DELETE FROM study_member");
        jdbcTemplate.update("DELETE FROM study");
        jdbcTemplate.update("DELETE FROM apartment");
        jdbcTemplate.update("DELETE FROM member");

        reviewerId = insertMember("reviewer@example.com", "평가자");
        revieweeId = insertMember("reviewee@example.com", "평가대상");
        Long apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (
                    complex_code, name, longitude, latitude
                ) VALUES ('BE028', '테스트 아파트', 127.0, 37.5)
                RETURNING id
                """, Long.class);
        studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (
                    apartment_id, leader_id, title, goal, capacity, status
                ) VALUES (?, ?, '평가 테스트 스터디', '평가 검증', 4, 'COMPLETED')
                RETURNING id
                """, Long.class, apartmentId, reviewerId);
    }

    @Test
    void V32가_적용되고_태그_좋아요_평가를_저장한다() {
        Long reviewId = insertReview(reviewerId, revieweeId, true);
        insertTag(reviewId, "PUNCTUAL");

        Integer v32Applied = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '32'
                  AND success = TRUE
                """, Integer.class);
        Integer storedTags = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_review_tag WHERE review_id = ?",
                Integer.class,
                reviewId
        );

        assertThat(v32Applied).isEqualTo(1);
        assertThat(storedTags).isEqualTo(1);
        assertThat(memberReviewRepository.countByRevieweeIdAndLikedTrue(
                revieweeId
        )).isEqualTo(1L);
    }

    @Test
    void PostgreSQL이_중복_본인_평가와_별점_범위를_강제한다() {
        insertReview(reviewerId, revieweeId, true);

        assertThatThrownBy(() -> insertReview(reviewerId, revieweeId, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertReview(reviewerId, reviewerId, true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertReviewWithRating(revieweeId, reviewerId, 6))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void PostgreSQL에서_평가수_좋아요수_태그_집계와_커서_정렬을_조회한다() {
        Long reviewer2 = insertMember("reviewer2@example.com", "평가자2");
        Long reviewer3 = insertMember("reviewer3@example.com", "평가자3");
        Instant newer = Instant.parse("2026-08-03T05:00:00Z");
        Instant older = Instant.parse("2026-08-02T05:00:00Z");

        Long olderId = insertReviewAt(reviewerId, revieweeId, false, older);
        Long newerLowerId = insertReviewAt(reviewer2, revieweeId, true, newer);
        Long newerHigherId = insertReviewAt(reviewer3, revieweeId, true, newer);
        insertTag(newerHigherId, "PUNCTUAL");
        insertTag(newerLowerId, "PUNCTUAL");
        insertTag(olderId, "THOROUGH");

        List<MemberReviewTagCountRow> tagCounts =
                memberReviewRepository.aggregateTagCountsByRevieweeId(
                        revieweeId
                );
        List<MemberReview> firstPage =
                memberReviewRepository.findFirstPageByRevieweeId(
                        revieweeId,
                        PageRequest.of(0, 2)
                );
        List<MemberReview> nextPage =
                memberReviewRepository.findNextPageByRevieweeId(
                        revieweeId,
                        firstPage.get(1).getCreatedAt(),
                        firstPage.get(1).getId(),
                        PageRequest.of(0, 2)
                );

        assertThat(memberReviewRepository.countByRevieweeId(revieweeId))
                .isEqualTo(3L);
        assertThat(memberReviewRepository.countByRevieweeIdAndLikedTrue(
                revieweeId
        )).isEqualTo(2L);
        assertThat(tagCounts).extracting(MemberReviewTagCountRow::getTagCode)
                .containsExactly("PUNCTUAL", "THOROUGH");
        assertThat(tagCounts.get(0).getTagCount()).isEqualTo(2L);
        assertThat(firstPage).extracting(MemberReview::getId)
                .containsExactly(newerHigherId, newerLowerId);
        assertThat(nextPage).extracting(MemberReview::getId)
                .containsExactly(olderId);
    }

    private Long insertMember(String email, String nickname) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (email, password_hash, nickname)
                VALUES (?, 'hash', ?)
                RETURNING id
                """, Long.class, email, nickname);
    }

    private Long insertReview(Long reviewer, Long reviewee, boolean liked) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member_review (
                    study_id, reviewer_id, reviewee_id, liked, content
                ) VALUES (?, ?, ?, ?, '좋은 팀원이었어요.')
                RETURNING id
                """, Long.class, studyId, reviewer, reviewee, liked);
    }

    private Long insertReviewWithRating(Long reviewer, Long reviewee, int rating) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member_review (
                    study_id, reviewer_id, reviewee_id, rating, liked, content
                ) VALUES (?, ?, ?, ?, false, '좋은 팀원이었어요.')
                RETURNING id
                """, Long.class, studyId, reviewer, reviewee, rating);
    }

    private Long insertReviewAt(
            Long reviewer,
            Long reviewee,
            boolean liked,
            Instant createdAt
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member_review (
                    study_id,
                    reviewer_id,
                    reviewee_id,
                    liked,
                    content,
                    created_at
                ) VALUES (?, ?, ?, ?, '익명 평가입니다.', ?)
                RETURNING id
                """, Long.class,
                studyId,
                reviewer,
                reviewee,
                liked,
                Timestamp.from(createdAt)
        );
    }

    private void insertTag(Long reviewId, String tagCode) {
        jdbcTemplate.update("""
                INSERT INTO member_review_tag (review_id, tag_code)
                VALUES (?, ?)
                """, reviewId, tagCode);
    }
}
