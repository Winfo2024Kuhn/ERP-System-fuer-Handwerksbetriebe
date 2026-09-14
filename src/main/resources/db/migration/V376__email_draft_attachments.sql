-- Draft attachments live in the same transaction as their text and are removed with the draft.
CREATE TABLE IF NOT EXISTS email_draft_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    draft_id BIGINT NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size BIGINT NOT NULL,
    data LONGBLOB NOT NULL,
    CONSTRAINT fk_email_draft_attachment_draft FOREIGN KEY (draft_id) REFERENCES email_draft(id) ON DELETE CASCADE
);

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_draft' AND COLUMN_NAME = 'geschaeftsdokument');
SET @s = IF(@c = 0, 'ALTER TABLE email_draft ADD COLUMN geschaeftsdokument BIT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
