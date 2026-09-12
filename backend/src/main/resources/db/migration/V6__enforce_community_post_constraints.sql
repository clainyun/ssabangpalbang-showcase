DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM post_attachment
        GROUP BY file_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce uq_post_attachment_file: duplicate file_id rows exist';
    END IF;
END
$$;

CREATE UNIQUE INDEX uq_post_attachment_file
    ON post_attachment (file_id);

ALTER TABLE post
    ADD CONSTRAINT ck_post_board_type
        CHECK (board_type IN ('INFORMATION', 'FREE')),
    ADD CONSTRAINT ck_post_status
        CHECK (status IN ('ACTIVE', 'HIDDEN')),
    ADD CONSTRAINT ck_post_title_not_blank
        CHECK (char_length(btrim(title)) BETWEEN 1 AND 200),
    ADD CONSTRAINT ck_post_content_length
        CHECK (
            content IS NOT NULL
            AND char_length(btrim(content)) BETWEEN 1 AND 5000
        ),
    ADD CONSTRAINT ck_post_origin
        CHECK (
            (
                NOT is_auto_report
                AND author_id IS NOT NULL
                AND report_id IS NULL
            )
            OR (
                is_auto_report
                AND board_type = 'INFORMATION'
                AND report_id IS NOT NULL
            )
        );
