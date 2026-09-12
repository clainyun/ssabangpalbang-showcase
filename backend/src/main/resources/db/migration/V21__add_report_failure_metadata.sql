-- BE-019: terminal Report Worker failure metadata and idempotency hash.
-- The raw failure command and processing Token are never persisted.
ALTER TABLE report
    ADD COLUMN fail_code VARCHAR(100),
    ADD COLUMN failed_at TIMESTAMPTZ,
    ADD COLUMN fail_payload_hash VARCHAR(64);

ALTER TABLE report
    ADD CONSTRAINT ck_report_fail_code
        CHECK (
            fail_code IS NULL
            OR fail_code ~ '^[A-Z][A-Z0-9_]{0,99}$'
        ),
    ADD CONSTRAINT ck_report_fail_payload_hash
        CHECK (
            fail_payload_hash IS NULL
            OR fail_payload_hash ~ '^[0-9a-f]{64}$'
        );

COMMENT ON COLUMN report.fail_code IS
    'AI Report Worker의 allowlist 실패 코드.';

COMMENT ON COLUMN report.failed_at IS
    '현재 처리 시도의 실패가 원자적으로 확정된 시각.';

COMMENT ON COLUMN report.fail_payload_hash IS
    '실패 단계·코드·안전 문구·retryable의 정규화 SHA-256 해시.';
