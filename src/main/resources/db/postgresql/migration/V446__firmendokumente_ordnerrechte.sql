-- Firmendokumente: Rechte je Abteilung an den obersten Ordnern (z. B. „Versicherungen“: Büro darf
-- ändern, Werkstatt nur ansehen, alle anderen sehen den Ordner nicht). Unterordner und Dokumente
-- erben die Rechte ihres obersten Ordners. Admins sehen und ändern immer alles.
--
-- WAS
--   firmendokument_ordner.rechte_eingeschraenkt
--                           nur am obersten Ordner von Bedeutung. FALSE (Standard, wie bisher) = alle
--                           angemeldeten Benutzer sehen und ändern den Zweig. TRUE = nur die
--                           eingetragenen Abteilungen (und Admins).
--   firmendokument_ordner_recht
--                           je oberstem Ordner und Abteilung eine Stufe: ANSEHEN oder AENDERN
--                           (Java-Enum FirmendokumentZugriffsStufe, deshalb VARCHAR ohne CHECK-Werteliste).
--                           Kein Eintrag = die Abteilung sieht den Zweig nicht. Wird der Ordner oder die
--                           Abteilung gelöscht, verschwinden die Einträge mit (ON DELETE CASCADE).
--   Dateien                 ab jetzt alle Formate (außer Programmdateien); neue Dateien liegen als
--                           <UUID>.bin unter uploads/firmendokumente/, alte <UUID>.pdf bleiben gültig.
--                           Dafür ändert sich am Schema nichts (gespeicherter_dateiname VARCHAR(60) passt).
--
-- ROBUSTHEIT
-- ADD COLUMN / CREATE TABLE / INDEX IF NOT EXISTS - ein zweiter Lauf ändert nichts. Bestehende Ordner
-- bleiben uneingeschränkt (DEFAULT FALSE), es ändert sich also für niemanden etwas, bis ein Admin
-- einen Ordner einschränkt.

ALTER TABLE firmendokument_ordner ADD COLUMN IF NOT EXISTS rechte_eingeschraenkt BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS firmendokument_ordner_recht (
    id           BIGSERIAL   PRIMARY KEY,
    ordner_id    BIGINT      NOT NULL REFERENCES firmendokument_ordner (id) ON DELETE CASCADE,
    abteilung_id BIGINT      NOT NULL REFERENCES abteilung (id) ON DELETE CASCADE,
    stufe        VARCHAR(20) NOT NULL,
    CONSTRAINT uk_firmendokument_ordner_recht UNIQUE (ordner_id, abteilung_id)
);

CREATE INDEX IF NOT EXISTS idx_firmendokument_ordner_recht_abteilung ON firmendokument_ordner_recht (abteilung_id);
