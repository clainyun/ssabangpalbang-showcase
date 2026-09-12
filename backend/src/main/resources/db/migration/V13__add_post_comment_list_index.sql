CREATE INDEX idx_post_comment_post_created_active
    ON post_comment (post_id, created_at, id)
    WHERE deleted_at IS NULL;
