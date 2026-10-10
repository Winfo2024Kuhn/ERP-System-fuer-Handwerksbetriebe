-- ---------------------------------------------------------------------------
-- Welche Stunden gehen als "Arbeit" an das Steuerbuero: Soll oder Ist?
--
-- datev_konfiguration.stundenart  : Vorgabe fuer den ganzen Betrieb.
-- datev_personalnummer.stundenart : Abweichung je Mitarbeiter, NULL = wie Betrieb.
--
-- Neue Installationen starten mit SOLL. Wer DATEV schon eingerichtet hat
-- (Beraternummer gesetzt), behaelt IST - so hat der Export bisher gerechnet,
-- ein Update darf die Lohndaten nicht still veraendern.
--
-- Idempotent: Spalte erst anlegen, wenn sie fehlt; das UPDATE trifft nur noch
-- leere Werte. Zwilling: db/postgresql/migration/V412__datev_stundenart.sql
-- ---------------------------------------------------------------------------

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'datev_konfiguration' AND column_name = 'stundenart');
SET @sql := IF(@c = 0,
    'ALTER TABLE datev_konfiguration ADD COLUMN stundenart VARCHAR(4) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE datev_konfiguration
SET stundenart = CASE WHEN berater_nr <> '' THEN 'IST' ELSE 'SOLL' END
WHERE stundenart IS NULL;

ALTER TABLE datev_konfiguration MODIFY COLUMN stundenart VARCHAR(4) NOT NULL DEFAULT 'SOLL';

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'datev_personalnummer' AND column_name = 'stundenart');
SET @sql := IF(@c = 0,
    'ALTER TABLE datev_personalnummer ADD COLUMN stundenart VARCHAR(4) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
