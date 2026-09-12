CREATE TABLE stt_job (
    id                        BIGSERIAL PRIMARY KEY,
    stt_id                    VARCHAR(50) NOT NULL,
    member_id                 BIGINT NOT NULL REFERENCES member(id),
    study_id                  BIGINT NOT NULL REFERENCES study(id),
    session_id                BIGINT NOT NULL REFERENCES field_session(id),
    audio_file_id             BIGINT NOT NULL REFERENCES file_meta(id),
    checklist_item_id         BIGINT NOT NULL REFERENCES checklist_item(id),
    field_record_id           BIGINT REFERENCES field_record(id),
    initial_client_request_id UUID NOT NULL,
    status                    VARCHAR(20) NOT NULL,
    fail_code                 VARCHAR(100),
    fail_reason               VARCHAR(500),
    retryable                 BOOLEAN NOT NULL DEFAULT FALSE,
    retry_count               INT NOT NULL DEFAULT 0,
    requested_at              TIMESTAMPTZ NOT NULL,
    started_at                TIMESTAMPTZ,
    completed_at              TIMESTAMPTZ,
    last_dispatched_at        TIMESTAMPTZ,
    dispatch_count            INT NOT NULL DEFAULT 0,
    version                   BIGINT NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_stt_job_stt_id UNIQUE (stt_id),
    CONSTRAINT uk_stt_job_audio_file UNIQUE (audio_file_id),
    CONSTRAINT uk_stt_job_initial_request
        UNIQUE (member_id, initial_client_request_id),
    CONSTRAINT uk_stt_job_field_record UNIQUE (field_record_id),
    CONSTRAINT ck_stt_job_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    CONSTRAINT ck_stt_job_retry_count CHECK (retry_count >= 0),
    CONSTRAINT ck_stt_job_dispatch_count CHECK (dispatch_count >= 0)
);

CREATE TABLE stt_job_attempt (
    id                BIGSERIAL PRIMARY KEY,
    stt_job_id        BIGINT NOT NULL REFERENCES stt_job(id),
    member_id         BIGINT NOT NULL REFERENCES member(id),
    client_request_id UUID NOT NULL,
    attempt_no        INT NOT NULL,
    request_type      VARCHAR(10) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    fail_code         VARCHAR(100),
    fail_reason       VARCHAR(500),
    requested_at      TIMESTAMPTZ NOT NULL,
    started_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    CONSTRAINT uk_stt_job_attempt_request
        UNIQUE (member_id, client_request_id),
    CONSTRAINT uk_stt_job_attempt_number
        UNIQUE (stt_job_id, attempt_no),
    CONSTRAINT ck_stt_job_attempt_no CHECK (attempt_no >= 1),
    CONSTRAINT ck_stt_job_attempt_request_type
        CHECK (request_type IN ('INITIAL', 'RETRY')),
    CONSTRAINT ck_stt_job_attempt_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED'))
);

CREATE INDEX idx_stt_job_member_status
    ON stt_job (member_id, status);
CREATE INDEX idx_stt_job_study_status
    ON stt_job (study_id, status);
CREATE INDEX idx_stt_job_attempt_job
    ON stt_job_attempt (stt_job_id, attempt_no);
