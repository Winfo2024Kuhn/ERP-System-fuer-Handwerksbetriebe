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
-- Idempotent: das UPDATE trifft nur noch leere Werte.
-- Zwilling: db/migration/V412__datev_stundenart.sql
-- ---------------------------------------------------------------------------

ALTER TABLE datev_konfiguration ADD COLUMN IF NOT EXISTS stundenart VARCHAR(4);

UPDATE datev_konfiguration
SET stundenart = CASE WHEN berater_nr <> '' THEN 'IST' ELSE 'SOLL' END
WHERE stundenart IS NULL;

ALTER TABLE datev_konfiguration ALTER COLUMN stundenart SET DEFAULT 'SOLL';
ALTER TABLE datev_konfiguration ALTER COLUMN stundenart SET NOT NULL;

ALTER TABLE datev_personalnummer ADD COLUMN IF NOT EXISTS stundenart VARCHAR(4);

-- Nur SOLL oder IST (beim Mitarbeiter zusaetzlich leer = wie Betrieb). Erst entfernen,
-- dann anlegen, damit ein zweiter Lauf nicht scheitert.
ALTER TABLE datev_konfiguration DROP CONSTRAINT IF EXISTS ck_datev_konfiguration_stundenart;
ALTER TABLE datev_konfiguration ADD CONSTRAINT ck_datev_konfiguration_stundenart CHECK (stundenart IN ('SOLL', 'IST'));
ALTER TABLE datev_personalnummer DROP CONSTRAINT IF EXISTS ck_datev_personalnummer_stundenart;
ALTER TABLE datev_personalnummer ADD CONSTRAINT ck_datev_personalnummer_stundenart CHECK (stundenart IN ('SOLL', 'IST'));
