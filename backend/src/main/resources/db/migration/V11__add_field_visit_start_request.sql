-- BE-014: GPS 임장 시작 clientRequestId 멱등성 요청 이력.

CREATE TABLE field_visit_start_request (
    id                 BIGSERIAL PRIMARY KEY,
    member_id          BIGINT NOT NULL REFERENCES member(id),
    study_id           BIGINT NOT NULL REFERENCES study(id),
    session_id         BIGINT NOT NULL REFERENCES field_session(id),
    participant_id     BIGINT NOT NULL REFERENCES field_participant(id),
    client_request_id  VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_field_visit_start_request_member_client
        UNIQUE (member_id, client_request_id)
);

CREATE INDEX idx_field_visit_start_request_session
    ON field_visit_start_request (session_id);

COMMENT ON TABLE field_visit_start_request IS
    'BE-014 임장 시작 client_request_id 멱등 이력. member_id+client_request_id UNIQUE.';

COMMENT ON COLUMN field_visit_start_request.request_fingerprint IS
    'studyId|정규화 latitude|정규화 longitude SHA-256 hex.';
