CREATE INDEX idx_notification_recipient_read_sent
    ON notification (recipient_id, is_read, sent_at DESC, id DESC);
