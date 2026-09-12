package com.ssafy.ssabangpalbang.community.service;

import com.ssafy.ssabangpalbang.community.dto.request.CommentCreateRequest;
import com.ssafy.ssabangpalbang.community.dto.response.CommentCreateResponse;
import com.ssafy.ssabangpalbang.community.repository.CommentCommandRepository;
import com.ssafy.ssabangpalbang.community.repository.CommentQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.PostQueryRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Testcontainers
@Import({
        CommentCommandService.class,
        PostQueryRepository.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CommentCreatePostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = buildContainer();

    private static PostgreSQLContainer<?> buildContainer() {
        ImageFromDockerfile image = new ImageFromDockerfile(
                "ssabangpalbang-comment-create-test",
                false
        ).withDockerfile(Path.of(
                "..",
                "infra",
                "postgres",
                "Dockerfile"
        ));
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
    static void registerDynamicProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );
        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
        registry.add(
                "spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver"
        );
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration"
        );
    }

    @Autowired
    private CommentCommandService commentCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private CommentCommandRepository commentCommandRepository;

    @MockitoBean
    private CommentQueryRepository commentQueryRepository;

    @MockitoBean
    private Clock clock;

    @Test
    void persistedCommentIsVisibleToCurrentMetricsAndHotView() {
        Long memberId = insertMember();
        Long postId = insertPost(memberId);

        CommentCreateResponse response = commentCommandService.create(
                memberId,
                postId,
                new CommentCreateRequest("  실제 PostgreSQL 댓글  ")
        );

        assertThat(response.comment().content())
                .isEqualTo("실제 PostgreSQL 댓글");
        assertThat(response.postMetrics().commentCount()).isEqualTo(1L);
        assertThat(response.postMetrics().likeCount()).isZero();
        assertThat(response.postMetrics().viewCount()).isEqualTo(1L);
        assertThat(response.postMetrics().isHot()).isTrue();
        assertThat(response.postMetrics().hotScore())
                .isGreaterThanOrEqualTo(new BigDecimal("20.0"));

        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT content
                        FROM post_comment
                        WHERE post_id = ?
                          AND author_id = ?
                          AND deleted_at IS NULL
                        """,
                String.class,
                postId,
                memberId
        )).isEqualTo("실제 PostgreSQL 댓글");
    }

    private Long insertMember() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO member (email, nickname)
                        VALUES (?, ?)
                        RETURNING id
                        """,
                Long.class,
                "comment-create-" + suffix + "@example.com",
                "comment-" + suffix
        );
    }

    private Long insertPost(Long memberId) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO post (
                            board_type,
                            author_id,
                            title,
                            content,
                            status,
                            is_auto_report,
                            view_count
                        )
                        VALUES (
                            'FREE',
                            ?,
                            '댓글 작성 통합 테스트',
                            '본문',
                            'ACTIVE',
                            FALSE,
                            1
                        )
                        RETURNING id
                        """,
                Long.class,
                memberId
        );
    }
}
