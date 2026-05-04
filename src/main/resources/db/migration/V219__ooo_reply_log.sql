-- Log der bereits beantworteten Absender pro Abwesenheitsplan.
-- Verhindert, dass derselbe Kunde innerhalb einer Abwesenheit mehrfach
-- automatisch beantwortet wird.
CREATE TABLE IF NOT EXISTS ooo_reply_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT NOT NULL,
    sender_address VARCHAR(320) NOT NULL,
    replied_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ooo_reply_log_schedule_sender UNIQUE (schedule_id, sender_address),
    CONSTRAINT fk_ooo_reply_log_schedule FOREIGN KEY (schedule_id)
        REFERENCES out_of_office_schedule (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
