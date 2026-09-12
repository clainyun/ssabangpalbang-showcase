-- BE-019: AI Report Worker create-or-get and processing lease state.
-- The opaque processing token is returned once and only its SHA-256 hash is stored.
ALTER TABLE report
    ADD COLUMN field_session_id BIGINT,
    ADD COLUMN processing_token_hash VARCHAR(64),
    ADD COLUMN processing_attempt INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN processing_lease_expires_at TIMESTAMPTZ;

UPDATE report r
SET field_session_id = fs.id
FROM field_session fs
WHERE fs.study_id = r.study_id
  AND r.field_session_id IS NULL;

-- A Report without its authoritative ended session cannot be processed safely.
-- Fail the migration instead of preserving an unrecoverable legacy row.
ALTER TABLE report
    ALTER COLUMN field_session_id SET NOT NULL;

UPDATE report
SET progress_stage = CASE progress_stage
    WHEN 'COLLECT' THEN 'RECORD_COLLECTION'
    WHEN 'STT' THEN 'STT_VALIDATION'
    WHEN 'ANALYZE' THEN 'REPORT_GENERATION'
    WHEN 'EVIDENCE' THEN 'EVIDENCE_MAPPING'
    WHEN 'DONE' THEN 'COMPLETED'
    ELSE progress_stage
END
WHERE progress_stage IN ('COLLECT', 'STT', 'ANALYZE', 'EVIDENCE', 'DONE');

ALTER TABLE report
    ADD CONSTRAINT fk_report_field_session
        FOREIGN KEY (field_session_id) REFERENCES field_session(id),
    ADD CONSTRAINT ck_report_processing_attempt
        CHECK (processing_attempt >= 0),
    ADD CONSTRAINT ck_report_processing_token_hash
        CHECK (
            processing_token_hash IS NULL
            OR processing_token_hash ~ '^[0-9a-f]{64}$'
        ),
    ADD CONSTRAINT uq_report_field_session
        UNIQUE (field_session_id);

COMMENT ON COLUMN report.field_session_id IS
    'BE-019 리포트 생성의 권위 있는 종료 임장 세션 ID.';

COMMENT ON COLUMN report.processing_token_hash IS
    'Worker 처리권 원문의 SHA-256 해시. 원문 Token은 저장하지 않는다.';

COMMENT ON COLUMN report.processing_attempt IS
    '처리권 신규 발급 또는 Lease 만료 재선점 횟수.';

COMMENT ON COLUMN report.processing_lease_expires_at IS
    '현재 Worker 처리권의 Backend 관리 만료 시각.';
