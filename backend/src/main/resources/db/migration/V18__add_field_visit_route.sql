-- BE-020: 임장 세션 공유 추천 경로(단지 주변 시설 순회).

CREATE TABLE field_visit_route (
    id                          BIGSERIAL PRIMARY KEY,
    session_id                  BIGINT NOT NULL UNIQUE REFERENCES field_session(id),
    generated_by_id             BIGINT NOT NULL REFERENCES member(id),
    total_distance_meters       INTEGER NOT NULL DEFAULT 0,
    estimated_duration_minutes  INTEGER NOT NULL DEFAULT 0,
    generated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_route_distance CHECK (total_distance_meters >= 0),
    CONSTRAINT ck_route_duration CHECK (estimated_duration_minutes >= 0)
);

CREATE TABLE field_visit_route_waypoint (
    id                      BIGSERIAL PRIMARY KEY,
    route_id                BIGINT NOT NULL REFERENCES field_visit_route(id) ON DELETE CASCADE,
    sequence                INTEGER NOT NULL,
    facility_type           VARCHAR(30) NOT NULL,
    name                    VARCHAR(100) NOT NULL,
    address                 VARCHAR(300),
    latitude                DOUBLE PRECISION NOT NULL,
    longitude               DOUBLE PRECISION NOT NULL,
    kakao_place_id          VARCHAR(50),
    distance_from_origin_m  INTEGER NOT NULL DEFAULT 0,
    distance_from_prev_m    INTEGER NOT NULL DEFAULT 0,
    walk_minutes_from_prev  INTEGER NOT NULL DEFAULT 0,
    stay_minutes            INTEGER NOT NULL DEFAULT 5,
    guide                   TEXT,
    CONSTRAINT uk_route_waypoint_sequence UNIQUE (route_id, sequence),
    CONSTRAINT ck_route_waypoint_sequence CHECK (sequence >= 1),
    CONSTRAINT ck_route_waypoint_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_route_waypoint_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_route_waypoint_lng CHECK (longitude BETWEEN -180 AND 180)
);

CREATE TABLE field_visit_route_waypoint_item (
    id                 BIGSERIAL PRIMARY KEY,
    waypoint_id        BIGINT NOT NULL REFERENCES field_visit_route_waypoint(id) ON DELETE CASCADE,
    checklist_item_id  BIGINT NOT NULL REFERENCES checklist_item(id) ON DELETE CASCADE,
    CONSTRAINT uk_route_waypoint_item UNIQUE (waypoint_id, checklist_item_id)
);

CREATE INDEX idx_route_waypoint_item_checklist_item
    ON field_visit_route_waypoint_item (checklist_item_id);
