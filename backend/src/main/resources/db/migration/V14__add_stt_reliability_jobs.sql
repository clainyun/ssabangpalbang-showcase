CREATE TABLE stt_dispatch_outbox (
    id                  BIGSERIAL PRIMARY KEY,
    stt_job_id          BIGINT NOT NULL REFERENCES stt_job(id),
    stt_id              VARCHAR(50) NOT NULL,
    attempt_no          INT NOT NULL,
    audio_file_id       BIGINT NOT NULL REFERENCES file_meta(id),
    object_key          VARCHAR(500) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    language            VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    attempt_count       INT NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ NOT NULL,
    last_error          VARCHAR(500),
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_stt_dispatch_outbox_attempt
        UNIQUE (stt_id, attempt_no),
    CONSTRAINT ck_stt_dispatch_outbox_attempt_no
        CHECK (attempt_no >= 1),
    CONSTRAINT ck_stt_dispatch_outbox_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_stt_dispatch_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_stt_dispatch_outbox_ready
    ON stt_dispatch_outbox (status, next_attempt_at, id);

CREATE TABLE stt_audio_cleanup_job (
    id                  BIGSERIAL PRIMARY KEY,
    audio_file_id       BIGINT NOT NULL REFERENCES file_meta(id),
    object_key          VARCHAR(500) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    attempt_count       INT NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ NOT NULL,
    last_error          VARCHAR(500),
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_stt_audio_cleanup_file UNIQUE (audio_file_id),
    CONSTRAINT ck_stt_audio_cleanup_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_stt_audio_cleanup_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_stt_audio_cleanup_ready
    ON stt_audio_cleanup_job (status, next_attempt_at, id);

-- Requests accepted before this migration must not remain permanently PENDING.
INSERT INTO stt_dispatch_outbox (
    stt_job_id,
    stt_id,
    attempt_no,
    audio_file_id,
    object_key,
    content_type,
    language,
    status,
    attempt_count,
    next_attempt_at,
    created_at,
    updated_at
)
SELECT
    job.id,
    job.stt_id,
    attempt.attempt_no,
    job.audio_file_id,
    file.s3_key,
    COALESCE(file.content_type, 'audio/octet-stream'),
    'ko-KR',
    'PENDING',
    0,
    now(),
    now(),
    now()
FROM stt_job job
JOIN stt_job_attempt attempt
  ON attempt.stt_job_id = job.id
 AND attempt.attempt_no = (
     SELECT MAX(latest.attempt_no)
     FROM stt_job_attempt latest
     WHERE latest.stt_job_id = job.id
 )
JOIN file_meta file ON file.id = job.audio_file_id
WHERE job.status = 'PENDING'
  AND attempt.status = 'PENDING'
ON CONFLICT (stt_id, attempt_no) DO NOTHING;

-- A completed DB record can safely retry physical deletion after deployment.
INSERT INTO stt_audio_cleanup_job (
    audio_file_id,
    object_key,
    status,
    attempt_count,
    next_attempt_at,
    created_at,
    updated_at
)
SELECT
    job.audio_file_id,
    file.s3_key,
    'PENDING',
    0,
    now(),
    now(),
    now()
FROM stt_job job
JOIN file_meta file ON file.id = job.audio_file_id
WHERE job.status = 'DONE'
  AND file.upload_status = 'DELETED'
ON CONFLICT (audio_file_id) DO NOTHING;
