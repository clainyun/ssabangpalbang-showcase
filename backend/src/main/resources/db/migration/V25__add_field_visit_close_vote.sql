-- BE-018-1: 실제 시작 참여자의 과반수 동의 기반 전체 임장 종료 투표

CREATE TABLE field_visit_close_vote (
    id                   BIGSERIAL PRIMARY KEY,
    field_session_id     BIGINT NOT NULL REFERENCES field_session(id),
    field_participant_id BIGINT NOT NULL REFERENCES field_participant(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_field_visit_close_vote_session_participant
        UNIQUE (field_session_id, field_participant_id)
);

CREATE INDEX idx_field_visit_close_vote_session_id
    ON field_visit_close_vote (field_session_id);
