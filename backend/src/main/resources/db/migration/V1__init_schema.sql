-- =====================================================================
-- 부동산 임장 스터디 앱 '싸방팔방' — 통합 ERD DDL (PostgreSQL)
-- [v7 / 최종 요구사항·API 충돌 해소 반영]
--
-- v6 -> v7 핵심 변경
--   1) study.goal 추가
--   2) checklist_item을 category/title/subtitle/display_order 구조로 정리
--   3) 관심 지역 공개 동의 컬럼 추가
--   4) HOT 기준을 7일·가중치·기준점이 명확한 VIEW로 확정
--   5) chatbot_message 비동기 처리 상태·실패 정보 추가
--   6) report.public_id 공개 조회 URI 정책을 COMMENT로 확정
--   7) notification.category·target_sub_id 추가
--   8) 리포트별 자동 정보게시글 1건 제약 추가
--
-- v5 -> v6 반영 사항도 모두 유지
--   apartment 위치/주차, report_favorite, post_attachment, 현장 기록 구조,
--   리포트 공개 식별자, 파일 메타데이터, 알림 발신자, 챗봇 근거 메타데이터
--
-- 범위 제외
--   - 정식 1:1 쪽지함·대화방·메시지 읽음·사용자 차단·신고 테이블은 Backlog
--   - MVP 쪽지는 notification 한 건으로 저장·발송한다.
--   - 현재 범위에는 사용자 차단 관계가 없으므로 차단 여부를 조회하거나 저장하지 않는다.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- =====================================================================
-- A. 회원·인증·소셜
-- =====================================================================

CREATE TABLE member (
    id                          BIGSERIAL PRIMARY KEY,
    email                       VARCHAR(255) UNIQUE NOT NULL,
    password_hash               VARCHAR(255),
    nickname                    VARCHAR(50) UNIQUE NOT NULL,
    profile_image_url           VARCHAR(500),
    selected_character_id       VARCHAR(20) NOT NULL DEFAULT 'PALBANG',
    age_group                   VARCHAR(20),
    age_group_public_agreed     BOOLEAN NOT NULL DEFAULT FALSE,
    service_notification_agreed BOOLEAN NOT NULL DEFAULT TRUE,
    ad_notification_agreed      BOOLEAN NOT NULL DEFAULT FALSE,
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE member_preference (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT UNIQUE NOT NULL REFERENCES member(id),
    purpose         VARCHAR(50),
    household_type  VARCHAR(50),
    budget          VARCHAR(50),
    interest_region               VARCHAR(100),
    interest_region_public_agreed BOOLEAN NOT NULL DEFAULT FALSE,
    priorities                    JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE social_account (
    id             BIGSERIAL PRIMARY KEY,
    member_id      BIGINT NOT NULL REFERENCES member(id),
    provider       VARCHAR(20) NOT NULL,
    social_user_id VARCHAR(255) NOT NULL,
    email          VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, social_user_id)
);

CREATE TABLE fcm_token (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT NOT NULL REFERENCES member(id),
    token      VARCHAR(255) UNIQUE NOT NULL,
    device_id  VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (member_id, device_id)
);

CREATE TABLE follow (
    id           BIGSERIAL PRIMARY KEY,
    follower_id  BIGINT NOT NULL REFERENCES member(id),
    following_id BIGINT NOT NULL REFERENCES member(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (follower_id, following_id),
    CHECK (follower_id <> following_id)
);

CREATE TABLE notification (
    id              BIGSERIAL PRIMARY KEY,
    recipient_id    BIGINT NOT NULL REFERENCES member(id),
    actor_id        BIGINT REFERENCES member(id),
    category        VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',
    type            VARCHAR(30) NOT NULL,
    target_screen   VARCHAR(50),
    target_id       BIGINT,
    target_sub_id   BIGINT,
    title           VARCHAR(200),
    body            VARCHAR(500),
    send_status     VARCHAR(20),
    fail_reason     VARCHAR(255),
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    read_at         TIMESTAMPTZ,
    idempotency_key VARCHAR(200) UNIQUE NOT NULL,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================================
-- B. 아파트·스터디·채팅
-- =====================================================================

CREATE TABLE apartment (
    id                    BIGSERIAL PRIMARY KEY,
    complex_code          VARCHAR(50) UNIQUE NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    address               VARCHAR(300),
    district_code         VARCHAR(10),
    district_name         VARCHAR(50),
    dong_name             VARCHAR(50),
    legal_dong_code       VARCHAR(20),
    longitude             DOUBLE PRECISION NOT NULL,
    latitude              DOUBLE PRECISION NOT NULL,
    household_count       INT,
    completion_year_month VARCHAR(7),
    parking_space_count   INT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (household_count IS NULL OR household_count >= 0),
    CHECK (parking_space_count IS NULL OR parking_space_count >= 0),
    CHECK (
        completion_year_month IS NULL
        OR completion_year_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'
    )
);

CREATE TABLE apartment_transaction (
    id             BIGSERIAL PRIMARY KEY,
    apartment_id   BIGINT NOT NULL REFERENCES apartment(id),
    deal_date      DATE NOT NULL,
    exclusive_area NUMERIC(8,2),
    price          BIGINT,
    floor          INT,
    is_canceled    BOOLEAN NOT NULL DEFAULT FALSE,
    dedup_key      VARCHAR(200) UNIQUE NOT NULL,
    collected_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE apartment_favorite (
    id           BIGSERIAL PRIMARY KEY,
    member_id    BIGINT NOT NULL REFERENCES member(id),
    apartment_id BIGINT NOT NULL REFERENCES apartment(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (member_id, apartment_id)
);

CREATE TABLE study (
    id           BIGSERIAL PRIMARY KEY,
    apartment_id BIGINT NOT NULL REFERENCES apartment(id),
    leader_id    BIGINT NOT NULL REFERENCES member(id),
    title        VARCHAR(200),
    intro        TEXT,
    goal         TEXT NOT NULL,
    capacity     INT NOT NULL,
    purpose      VARCHAR(50),
    status       VARCHAR(20) NOT NULL DEFAULT 'RECRUITING',
    deleted_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (capacity > 0),
    CHECK (btrim(goal) <> '')
);

CREATE TABLE study_notice (
    id         BIGSERIAL PRIMARY KEY,
    study_id   BIGINT NOT NULL REFERENCES study(id),
    content    TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE study_application (
    id           BIGSERIAL PRIMARY KEY,
    study_id     BIGINT NOT NULL REFERENCES study(id),
    applicant_id BIGINT NOT NULL REFERENCES member(id),
    intro        VARCHAR(200),
    purpose      VARCHAR(20),
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at   TIMESTAMPTZ,
    UNIQUE (study_id, applicant_id)
);

CREATE TABLE study_member (
    id        BIGSERIAL PRIMARY KEY,
    study_id  BIGINT NOT NULL REFERENCES study(id),
    member_id BIGINT NOT NULL REFERENCES member(id),
    role      VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at   TIMESTAMPTZ,
    UNIQUE (study_id, member_id)
);

CREATE TABLE schedule (
    id            BIGSERIAL PRIMARY KEY,
    study_id      BIGINT UNIQUE NOT NULL REFERENCES study(id),
    start_at      TIMESTAMPTZ NOT NULL,
    end_at        TIMESTAMPTZ,
    meeting_place VARCHAR(200),
    status        VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_at IS NULL OR end_at >= start_at)
);

CREATE TABLE file_meta (
    id            BIGSERIAL PRIMARY KEY,
    owner_id      BIGINT NOT NULL REFERENCES member(id),
    study_id      BIGINT REFERENCES study(id),
    file_usage    VARCHAR(30) NOT NULL,
    original_name VARCHAR(255),
    s3_key        VARCHAR(500) UNIQUE NOT NULL,
    content_type  VARCHAR(100),
    size_bytes    BIGINT,
    upload_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at    TIMESTAMPTZ,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (size_bytes IS NULL OR size_bytes >= 0)
);

CREATE TABLE chat_message (
    id            BIGSERIAL PRIMARY KEY,
    study_id      BIGINT NOT NULL REFERENCES study(id),
    sender_id     BIGINT REFERENCES member(id),
    message_type  VARCHAR(20) NOT NULL,
    content       TEXT,
    image_file_id BIGINT REFERENCES file_meta(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chat_read_status (
    id           BIGSERIAL PRIMARY KEY,
    study_id     BIGINT NOT NULL REFERENCES study(id),
    member_id    BIGINT NOT NULL REFERENCES member(id),
    last_read_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (study_id, member_id)
);

-- =====================================================================
-- C. 현장 임장·체크리스트
-- =====================================================================

CREATE TABLE field_session (
    id            BIGSERIAL PRIMARY KEY,
    study_id      BIGINT UNIQUE NOT NULL REFERENCES study(id),
    status        VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at      TIMESTAMPTZ,
    ended_by_id   BIGINT REFERENCES member(id),
    end_reason    VARCHAR(30)
);

CREATE TABLE field_participant (
    id                BIGSERIAL PRIMARY KEY,
    session_id        BIGINT NOT NULL REFERENCES field_session(id),
    member_id         BIGINT NOT NULL REFERENCES member(id),
    status            VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at          TIMESTAMPTZ,
    end_reason        VARCHAR(30),
    stay_duration_sec INT,
    UNIQUE (session_id, member_id),
    CHECK (stay_duration_sec IS NULL OR stay_duration_sec >= 0)
);

CREATE TABLE checklist (
    id           BIGSERIAL PRIMARY KEY,
    session_id   BIGINT NOT NULL REFERENCES field_session(id),
    member_id    BIGINT NOT NULL REFERENCES member(id),
    is_fallback  BOOLEAN NOT NULL DEFAULT FALSE,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, member_id)
);

CREATE TABLE checklist_item (
    id            BIGSERIAL PRIMARY KEY,
    checklist_id  BIGINT NOT NULL REFERENCES checklist(id),
    category      VARCHAR(30) NOT NULL,
    title         TEXT NOT NULL,
    subtitle      TEXT,
    display_order INT NOT NULL,
    UNIQUE (checklist_id, display_order),
    CHECK (btrim(title) <> '')
);

CREATE TABLE field_record (
    id                BIGSERIAL PRIMARY KEY,
    session_id        BIGINT NOT NULL REFERENCES field_session(id),
    checklist_item_id BIGINT NOT NULL REFERENCES checklist_item(id),
    author_id         BIGINT NOT NULL REFERENCES member(id),
    source_type       VARCHAR(20) NOT NULL,
    text_content      TEXT,
    photo_file_id     BIGINT REFERENCES file_meta(id),
    stt_status        VARCHAR(20),
    client_request_id VARCHAR(100) UNIQUE NOT NULL,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE checklist_answer (
    id                BIGSERIAL PRIMARY KEY,
    checklist_item_id BIGINT UNIQUE NOT NULL REFERENCES checklist_item(id),
    is_completed      BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at      TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================================
-- D. AI 리포트
-- =====================================================================

CREATE TABLE report (
    id             BIGSERIAL PRIMARY KEY,
    public_id      UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    study_id       BIGINT UNIQUE NOT NULL REFERENCES study(id),
    apartment_id   BIGINT NOT NULL REFERENCES apartment(id),
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    visibility     VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    progress_stage VARCHAR(20),
    result_json    JSONB,
    fail_reason    VARCHAR(255),
    is_retryable   BOOLEAN NOT NULL DEFAULT FALSE,
    published_at   TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE report_evidence (
    id              BIGSERIAL PRIMARY KEY,
    report_id       BIGINT NOT NULL REFERENCES report(id),
    field_record_id BIGINT NOT NULL REFERENCES field_record(id),
    claim_key       VARCHAR(100),
    display_order   INT,
    UNIQUE (report_id, field_record_id, claim_key)
);

CREATE TABLE report_favorite (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT NOT NULL REFERENCES member(id),
    report_id  BIGINT NOT NULL REFERENCES report(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (member_id, report_id)
);

-- =====================================================================
-- E. 커뮤니티
-- =====================================================================

CREATE TABLE post (
    id             BIGSERIAL PRIMARY KEY,
    board_type     VARCHAR(20) NOT NULL,
    author_id      BIGINT REFERENCES member(id),
    title          VARCHAR(200) NOT NULL,
    content        TEXT,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_auto_report BOOLEAN NOT NULL DEFAULT FALSE,
    report_id      BIGINT REFERENCES report(id),
    apartment_id   BIGINT REFERENCES apartment(id),
    view_count     BIGINT NOT NULL DEFAULT 0,
    deleted_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (view_count >= 0),
    CHECK (
        NOT is_auto_report
        OR (report_id IS NOT NULL AND board_type = 'INFORMATION')
    )
);

CREATE TABLE post_comment (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT NOT NULL REFERENCES post(id),
    author_id  BIGINT NOT NULL REFERENCES member(id),
    content    TEXT NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE post_like (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT NOT NULL REFERENCES post(id),
    member_id  BIGINT NOT NULL REFERENCES member(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (post_id, member_id)
);

CREATE TABLE post_attachment (
    id            BIGSERIAL PRIMARY KEY,
    post_id       BIGINT NOT NULL REFERENCES post(id),
    file_id       BIGINT NOT NULL REFERENCES file_meta(id),
    display_order INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (post_id, file_id),
    UNIQUE (post_id, display_order)
);

-- ---------------------------------------------------------------------
-- 커뮤니티 HOT 확정 규칙
--   대상 기간: 작성 후 7일 이내의 ACTIVE 게시글
--   점수식   : 조회 수 × 1 + 좋아요 수 × 5 + 댓글 수 × 3
--              + max(0, 168 - 경과 시간) × 0.1
--   HOT 기준 : 점수 20점 이상
--   정렬      : 점수 DESC, 작성 시각 DESC, 게시글 ID DESC
-- ---------------------------------------------------------------------
CREATE VIEW post_hot_metric AS
WITH like_count AS (
    SELECT post_id, COUNT(*)::BIGINT AS like_count
    FROM post_like
    GROUP BY post_id
),
comment_count AS (
    SELECT post_id, COUNT(*)::BIGINT AS comment_count
    FROM post_comment
    WHERE deleted_at IS NULL
    GROUP BY post_id
),
metrics AS (
    SELECT
        p.id AS post_id,
        p.board_type,
        p.created_at,
        p.view_count,
        COALESCE(l.like_count, 0) AS like_count,
        COALESCE(c.comment_count, 0) AS comment_count,
        ROUND(
            p.view_count::NUMERIC
            + COALESCE(l.like_count, 0)::NUMERIC * 5
            + COALESCE(c.comment_count, 0)::NUMERIC * 3
            + GREATEST(
                0::NUMERIC,
                168::NUMERIC
                - EXTRACT(EPOCH FROM (now() - p.created_at)) / 3600
            ) * 0.1,
            2
        ) AS hot_score
    FROM post p
    LEFT JOIN like_count l ON l.post_id = p.id
    LEFT JOIN comment_count c ON c.post_id = p.id
    WHERE p.status = 'ACTIVE'
      AND p.deleted_at IS NULL
      AND p.created_at >= now() - INTERVAL '7 days'
)
SELECT
    post_id,
    board_type,
    view_count,
    like_count,
    comment_count,
    hot_score,
    (hot_score >= 20) AS is_hot,
    DENSE_RANK() OVER (
        PARTITION BY board_type
        ORDER BY hot_score DESC, created_at DESC, post_id DESC
    ) AS hot_rank,
    created_at
FROM metrics;

-- =====================================================================
-- F. AI 챗봇·RAG
-- =====================================================================

CREATE TABLE chatbot_conversation (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT NOT NULL REFERENCES member(id),
    apartment_id    BIGINT NOT NULL REFERENCES apartment(id),
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chatbot_message (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES chatbot_conversation(id),
    role            VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    content         TEXT,
    basis_type      VARCHAR(20),
    basis_label     VARCHAR(100),
    sources_json    JSONB,
    fail_reason     VARCHAR(500),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (role IN ('USER', 'ASSISTANT')),
    CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CHECK (
        (role = 'USER' AND status = 'COMPLETED' AND content IS NOT NULL)
        OR
        (role = 'ASSISTANT' AND (
            (status IN ('PENDING', 'PROCESSING') AND content IS NULL)
            OR (status = 'COMPLETED' AND content IS NOT NULL)
            OR status = 'FAILED'
        ))
    ),
    CHECK (status <> 'FAILED' OR fail_reason IS NOT NULL)
);

CREATE TABLE apartment_rag_document (
    id           BIGSERIAL PRIMARY KEY,
    apartment_id BIGINT NOT NULL REFERENCES apartment(id),
    source_type  VARCHAR(20) NOT NULL,
    source_id    BIGINT,
    content      TEXT NOT NULL,
    embedding    vector(2560),
    source_at    TIMESTAMPTZ,
    reindex_key  VARCHAR(200) UNIQUE NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================================
-- 인덱스
-- =====================================================================

CREATE INDEX idx_apartment_district ON apartment (district_code, district_name);
CREATE INDEX idx_apartment_dong ON apartment (district_code, dong_name);
CREATE INDEX idx_apartment_coord ON apartment (latitude, longitude);

-- 키워드 검색
CREATE INDEX idx_apartment_name_trgm
    ON apartment USING GIN (name gin_trgm_ops);
CREATE INDEX idx_apartment_address_trgm
    ON apartment USING GIN (address gin_trgm_ops);
CREATE INDEX idx_apartment_dong_trgm
    ON apartment USING GIN (dong_name gin_trgm_ops);

-- 현재 위치 반경·거리순 검색. longitude/latitude 중복 컬럼을 만들지 않고
-- PostGIS geography 식 인덱스로 처리한다.
CREATE INDEX idx_apartment_location_gist
    ON apartment USING GIST (
        (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography)
    );

CREATE INDEX idx_transaction_recent
    ON apartment_transaction (apartment_id, deal_date DESC);
CREATE INDEX idx_apartment_favorite_member
    ON apartment_favorite (member_id, id DESC);

CREATE INDEX idx_study_apartment_status
    ON study (apartment_id, status, id DESC);
CREATE INDEX idx_study_member_member_status
    ON study_member (member_id, status, study_id);
CREATE INDEX idx_schedule_start
    ON schedule (start_at, status);

CREATE INDEX idx_file_meta_expiry
    ON file_meta (expires_at)
    WHERE deleted_at IS NULL AND expires_at IS NOT NULL;
CREATE INDEX idx_chat_message_study
    ON chat_message (study_id, id DESC);
CREATE INDEX idx_notification_recipient
    ON notification (recipient_id, id DESC);
CREATE INDEX idx_notification_target
    ON notification (category, target_screen, target_id, target_sub_id);

CREATE INDEX idx_field_participant_member
    ON field_participant (member_id, session_id);
CREATE INDEX idx_field_record_author
    ON field_record (author_id, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_field_record_item
    ON field_record (checklist_item_id, created_at)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_report_apartment_status
    ON report (apartment_id, status, completed_at DESC);
CREATE INDEX idx_report_favorite_member
    ON report_favorite (member_id, id DESC);
CREATE INDEX idx_report_evidence_report
    ON report_evidence (report_id, display_order, id);

CREATE INDEX idx_post_board_recent
    ON post (board_type, status, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_post_hot_candidates
    ON post (board_type, status, view_count DESC, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_post_author
    ON post (author_id, id DESC);
CREATE UNIQUE INDEX uq_post_auto_report_per_report
    ON post (report_id)
    WHERE is_auto_report = TRUE
      AND report_id IS NOT NULL
      AND deleted_at IS NULL;
CREATE INDEX idx_post_comment_post
    ON post_comment (post_id, id);
CREATE INDEX idx_post_comment_author
    ON post_comment (author_id, id DESC);
CREATE INDEX idx_post_like_post
    ON post_like (post_id, id);
CREATE INDEX idx_post_attachment_post
    ON post_attachment (post_id, display_order);
CREATE INDEX idx_post_search_trgm
    ON post USING GIN ((title || ' ' || COALESCE(content, '')) gin_trgm_ops);

CREATE INDEX idx_follow_follower
    ON follow (follower_id, id DESC);
CREATE INDEX idx_chatbot_conversation_member
    ON chatbot_conversation (member_id, last_message_at DESC);
CREATE INDEX idx_chatbot_message_conv
    ON chatbot_message (conversation_id, id);
CREATE INDEX idx_chatbot_message_processing
    ON chatbot_message (status, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');

-- =====================================================================
-- 상태값 권장
-- =====================================================================
-- member.status                 ACTIVE/WITHDRAWN
-- study.status                  RECRUITING/CLOSED/IN_PROGRESS/COMPLETED/CANCELED
-- study_application.status      PENDING/APPROVED/REJECTED
-- study_member.status           ACTIVE/REMOVED
-- schedule.status               SCHEDULED/COMPLETED/CANCELED
-- file_meta.file_usage          FIELD_PHOTO/STT_AUDIO/CHAT_IMAGE/POST_ATTACHMENT
-- file_meta.upload_status       PENDING/COMPLETED/FAILED/DELETED
-- field_session.status          IN_PROGRESS/ENDED
-- field_session.end_reason      ALL_ENDED/LEADER_FORCED
-- field_participant.status      IN_PROGRESS/ENDED
-- field_participant.end_reason  SELF_ENDED/LEADER_FORCED/SESSION_ENDED
-- field_record.source_type      TEXT/PHOTO/STT
-- field_record.stt_status       PENDING/PROCESSING/DONE/FAILED
-- report.status                 PENDING/IN_PROGRESS/DONE/FAILED
-- report.visibility             PUBLIC/PARTICIPANTS_ONLY
-- report.progress_stage         COLLECT/STT/ANALYZE/EVIDENCE/DONE
-- post.board_type               INFORMATION/FREE
-- post.status                   ACTIVE/HIDDEN
-- chatbot_message.role          USER/ASSISTANT
-- chatbot_message.status        PENDING/PROCESSING/COMPLETED/FAILED
-- chatbot_message.basis_type    REPORT/WEB/NONE
-- notification.category        STUDY/FIELD/REPORT/COMMUNITY/MESSAGE/SYSTEM
-- notification.type            APPLICATION_RESULT/SCHEDULE_CHANGED/D1/
--                              REPORT_COMPLETED/FIELD_END_REQUEST/MESSAGE
-- =====================================================================


-- =====================================================================
-- 확정 정책 COMMENT
-- =====================================================================
COMMENT ON COLUMN member_preference.interest_region_public_agreed IS
    'TRUE인 경우에만 공개 프로필/팔로잉 목록에 interest_region을 노출한다.';

COMMENT ON COLUMN study.goal IS
    '스터디 생성 및 모집/내 스터디 상세 화면에 표시하는 스터디 목표.';

COMMENT ON COLUMN checklist_item.title IS
    '체크리스트 항목의 주 제목. API 필드명 title.';

COMMENT ON COLUMN checklist_item.subtitle IS
    '체크리스트 항목의 보조 설명. API 필드명 subtitle.';

COMMENT ON COLUMN report.public_id IS
    '외부 공유용 공개 식별자. 공개 조회 URI: GET /api/v1/reports/public/{publicId}';

COMMENT ON COLUMN notification.category IS
    '알림 상위 분류. type보다 넓은 화면/API 분류 단위.';

COMMENT ON COLUMN notification.target_id IS
    '딥링크의 주 대상 ID. 예: studyId, reportId, postId.';

COMMENT ON COLUMN notification.target_sub_id IS
    '딥링크의 보조 대상 ID. 예: commentId, scheduleId. 없으면 NULL.';

COMMENT ON VIEW post_hot_metric IS
    '7일 이내 ACTIVE 게시글에 조회×1, 좋아요×5, 댓글×3, 최근성 보너스를 적용하고 20점 이상을 HOT으로 판정한다.';
