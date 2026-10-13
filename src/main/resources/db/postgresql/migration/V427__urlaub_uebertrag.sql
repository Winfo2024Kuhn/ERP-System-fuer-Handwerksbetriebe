-- Resturlaub ins neue Jahr übertragen (Issue #270, Punkt 4).
--
-- WARUM
-- Bisher begann jedes Jahr mit dem vollen Anspruch; nicht genommener Urlaub ging
-- stillschweigend verloren. Der Rest des Vorjahres wird jetzt aus den gebuchten
-- Urlaubstagen berechnet (keine gespeicherte Kopie, die veralten kann). Gespeichert
-- wird nur, was das Büro von Hand festlegt.
--
-- WAS
--   urlaub_uebertrag            von Hand festgelegter Übertrag je Mitarbeiter und Jahr
--     jahr                      Jahr, in das der Rest übertragen wird
--     tage                      Tage aus dem Vorjahr, halbe Tage erlaubt (NUMERIC(5,1))
--   system_setting
--     urlaub.uebertrag.ab-jahr  erstes Jahr, dessen Rest automatisch weitergegeben wird.
--                               Davor fehlen oft die Urlaubsdaten; ohne Grenze bekäme jeder
--                               Mitarbeiter einen Übertrag aus Jahren ohne Erfassung.
--   mitarbeiter.resturlaub_vorjahr
--                               wurde nie beschrieben (ganze Tage, ohne Jahr). Vorhandene Werte
--                               wandern als Übertrag ins laufende Jahr, danach entfällt die Spalte.
--
-- ROBUSTHEIT
-- IF NOT EXISTS, ON CONFLICT DO NOTHING und eine Prüfung auf die alte Spalte - ein zweiter Lauf
-- ändert nichts.

CREATE TABLE IF NOT EXISTS urlaub_uebertrag (
    id BIGSERIAL PRIMARY KEY,
    mitarbeiter_id BIGINT NOT NULL REFERENCES mitarbeiter (id) ON DELETE CASCADE,
    jahr INTEGER NOT NULL,
    tage NUMERIC(5, 1) NOT NULL,
    geaendert_am TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_urlaub_uebertrag_mitarbeiter_jahr UNIQUE (mitarbeiter_id, jahr)
);

INSERT INTO system_setting (setting_key, setting_value, beschreibung)
VALUES ('urlaub.uebertrag.ab-jahr', CAST(EXTRACT(YEAR FROM CURRENT_DATE) AS INTEGER)::TEXT,
        'Ab diesem Jahr wird der Resturlaub automatisch ins Folgejahr übertragen')
ON CONFLICT (setting_key) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema()
                 AND table_name = 'mitarbeiter'
                 AND column_name = 'resturlaub_vorjahr') THEN
        INSERT INTO urlaub_uebertrag (mitarbeiter_id, jahr, tage)
        SELECT id, CAST(EXTRACT(YEAR FROM CURRENT_DATE) AS INTEGER), resturlaub_vorjahr
        FROM mitarbeiter
        WHERE resturlaub_vorjahr IS NOT NULL AND resturlaub_vorjahr > 0
        ON CONFLICT (mitarbeiter_id, jahr) DO NOTHING;
        ALTER TABLE mitarbeiter DROP COLUMN resturlaub_vorjahr;
    END IF;
END $$;
