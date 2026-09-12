CREATE TABLE checklist_generation_progress (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT NOT NULL REFERENCES field_session(id),
    member_id       BIGINT NOT NULL REFERENCES member(id),
    attempt_id      VARCHAR(64) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    stage           VARCHAR(30) NOT NULL,
    progress_rate   INT NOT NULL,
    message         VARCHAR(255) NOT NULL,
    failure_message VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, member_id, attempt_id),
    CHECK (status IN ('IN_PROGRESS', 'DONE', 'FAILED')),
    CHECK (progress_rate BETWEEN 0 AND 100)
);
