package com.ssafy.ssabangpalbang.community.repository;

import com.ssafy.ssabangpalbang.community.domain.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * JDBC boundary for the report-completion side effect.  The insert deliberately
 * uses the same predicate as the partial unique index so duplicate events are
 * a no-op without changing the original automatic post.
 */
@Repository
@RequiredArgsConstructor
public class AutoReportPostRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<CompletedReportSource> findCompletedReportSource(
            Long reportId
    ) {
        return jdbcTemplate.query(
                """
                        SELECT r.id AS report_id,
                               r.apartment_id,
                               a.name AS apartment_name,
                               fs.started_at AS field_session_started_at,
                               r.result_json ->> 'title' AS report_title,
                               r.result_json ->> 'summary' AS report_summary
                        FROM report r
                        JOIN apartment a ON a.id = r.apartment_id
                        JOIN field_session fs ON fs.id = r.field_session_id
                        WHERE r.id = ?
                          AND r.status = 'DONE'
                        """,
                (resultSet, rowNumber) -> new CompletedReportSource(
                        resultSet.getLong("report_id"),
                        resultSet.getLong("apartment_id"),
                        resultSet.getString("apartment_name"),
                        resultSet.getTimestamp(
                                "field_session_started_at"
                        ).toInstant(),
                        resultSet.getString("report_title"),
                        resultSet.getString("report_summary")
                ),
                reportId
        ).stream().findFirst();
    }

    /**
     * @return 1 when this invocation created the post, otherwise 0 when an
     * existing automatic post already owns the report id.
     */
    public int insertIfAbsent(Post post) {
        return jdbcTemplate.update(
                """
                        INSERT INTO post (
                            board_type,
                            author_id,
                            title,
                            content,
                            status,
                            is_auto_report,
                            report_id,
                            apartment_id,
                            view_count,
                            created_at,
                            updated_at
                        )
                        VALUES (?, NULL, ?, ?, 'ACTIVE', TRUE, ?, ?, 0, NOW(), NOW())
                        ON CONFLICT (report_id)
                        WHERE is_auto_report = TRUE
                          AND report_id IS NOT NULL
                        DO NOTHING
                        """,
                post.getBoardType().name(),
                post.getTitle(),
                post.getContent(),
                post.getReportId(),
                post.getApartmentId()
        );
    }

    public record CompletedReportSource(
            Long reportId,
            Long apartmentId,
            String apartmentName,
            Instant fieldSessionStartedAt,
            String title,
            String summary
    ) {
    }
}
