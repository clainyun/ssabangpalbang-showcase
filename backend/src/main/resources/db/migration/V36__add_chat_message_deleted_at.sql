-- 채팅 메시지 소프트 삭제 시각.
-- 행을 지우지 않고 표시만 바꾼다: 커서 페이지네이션(id 기준)이 흔들리지 않고,
-- 안 읽은 수 계산도 기존 행 수 기준을 유지한다. 삭제된 메시지는 이력에서
-- "삭제된 메시지" 톰스톤으로 내려가며 content·이미지 URL은 노출하지 않는다.
ALTER TABLE chat_message
    ADD COLUMN deleted_at TIMESTAMPTZ;
