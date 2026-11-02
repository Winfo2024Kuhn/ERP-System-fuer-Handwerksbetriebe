-- Firmendokumente: Ablage für Firmen-PDFs (Torprüfung, Gefährdungsbeurteilung, Versicherungen …)
-- in einer Ordner-Baumstruktur unter Stammdaten. Sichtbar für jeden angemeldeten PC-Nutzer.
--
-- WAS
--   firmendokument_ordner   Ordnerbaum (parent_id NULL = oberste Ebene). Ein Ordner mit Inhalt
--                           lässt sich nicht löschen (RESTRICT), damit nichts mitverschwindet.
--   firmendokument          ein PDF: frei gewählter Name, Original-Dateiname (nur zur Anzeige),
--                           gespeicherter Dateiname (UUID.pdf unter uploads/firmendokumente/),
--                           optionales Fälligkeitsdatum (z. B. nächste Torprüfung).
--   Startordner             fünf Ordner, aber nur, wenn es noch gar keine gibt.
--
-- ROBUSTHEIT
-- CREATE TABLE / INDEX IF NOT EXISTS, Startordner nur bei leerer Tabelle - ein zweiter Lauf ändert nichts.

CREATE TABLE IF NOT EXISTS firmendokument_ordner (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    parent_id   BIGINT       REFERENCES firmendokument_ordner (id) ON DELETE RESTRICT,
    angelegt_am TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_firmendokument_ordner_parent ON firmendokument_ordner (parent_id);

CREATE TABLE IF NOT EXISTS firmendokument (
    id                      BIGSERIAL    PRIMARY KEY,
    ordner_id               BIGINT       NOT NULL REFERENCES firmendokument_ordner (id) ON DELETE RESTRICT,
    name                    VARCHAR(200) NOT NULL,
    original_dateiname      VARCHAR(255) NOT NULL,
    gespeicherter_dateiname VARCHAR(60)  NOT NULL UNIQUE,
    groesse_bytes           BIGINT       NOT NULL,
    hochgeladen_am          TIMESTAMP    NOT NULL,
    hochgeladen_von         VARCHAR(255),
    faellig_am              DATE,
    version                 BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_firmendokument_ordner ON firmendokument (ordner_id);
CREATE INDEX IF NOT EXISTS idx_firmendokument_faellig ON firmendokument (faellig_am);

INSERT INTO firmendokument_ordner (name)
SELECT startordner.name
FROM (VALUES ('Arbeitsschutz'), ('Prüfungen'), ('Versicherungen'), ('Verträge'), ('Zertifikate & Nachweise'))
     AS startordner (name)
WHERE NOT EXISTS (SELECT 1 FROM firmendokument_ordner);
