package com.ssafy.ssabangpalbang.community.repository;

import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.dto.request.PostSort;
import com.ssafy.ssabangpalbang.community.repository.projection.PostListKeyRow;
import com.ssafy.ssabangpalbang.community.support.PostCursor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
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
@Import(PostQueryRepository.class)
class PostListRepositoryLocalIntegrationTest {

    @Autowired
    private PostQueryRepository postQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void postgresSearchEscapesWildcardsAndLatestCursorUsesPostId() {
        Long memberId = insertMember();
        String marker = uniqueMarker();
        Instant sameCreatedAt = Instant.now()
                .minusSeconds(60)
                .truncatedTo(ChronoUnit.MICROS);
        Long olderId = insertPost(
                memberId,
                marker + " 100%_ 제목",
                "검색 본문",
                "ACTIVE",
                null,
                sameCreatedAt.minusSeconds(1),
                0L
        );
        Long lowerTieId = insertPost(
                memberId,
                marker + " 100%_ 두 번째",
                "검색 본문",
                "ACTIVE",
                null,
                sameCreatedAt,
                0L
        );
        Long higherTieId = insertPost(
                memberId,
                marker + " 100%_ 첫 번째",
                "검색 본문",
                "ACTIVE",
                null,
                sameCreatedAt,
                0L
        );
        insertPost(
                memberId,
                marker + " 100AB 오검색",
                "검색 제외",
                "ACTIVE",
                null,
                sameCreatedAt.plusSeconds(1),
                0L
        );
        insertPost(
                memberId,
                marker + " 100%_ 숨김",
                "검색 제외",
                "HIDDEN",
                null,
                sameCreatedAt.plusSeconds(2),
                0L
        );

        List<PostListKeyRow> firstPage =
                postQueryRepository.findListKeys(
                        BoardType.FREE,
                        PostSort.LATEST,
                        marker + " 100%_",
                        null,
                        2
                );
        assertThat(firstPage)
                .extracting(PostListKeyRow::postId)
                .containsExactly(higherTieId, lowerTieId);

        List<PostListKeyRow> secondPage =
                postQueryRepository.findListKeys(
                        BoardType.FREE,
                        PostSort.LATEST,
                        marker + " 100%_",
                        PostCursor.latest(
                                sameCreatedAt,
                                lowerTieId,
                                "hash"
                        ),
                        2
                );
        assertThat(secondPage)
                .extracting(PostListKeyRow::postId)
                .containsExactly(olderId);
    }

    @Test
    void postgresHotViewIncludesRoundedTwentyAndExcludesNineteenNinetyNine() {
        Long memberId = insertMember();
        String marker = uniqueMarker();
        Long includedId = insertPostAtDatabaseAge(
                memberId,
                marker + " 20.00",
                "8 hours",
                4L
        );
        insertPostAtDatabaseAge(
                memberId,
                marker + " 19.99",
                "8 hours 3 minutes 36 seconds",
                4L
        );

        List<PostListKeyRow> result =
                postQueryRepository.findListKeys(
                        BoardType.FREE,
                        PostSort.HOT,
                        marker,
                        null,
                        10
                );

        assertThat(result)
                .extracting(PostListKeyRow::postId)
                .containsExactly(includedId);
        assertThat(result.get(0).hotScore())
                .isEqualByComparingTo(new BigDecimal("20.00"));
    }

    private Long insertMember() {
        String marker = uniqueMarker();
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO member (
                            email, password_hash, nickname, status
                        )
                        VALUES (?, 'encoded-password', ?, 'ACTIVE')
                        RETURNING id
                        """,
                Long.class,
                marker + "@example.com",
                marker
        );
    }

    private Long insertPost(
            Long memberId,
            String title,
            String content,
            String status,
            Instant deletedAt,
            Instant createdAt,
            long viewCount
    ) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO post (
                            board_type, author_id, title, content,
                            status, is_auto_report, view_count,
                            deleted_at, created_at, updated_at
                        )
                        VALUES (
                            'FREE', ?, ?, ?, ?, FALSE, ?, ?, ?, ?
                        )
                        RETURNING id
                        """,
                Long.class,
                memberId,
                title,
                content,
                status,
                viewCount,
                deletedAt,
                createdAt,
                createdAt
        );
    }

    private Long insertPostAtDatabaseAge(
            Long memberId,
            String title,
            String age,
            long viewCount
    ) {
        String sql = """
                INSERT INTO post (
                    board_type, author_id, title, content,
                    status, is_auto_report, view_count,
                    created_at, updated_at
                )
                VALUES (
                    'FREE', ?, ?, 'HOT 경계 본문',
                    'ACTIVE', FALSE, ?,
                    now() - CAST(? AS INTERVAL),
                    now() - CAST(? AS INTERVAL)
                )
                RETURNING id
                """;
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                memberId,
                title,
                viewCount,
                age,
                age
        );
    }

    private String uniqueMarker() {
        return "post-list-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
    }
}
