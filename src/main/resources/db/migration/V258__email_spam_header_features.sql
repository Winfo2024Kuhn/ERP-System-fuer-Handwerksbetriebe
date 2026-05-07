-- Spam-Filter Erweiterung: zusaetzliche Header-Felder fuer strukturelle Features.
-- Ziel ist es, gut formulierten Spam (KI-generierte Cold-Mails, Branchenbuch-Maschen)
-- ueber Header-Signale (Reply-To, SPF/DKIM/DMARC) zu erkennen, nicht nur ueber Tokens.
--
-- reply_to_address:        Reply-To Header (oft abweichend bei Phishing/Spam)
-- authentication_results:  Roh-Header "Authentication-Results" (SPF, DKIM, DMARC);
--                          kompakt geparst im SpamFilterService.
--
-- Idempotent (Pattern wie V256), damit Re-Runs nicht scheitern.

-- 1. reply_to_address
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'email'
      AND COLUMN_NAME  = 'reply_to_address'
);
SET @add_col = IF(@col_exists = 0,
    'ALTER TABLE email ADD COLUMN reply_to_address VARCHAR(255) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @add_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. authentication_results
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'email'
      AND COLUMN_NAME  = 'authentication_results'
);
SET @add_col = IF(@col_exists = 0,
    'ALTER TABLE email ADD COLUMN authentication_results TEXT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @add_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
