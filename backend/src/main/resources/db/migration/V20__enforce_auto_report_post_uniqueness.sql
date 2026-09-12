-- A report owns exactly one automatic information post for its entire history.
-- The former index allowed a second row after soft deletion, which broke the
-- report-id idempotency key used by the completion event listener.
DROP INDEX IF EXISTS uq_post_auto_report_per_report;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM post
        WHERE is_auto_report = TRUE
          AND report_id IS NOT NULL
        GROUP BY report_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce uq_post_auto_report_per_report: duplicate automatic report posts exist';
    END IF;
END
$$;

CREATE UNIQUE INDEX uq_post_auto_report_per_report
    ON post (report_id)
    WHERE is_auto_report = TRUE
      AND report_id IS NOT NULL;
