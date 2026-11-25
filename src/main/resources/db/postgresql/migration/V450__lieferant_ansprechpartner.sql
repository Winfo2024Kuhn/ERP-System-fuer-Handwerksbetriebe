-- Mehrere Ansprechpartner je Lieferant (1:n) statt des einen Textfelds "Vertreter".
--
-- Neue Tabelle lieferant_ansprechpartner: Anrede, Vorname, Nachname (Pflicht), Aufgabe
-- (freier Text wie "Außendienst", "Innendienst", "Buchhaltung"), Telefon, Handy, E-Mail, Notiz.
-- Gepflegt im Reiter "Ansprechpartner" der Lieferanten-Detailseite
-- (/api/lieferanten/{id}/ansprechpartner). Telefon und Handy kennen Anruferkennung und
-- FRITZ!Box-Telefonbuch. Wird der Lieferant gelöscht, löscht die Datenbank seine
-- Ansprechpartner mit (ON DELETE CASCADE, DSGVO).
--
-- Datenübernahme: Für jeden Lieferanten mit ausgefülltem "vertreter" entsteht ein
-- Ansprechpartner mit nachname = trim(vertreter) und aufgabe = 'Vertreter'. Danach wird
-- die Spalte lieferanten.vertreter gelöscht.
--
-- Idempotent: Tabelle und Index mit IF NOT EXISTS; die Übernahme läuft nur, solange die
-- Spalte vertreter noch existiert, und legt keinen Ansprechpartner doppelt an (gleicher
-- Lieferant, gleicher Nachname). Ein zweiter Lauf ändert nichts.

CREATE TABLE IF NOT EXISTS lieferant_ansprechpartner (
    id BIGSERIAL PRIMARY KEY,
    lieferant_id BIGINT NOT NULL REFERENCES lieferanten (id) ON DELETE CASCADE,
    anrede VARCHAR(32),
    vorname VARCHAR(100),
    nachname VARCHAR(255) NOT NULL,
    aufgabe VARCHAR(100),
    telefon VARCHAR(50),
    mobiltelefon VARCHAR(50),
    email VARCHAR(255),
    notiz VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_lieferant_ansprechpartner_lieferant
    ON lieferant_ansprechpartner (lieferant_id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema()
                 AND table_name = 'lieferanten'
                 AND column_name = 'vertreter') THEN
        EXECUTE $sql$
            INSERT INTO lieferant_ansprechpartner (lieferant_id, nachname, aufgabe)
            SELECT l.id, trim(l.vertreter), 'Vertreter'
            FROM lieferanten l
            WHERE l.vertreter IS NOT NULL
              AND trim(l.vertreter) <> ''
              AND NOT EXISTS (SELECT 1 FROM lieferant_ansprechpartner a
                              WHERE a.lieferant_id = l.id
                                AND a.nachname = trim(l.vertreter))
        $sql$;
    END IF;
END $$;

ALTER TABLE lieferanten DROP COLUMN IF EXISTS vertreter;
