package com.ssafy.ssabangpalbang.community.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ssafy.ssabangpalbang.apartment.domain.QApartment;
import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.domain.PostStatus;
import com.ssafy.ssabangpalbang.community.domain.QPost;
import com.ssafy.ssabangpalbang.community.domain.QPostHotMetric;
import com.ssafy.ssabangpalbang.community.dto.request.PostSort;
import com.ssafy.ssabangpalbang.community.repository.projection.PostDetailRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostInteractionRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostListCardRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostListKeyRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostReactionCountRow;
import com.ssafy.ssabangpalbang.community.support.PostCursor;
import com.ssafy.ssabangpalbang.member.domain.QMember;
import com.ssafy.ssabangpalbang.report.domain.QReport;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class PostQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final JdbcTemplate jdbcTemplate;

    public PostQueryRepository(
            EntityManager entityManager,
            JdbcTemplate jdbcTemplate
    ) {
        this.queryFactory = new JPAQueryFactory(entityManager);
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PostDetailRow> findVisibleDetail(Long postId) {
        QPost post = QPost.post;
        QMember author = QMember.member;
        QApartment apartment = QApartment.apartment;
        QReport report = QReport.report;

        PostDetailRow row = queryFactory
                .select(Projections.constructor(
                        PostDetailRow.class,
                        post.id,
                        post.boardType,
                        post.title,
                        post.content,
                        post.status,
                        post.autoReport,
                        post.authorId,
                        author.nickname,
                        author.profileImageUrl,
                        author.selectedCharacterId,
                        author.status,
                        author.deletedAt,
                        apartment.id,
                        apartment.name,
                        apartment.address,
                        post.reportId,
                        report.status,
                        post.createdAt,
                        post.updatedAt
                ))
                .from(post)
                .leftJoin(author).on(author.id.eq(post.authorId))
                .leftJoin(apartment).on(apartment.id.eq(post.apartmentId))
                .leftJoin(report).on(report.id.eq(post.reportId))
                .where(
                        post.id.eq(postId),
                        post.status.eq(PostStatus.ACTIVE),
                        post.deletedAt.isNull()
                )
                .fetchOne();

        return Optional.ofNullable(row);
    }

    public Optional<PostInteractionRow> findInteractions(
            Long postId,
            Long memberId
    ) {
        return jdbcTemplate.query(
                """
                        SELECT
                            (
                                SELECT COUNT(*)
                                FROM post_like post_like_count
                                WHERE post_like_count.post_id = post.id
                            ) AS like_count,
                            (
                                SELECT COUNT(*)
                                FROM post_comment post_comment_count
                                WHERE post_comment_count.post_id = post.id
                                  AND post_comment_count.deleted_at IS NULL
                            ) AS comment_count,
                            EXISTS (
                                SELECT 1
                                FROM post_like my_like
                                WHERE my_like.post_id = post.id
                                  AND my_like.member_id = ?
                            ) AS liked_by_me,
                            metric.hot_score,
                            metric.is_hot,
                            metric.hot_rank
                        FROM post
                        LEFT JOIN post_hot_metric metric
                          ON metric.post_id = post.id
                        WHERE post.id = ?
                        """,
                (resultSet, rowNumber) -> new PostInteractionRow(
                        resultSet.getLong("like_count"),
                        resultSet.getLong("comment_count"),
                        resultSet.getBoolean("liked_by_me"),
                        resultSet.getBigDecimal("hot_score"),
                        resultSet.getObject("is_hot", Boolean.class),
                        resultSet.getObject("hot_rank", Long.class)
                ),
                memberId,
                postId
        ).stream().findFirst();
    }

    public List<PostListKeyRow> findListKeys(
            BoardType boardType,
            PostSort sort,
            String keyword,
            PostCursor cursor,
            int limit
    ) {
        if (sort == PostSort.HOT) {
            return findHotKeys(
                    boardType,
                    keyword,
                    cursor,
                    limit
            );
        }
        return findLatestKeys(
                boardType,
                keyword,
                cursor,
                limit
        );
    }

    public List<PostListCardRow> findListCards(
            Collection<Long> postIds
    ) {
        if (postIds.isEmpty()) {
            return List.of();
        }

        QPost post = QPost.post;
        QMember author = QMember.member;
        QApartment apartment = QApartment.apartment;
        QReport report = QReport.report;
        QPostHotMetric metric = QPostHotMetric.postHotMetric;

        return queryFactory
                .select(Projections.constructor(
                        PostListCardRow.class,
                        post.id,
                        post.boardType,
                        post.title,
                        post.content,
                        post.autoReport,
                        post.authorId,
                        author.nickname,
                        author.profileImageUrl,
                        author.selectedCharacterId,
                        author.status,
                        author.deletedAt,
                        apartment.id,
                        apartment.name,
                        post.reportId,
                        report.status,
                        post.viewCount,
                        metric.hotScore,
                        metric.hot,
                        metric.hotRank,
                        post.createdAt,
                        post.updatedAt
                ))
                .from(post)
                .leftJoin(author).on(author.id.eq(post.authorId))
                .leftJoin(apartment).on(apartment.id.eq(post.apartmentId))
                .leftJoin(report).on(report.id.eq(post.reportId))
                .leftJoin(metric).on(metric.postId.eq(post.id))
                .where(
                        post.id.in(postIds),
                        visible(post)
                )
                .fetch();
    }

    public List<PostReactionCountRow> findReactionCounts(
            Collection<Long> postIds,
            Long memberId
    ) {
        if (postIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(
                ", ",
                Collections.nCopies(postIds.size(), "?")
        );
        String sql = """
                SELECT
                    selected.id AS post_id,
                    (
                        SELECT COUNT(*)
                        FROM post_like like_count
                        WHERE like_count.post_id = selected.id
                    ) AS like_count,
                    (
                        SELECT COUNT(*)
                        FROM post_comment comment_count
                        WHERE comment_count.post_id = selected.id
                          AND comment_count.deleted_at IS NULL
                    ) AS comment_count,
                    EXISTS (
                        SELECT 1
                        FROM post_like my_like
                        WHERE my_like.post_id = selected.id
                          AND my_like.member_id = ?
                    ) AS liked_by_me
                FROM post selected
                WHERE selected.id IN (%s)
                """.formatted(placeholders);

        List<Object> arguments = new ArrayList<>(postIds.size() + 1);
        arguments.add(memberId);
        arguments.addAll(postIds);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new PostReactionCountRow(
                        resultSet.getLong("post_id"),
                        resultSet.getLong("like_count"),
                        resultSet.getLong("comment_count"),
                        resultSet.getBoolean("liked_by_me")
                ),
                arguments.toArray()
        );
    }

    private List<PostListKeyRow> findLatestKeys(
            BoardType boardType,
            String keyword,
            PostCursor cursor,
            int limit
    ) {
        QPost post = QPost.post;

        return queryFactory
                .select(Projections.constructor(
                        PostListKeyRow.class,
                        post.id,
                        post.createdAt,
                        Expressions.nullExpression(
                                java.math.BigDecimal.class
                        )
                ))
                .from(post)
                .where(
                        visible(post),
                        boardTypeEquals(post, boardType),
                        keywordContains(post, keyword),
                        latestAfter(post, cursor)
                )
                .orderBy(
                        post.createdAt.desc(),
                        post.id.desc()
                )
                .limit(limit)
                .fetch();
    }

    private List<PostListKeyRow> findHotKeys(
            BoardType boardType,
            String keyword,
            PostCursor cursor,
            int limit
    ) {
        QPost post = QPost.post;
        QPostHotMetric metric = QPostHotMetric.postHotMetric;

        return queryFactory
                .select(Projections.constructor(
                        PostListKeyRow.class,
                        post.id,
                        metric.createdAt,
                        metric.hotScore
                ))
                .from(metric)
                .join(post).on(post.id.eq(metric.postId))
                .where(
                        visible(post),
                        metric.hot.isTrue(),
                        boardTypeEquals(post, boardType),
                        keywordContains(post, keyword),
                        hotAfter(metric, cursor)
                )
                .orderBy(
                        metric.hotScore.desc(),
                        metric.createdAt.desc(),
                        metric.postId.desc()
                )
                .limit(limit)
                .fetch();
    }

    private BooleanExpression visible(QPost post) {
        return post.status.eq(PostStatus.ACTIVE)
                .and(post.deletedAt.isNull());
    }

    private BooleanExpression boardTypeEquals(
            QPost post,
            BoardType boardType
    ) {
        return boardType == null
                ? null
                : post.boardType.eq(boardType);
    }

    private BooleanExpression keywordContains(
            QPost post,
            String keyword
    ) {
        if (keyword == null) {
            return null;
        }

        String pattern = "%" + escapeLike(keyword) + "%";
        return Expressions.booleanTemplate(
                "({0} || ' ' || coalesce({1}, '')) "
                        + "ilike {2} escape '\\'",
                post.title,
                post.content,
                pattern
        );
    }

    private String escapeLike(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private BooleanExpression latestAfter(
            QPost post,
            PostCursor cursor
    ) {
        if (cursor == null) {
            return null;
        }

        return post.createdAt.lt(cursor.createdAt())
                .or(post.createdAt.eq(cursor.createdAt())
                        .and(post.id.lt(cursor.postId())));
    }

    private BooleanExpression hotAfter(
            QPostHotMetric metric,
            PostCursor cursor
    ) {
        if (cursor == null) {
            return null;
        }

        return metric.hotScore.lt(cursor.hotScore())
                .or(metric.hotScore.eq(cursor.hotScore())
                        .and(metric.createdAt.lt(cursor.createdAt())))
                .or(metric.hotScore.eq(cursor.hotScore())
                        .and(metric.createdAt.eq(cursor.createdAt()))
                        .and(metric.postId.lt(cursor.postId())));
    }
}
