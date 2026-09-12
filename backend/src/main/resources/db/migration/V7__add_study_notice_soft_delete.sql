ALTER TABLE study_notice
    ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX idx_study_notice_active_study_id_id
    ON study_notice (study_id, id DESC)
    WHERE deleted_at IS NULL;
