-- Bearbeitbare Rechtstexte mit Fassungen (zuerst: Widerrufsbelehrung).
--
-- WARUM
-- Die Widerrufsbelehrung geht nach jeder Online-Annahme als PDF mit der Auftragsbestätigung an
-- den Kunden. Den Text pflegt der Betrieb jetzt selbst im Formularwesen. Jedes Speichern legt eine
-- neue Fassung an, alte Fassungen werden nie überschrieben: Im Streitfall muss belegbar sein,
-- welcher Wortlaut bei einer bestimmten Annahme verschickt wurde.
--
-- WAS
--   rechtstext_fassung          eine Zeile je gespeicherter Fassung (art + fassung eindeutig).
--     art                       Java-Enum RechtstextArt (z. B. WIDERRUFSBELEHRUNG), ohne CHECK.
--     fassung                   laufende Nummer je Art, beginnt bei 1.
--     stand                     Datum der Fassung (Speichertag), steht im PDF als „Stand“.
--     inhalt_html               bereinigtes HTML (nur p, br, strong, b, em, i, h3, ul, ol, li).
--   dokument_freigabe
--     widerruf_versand_stand    Stand der Belehrung, die das ERP mit der AB verschickt hat.
--     widerruf_versand_fassung  deren Fassungsnummer (0 = mitgelieferter Standardtext).
--
-- ROBUSTHEIT
-- Nur IF NOT EXISTS - ein zweiter Lauf ändert nichts. Ohne gespeicherte Fassung gilt der
-- mitgelieferte Standardtext; bestehende Annahmen bleiben NULL.

CREATE TABLE IF NOT EXISTS rechtstext_fassung (
    id BIGSERIAL PRIMARY KEY,
    art VARCHAR(40) NOT NULL,
    fassung INTEGER NOT NULL,
    stand DATE NOT NULL,
    inhalt_html TEXT NOT NULL,
    geaendert_am TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_rechtstext_fassung_art_fassung UNIQUE (art, fassung)
);

ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS widerruf_versand_stand VARCHAR(20);
ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS widerruf_versand_fassung INTEGER;
