ALTER TABLE email ADD COLUMN is_starred BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_email_starred ON email (is_starred);
