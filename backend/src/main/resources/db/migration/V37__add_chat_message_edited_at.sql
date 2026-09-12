-- 채팅 메시지 수정 시각. null이면 수정된 적 없는 메시지다.
-- content는 제자리에서 갱신하고 이 컬럼으로 "수정됨" 표시와 클라이언트 병합
-- 우선순위(더 늦게 수정된 버전이 이긴다)를 판단한다.
ALTER TABLE chat_message
    ADD COLUMN edited_at TIMESTAMPTZ;
