-- AI-002: 체크리스트 항목별 예시 메모.
-- 카탈로그 선정 경로는 카탈로그의 example을, 자유생성 경로는 AI가 생성한 example을 저장한다.
-- 없을 수 있으므로 NULL 허용(프론트는 null이면 기본 안내 문구를 쓴다).
ALTER TABLE checklist_item
    ADD COLUMN example TEXT;

COMMENT ON COLUMN checklist_item.example IS
    '현장에서 이 항목을 확인한 뒤 남길 법한 한 줄 예시 메모. 프론트 메모 입력 placeholder에 사용.';
