-- BE-019: completion idempotency without retaining the raw processing Token
-- or the sensitive full completion request.
ALTER TABLE report
    ADD COLUMN complete_payload_hash VARCHAR(64);

ALTER TABLE report
    ADD CONSTRAINT ck_report_complete_payload_hash
        CHECK (
            complete_payload_hash IS NULL
            OR complete_payload_hash ~ '^[0-9a-f]{64}$'
        );

COMMENT ON COLUMN report.complete_payload_hash IS
    'AI 결과·근거 payload의 정규화 SHA-256 해시. Token·attempt와 함께 멱등 판정.';
