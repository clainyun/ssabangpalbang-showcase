-- BE-014: 최초 임장 세션 시작 시점의 고정 참여 후보 명단.

CREATE TABLE field_visit_candidate (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT NOT NULL REFERENCES field_session(id),
    member_id   BIGINT NOT NULL REFERENCES member(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_field_visit_candidate_session_member
        UNIQUE (session_id, member_id)
);

COMMENT ON TABLE field_visit_candidate IS
    'BE-014 최초 임장 세션 시작 시점의 ACTIVE 스터디 멤버 고정 명단.';

-- V1부터 존재할 수 있는 기존 세션은 배포 시점의 ACTIVE 명단과 기존 참여자를
-- 후보로 고정해, 마이그레이션 직후 후발 시작 권한이 일괄 소실되지 않게 한다.
INSERT INTO field_visit_candidate (session_id, member_id, created_at)
SELECT fs.id, sm.member_id, fs.started_at
FROM field_session fs
JOIN study_member sm
  ON sm.study_id = fs.study_id
 AND sm.status = 'ACTIVE'
UNION
SELECT fs.id, fp.member_id, fs.started_at
FROM field_session fs
JOIN field_participant fp ON fp.session_id = fs.id
ON CONFLICT (session_id, member_id) DO NOTHING;
