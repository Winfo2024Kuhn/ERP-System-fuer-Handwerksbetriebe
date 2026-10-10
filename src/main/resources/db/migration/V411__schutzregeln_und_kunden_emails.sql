-- V411 (MySQL): Kunden-E-Mails mehrfach erlaubt.
--
-- Dieselbe E-Mail-Adresse darf bei mehreren Kunden stehen, z. B. eine
-- Hausverwaltung fuer mehrere WEGs. Bisher war kunden_emails.email eindeutig
-- (Hibernate-Index UK_rbeo8g0h6kse6liqmbrdidtfj), jetzt ein normaler Index.
-- Kunde.kundenEmails traegt kein unique mehr.
--
-- Die Schutzregeln "eine laufende Zeitbuchung / eine offene Pause pro
-- Mitarbeiter" hat MySQL schon (V321, V370); die PostgreSQL-Fassung von V411
-- zieht sie dort nach.
--
-- Idempotent: Auf Servern, deren Index schon von Hand gelockert wurde, bleibt
-- er, wie er ist.

-- Eindeutigen Index nur auf email entfernen (Name je nach Hibernate-Stand)
SET @uk_name := (
    SELECT INDEX_NAME FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kunden_emails'
      AND NON_UNIQUE = 0
      AND INDEX_NAME <> 'PRIMARY'
    GROUP BY INDEX_NAME
    HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'email'
    LIMIT 1
);
SET @drop_sql := IF(@uk_name IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE kunden_emails DROP INDEX `', @uk_name, '`'));
PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Normaler Index fuer die Suche nach Kunden per E-Mail, falls noch keiner da ist
SET @idx_vorhanden := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kunden_emails'
      AND COLUMN_NAME = 'email'
      AND SEQ_IN_INDEX = 1
);
SET @add_sql := IF(@idx_vorhanden > 0,
    'SELECT 1',
    'ALTER TABLE kunden_emails ADD INDEX idx_kunden_emails_email (email)');
PREPARE stmt FROM @add_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
