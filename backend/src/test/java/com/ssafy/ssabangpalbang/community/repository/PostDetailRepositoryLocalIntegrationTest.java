package com.ssafy.ssabangpalbang.community.repository;

import com.ssafy.ssabangpalbang.community.repository.projection.PostDetailRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostInteractionRow;
import com.ssafy.ssabangpalbang.community.service.PostQueryService;
import com.ssafy.ssabangpalbang.community.support.PostResponseAssembler;
import com.ssafy.ssabangpalbang.media.service.MediaFileQueryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(
        named = "RUN_LOCAL_INFRA_TESTS",
        matches = "true"
)
@Import({
        PostCommandRepositoryImpl.class,
        PostQueryRepository.class,
        PostQueryService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostDetailRepositoryLocalIntegrationTest {

    @Autowired
    private PostCommandRepository postCommandRepository;

    @Autowired
    private PostQueryRepository postQueryRepository;

    @Autowired
    private PostQueryService postQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MediaFileQueryPort mediaFileQueryPort;

    @MockitoBean
    private PostResponseAssembler postResponseAssembler;

    @Test
    void concurrentIncrementsAreAtomicAndReturnDistinctUpdatedCounts()
            throws Exception {
        Long postId = insertPost(0L);
        int requestCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return postCommandRepository
                            .incrementViewCountIfVisible(postId)
                            .orElseThrow();
                }));
            }
            start.countDown();

            Set<Long> returnedCounts = new HashSet<>();
            for (Future<Long> future : futures) {
                returnedCounts.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(returnedCounts)
                    .containsExactlyInAnyOrderElementsOf(
                            java.util.stream.LongStream
                                    .rangeClosed(1, requestCount)
                                    .boxed()
                                    .toList()
                    );
            assertThat(currentViewCount(postId))
                    .isEqualTo(requestCount);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void hiddenAndDeletedPostsAreNotIncremented() {
        Long hiddenPostId = insertPost(0L);
        Long deletedPostId = insertPost(0L);
        jdbcTemplate.update(
                "UPDATE post SET status = 'HIDDEN' WHERE id = ?",
                hiddenPostId
        );
        jdbcTemplate.update(
                "UPDATE post SET deleted_at = now() WHERE id = ?",
                deletedPostId
        );

        assertThat(postCommandRepository
                .incrementViewCountIfVisible(hiddenPostId))
                .isEmpty();
        assertThat(postCommandRepository
                .incrementViewCountIfVisible(deletedPostId))
                .isEmpty();
        assertThat(currentViewCount(hiddenPostId)).isZero();
        assertThat(currentViewCount(deletedPostId)).isZero();
    }

    @Test
    void rollbackCancelsIncrementAndHotProjectionUsesUpdatedCount() {
        Long rollbackPostId = insertPost(0L);
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT author_id FROM post WHERE id = ?",
                Long.class,
                rollbackPostId
        );
        when(mediaFileQueryPort.findAllById(List.of()))
                .thenReturn(List.of());
        when(postResponseAssembler.assembleDetail(
                any(),
                anyLong(),
                anyLong(),
                any(PostInteractionRow.class),
                anyList(),
                anyList()
        )).thenThrow(new IllegalStateException("response assembly failed"));

        assertThatThrownBy(() ->
                postQueryService.getDetail(memberId, rollbackPostId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(currentViewCount(rollbackPostId)).isZero();

        Long hotPostId = insertPost(3L);
        long updatedCount = postCommandRepository
                .incrementViewCountIfVisible(hotPostId)
                .orElseThrow();
        PostDetailRow row = postQueryRepository
                .findVisibleDetail(hotPostId)
                .orElseThrow();
        PostInteractionRow interactions = postQueryRepository
                .findInteractions(hotPostId, memberId)
                .orElseThrow();

        assertThat(updatedCount).isEqualTo(4L);
        assertThat(row.postId()).isEqualTo(hotPostId);
        assertThat(interactions.hot()).isTrue();
        assertThat(interactions.hotScore())
                .isGreaterThanOrEqualTo(new BigDecimal("20.00"));
        assertThat(interactions.hotRank()).isNotNull();
    }

    private Long insertPost(long viewCount) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        Long memberId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO member (
                            email, password_hash, nickname, status
                        )
                        VALUES (?, 'encoded-password', ?, 'ACTIVE')
                        RETURNING id
                        """,
                Long.class,
                "post-detail-" + suffix + "@example.com",
                "detail-" + suffix
        );
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO post (
                            board_type, author_id, title, content,
                            status, is_auto_report, view_count
                        )
                        VALUES (
                            'FREE', ?, '상세 조회 테스트', '상세 조회 본문',
                            'ACTIVE', FALSE, ?
                        )
                        RETURNING id
                        """,
                Long.class,
                memberId,
                viewCount
        );
    }

    private long currentViewCount(Long postId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT view_count FROM post WHERE id = ?",
                Long.class,
                postId
        );
        return count == null ? 0L : count;
    }
}
