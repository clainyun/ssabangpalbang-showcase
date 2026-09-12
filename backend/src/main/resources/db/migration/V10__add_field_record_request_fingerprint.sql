-- BE-015: 현장 기록 POST 멱등성용 최초 요청 fingerprint.
-- STT(BE-016) 행은 NULL을 유지하고, TEXT/PHOTO 생성 시에만 채운다.

ALTER TABLE field_record
    ADD COLUMN request_fingerprint VARCHAR(128);

COMMENT ON COLUMN field_record.request_fingerprint IS
    'BE-015 TEXT/PHOTO 생성 시 client_request_id 멱등 비교용. STT는 NULL.';
