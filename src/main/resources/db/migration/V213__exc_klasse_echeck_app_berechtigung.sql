-- V213: EXC-Klasse für Projekte (EN 1090 EXC 1-4) + E-Check App-Berechtigung pro Abteilung

-- EXC-Klasse am Projekt (optional, NULL = kein EN 1090 Projekt)
ALTER TABLE projekt ADD COLUMN IF NOT EXISTS exc_klasse VARCHAR(10) NULL;

-- E-Check App-Freigabe pro Abteilung
-- Wenn TRUE: Mitarbeiter dieser Abteilung sehen in der Zeiterfassungs-App den E-Check Menüpunkt
ALTER TABLE abteilung ADD COLUMN IF NOT EXISTS darf_echeck_app BOOLEAN NOT NULL DEFAULT FALSE;
