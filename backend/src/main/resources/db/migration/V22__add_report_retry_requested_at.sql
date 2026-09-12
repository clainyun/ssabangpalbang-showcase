-- BE-019: preserve the accepted timestamp of the current report retry cycle.
ALTER TABLE report
    ADD COLUMN retry_requested_at TIMESTAMPTZ;

COMMENT ON COLUMN report.retry_requested_at IS
    '현재 리포트 재생성 주기의 최초 수락 시각. 중복 요청 응답에 재사용한다.';
