-- 승인된 편도 보행 경로의 GeoJSON LineString. 기존 V16 행은 안전 재계산 전까지 NULL이다.

ALTER TABLE field_visit_route
    ADD COLUMN geometry JSONB;
