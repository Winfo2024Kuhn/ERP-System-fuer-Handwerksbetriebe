-- Erweitert subject/recipient/cc in email_draft auf TEXT, weil eingehende
-- Nachrichten teils Betreff/Empfaenger-Listen >1000 Zeichen liefern
-- (lange Reply-Ketten "AW: AW: AW: ..." oder Mailinglisten mit vielen Empfaengern).
-- Vorher: VARCHAR(1000) -> "Data truncation: Data too long for column 'subject'".
--
-- Idempotent: pruefen via INFORMATION_SCHEMA, ob die Spalte bereits TEXT ist.

-- subject
SET @subject_is_varchar = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'email_draft'
      AND COLUMN_NAME  = 'subject'
      AND DATA_TYPE    = 'varchar'
);
SET @alter_subject = IF(@subject_is_varchar = 1,
    'ALTER TABLE email_draft MODIFY COLUMN subject TEXT',
    'SELECT 1'
);
PREPARE stmt_subject FROM @alter_subject;
EXECUTE stmt_subject;
DEALLOCATE PREPARE stmt_subject;

-- recipient
SET @recipient_is_varchar = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'email_draft'
      AND COLUMN_NAME  = 'recipient'
      AND DATA_TYPE    = 'varchar'
);
SET @alter_recipient = IF(@recipient_is_varchar = 1,
    'ALTER TABLE email_draft MODIFY COLUMN recipient TEXT',
    'SELECT 1'
);
PREPARE stmt_recipient FROM @alter_recipient;
EXECUTE stmt_recipient;
DEALLOCATE PREPARE stmt_recipient;

-- cc
SET @cc_is_varchar = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'email_draft'
      AND COLUMN_NAME  = 'cc'
      AND DATA_TYPE    = 'varchar'
);
SET @alter_cc = IF(@cc_is_varchar = 1,
    'ALTER TABLE email_draft MODIFY COLUMN cc TEXT',
    'SELECT 1'
);
PREPARE stmt_cc FROM @alter_cc;
EXECUTE stmt_cc;
DEALLOCATE PREPARE stmt_cc;
