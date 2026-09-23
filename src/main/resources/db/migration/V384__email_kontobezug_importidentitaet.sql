SET @email_konto_col = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email' AND column_name = 'konto_id');
SET @email_konto_sql = IF(@email_konto_col = 0,
    'ALTER TABLE email ADD COLUMN konto_id VARCHAR(16) NULL', 'SELECT 1');
PREPARE email_konto_stmt FROM @email_konto_sql;
EXECUTE email_konto_stmt;
DEALLOCATE PREPARE email_konto_stmt;

SET @email_auto_submitted_col = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email' AND column_name = 'auto_submitted');
SET @email_auto_submitted_sql = IF(@email_auto_submitted_col = 0,
    'ALTER TABLE email ADD COLUMN auto_submitted VARCHAR(255) NULL', 'SELECT 1');
PREPARE email_auto_submitted_stmt FROM @email_auto_submitted_sql;
EXECUTE email_auto_submitted_stmt;
DEALLOCATE PREPARE email_auto_submitted_stmt;

SET @email_in_reply_to_col = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email' AND column_name = 'in_reply_to');
SET @email_in_reply_to_sql = IF(@email_in_reply_to_col = 0,
    'ALTER TABLE email ADD COLUMN in_reply_to VARCHAR(1000) NULL', 'SELECT 1');
PREPARE email_in_reply_to_stmt FROM @email_in_reply_to_sql;
EXECUTE email_in_reply_to_stmt;
DEALLOCATE PREPARE email_in_reply_to_stmt;

SET @email_references_col = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email' AND column_name = 'references_header');
SET @email_references_sql = IF(@email_references_col = 0,
    'ALTER TABLE email ADD COLUMN references_header TEXT NULL', 'SELECT 1');
PREPARE email_references_stmt FROM @email_references_sql;
EXECUTE email_references_stmt;
DEALLOCATE PREPARE email_references_stmt;

UPDATE email SET konto_id = 'HAUPT' WHERE konto_id IS NULL;
ALTER TABLE email MODIFY COLUMN konto_id VARCHAR(16) NOT NULL;

-- Entfernt sowohl den explizit benannten Index als auch ein mögliches
-- implizites UNIQUE aus @Column(unique = true), unabhängig vom Hibernate-Namen.
SET @email_msg_unique_drops = (
    SELECT GROUP_CONCAT(CONCAT('DROP INDEX `', index_name, '`') SEPARATOR ', ')
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'email'
          AND non_unique = 0 AND index_name <> 'PRIMARY'
        GROUP BY index_name
        HAVING COUNT(*) = 1 AND MAX(column_name = 'message_id') = 1
    ) email_unique_indexes
);
SET @email_msg_drop_sql = IF(@email_msg_unique_drops IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE email ', @email_msg_unique_drops));
PREPARE email_msg_drop_stmt FROM @email_msg_drop_sql;
EXECUTE email_msg_drop_stmt;
DEALLOCATE PREPARE email_msg_drop_stmt;

SET @email_konto_msg_index = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'email'
      AND index_name = 'idx_email_konto_message_id');
SET @email_konto_msg_sql = IF(@email_konto_msg_index = 0,
    'CREATE UNIQUE INDEX idx_email_konto_message_id ON email (konto_id, message_id)', 'SELECT 1');
PREPARE email_konto_msg_stmt FROM @email_konto_msg_sql;
EXECUTE email_konto_msg_stmt;
DEALLOCATE PREPARE email_konto_msg_stmt;

CREATE TABLE IF NOT EXISTS email_import_identitaet (
    id BIGINT NOT NULL AUTO_INCREMENT,
    konto_id VARCHAR(16) NOT NULL,
    folder VARCHAR(255) NOT NULL,
    uidvalidity BIGINT NOT NULL,
    uid BIGINT NOT NULL,
    pruefkonflikt BOOLEAN NOT NULL DEFAULT FALSE,
    email_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_email_import_identitaet_uid UNIQUE (konto_id, folder, uidvalidity, uid),
    INDEX idx_email_import_identitaet_email (email_id),
    CONSTRAINT fk_email_import_identitaet_email FOREIGN KEY (email_id)
        REFERENCES email (id) ON DELETE CASCADE
) ENGINE=InnoDB;
