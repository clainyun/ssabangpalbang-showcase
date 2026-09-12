-- 클라이언트 재전송 시 동일 메시지를 중복 저장하지 않기 위한 멱등성 키.
-- 프론트엔드는 새 메시지마다 새 UUID를 생성하고, 같은 전송을 재시도할 때만 재사용한다.
-- 유일성 범위는 (sender_id, client_message_id)이며 study_id는 포함하지 않는다.
-- SYSTEM 메시지처럼 sender_id 또는 client_message_id가 없는 저장은 이 제약의 대상이 아니다.
ALTER TABLE chat_message ADD COLUMN client_message_id VARCHAR(100);

CREATE UNIQUE INDEX uq_chat_message_sender_client_message
    ON chat_message (sender_id, client_message_id)
    WHERE sender_id IS NOT NULL AND client_message_id IS NOT NULL;
