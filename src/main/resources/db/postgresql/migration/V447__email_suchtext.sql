-- E-Mail-Suche: ein normalisierter Suchtext je Mail plus Trigramm-Index, damit „müller geländer“
-- auch bei vielen tausend Mails schnell alle Mails findet, in denen beide Wörter irgendwo vorkommen.
--
-- WAS
--   email.suchtext          Absender und Empfänger samt Anzeigenamen, Antwortadresse, CC, Betreff,
--                           Anhang-Dateinamen, Klartext (mit zitiertem Verlauf) und Text des HTML-Teils;
--                           klein geschrieben, ä/ö/ü/ß als a/o/u/ss, ohne Akzente (EmailSuchtext,
--                           EmailSuchNormalisierung). Höchstens 200.000 Zeichen. Gepflegt aus Java beim
--                           Speichern; NULL = noch nicht berechnet.
--   idx_email_suchtext_trgm GIN-Trigramm-Index (pg_trgm) für die Bedingungen suchtext LIKE '%wort%'.
--   idx_email_ohne_suchtext kleiner Teilindex auf die Mails ohne Suchtext - die Nachpflege
--                           (EmailSuchtextNachpflege) findet sie so ohne die ganze Tabelle zu lesen.
--
-- ALTBESTAND
--   Bestehende Mails haben nach dieser Migration noch keinen Suchtext. Die Nachpflege füllt ihn nach
--   dem Start im Hintergrund in Blöcken (dieselbe Java-Normalisierung wie beim Speichern). Bis dahin
--   fehlen diese Mails in der Suche. Hier wird bewusst nichts per SQL vorbefüllt: die Normalisierung
--   (HTML-Text, Umlaute, Akzente) gibt es nur in Java, und ein UPDATE über alle Mails würde den Start
--   lange blockieren.
--
-- ROBUSTHEIT
--   ADD COLUMN / CREATE ... IF NOT EXISTS - ein zweiter Lauf ändert nichts.
--   Darf der Datenbank-Benutzer die Erweiterung pg_trgm nicht anlegen (fehlende Rechte), läuft die
--   Migration trotzdem durch: Der DO-Block fängt den Fehler ab und meldet ihn nur (NOTICE). Die Suche
--   funktioniert dann genauso, nur ohne Index und deshalb langsamer. Ein Admin kann später
--   `CREATE EXTENSION pg_trgm;` ausführen und den Index mit dem CREATE INDEX aus dem DO-Block von Hand nachlegen.

ALTER TABLE email ADD COLUMN IF NOT EXISTS suchtext TEXT;

CREATE INDEX IF NOT EXISTS idx_email_ohne_suchtext ON email (id) WHERE suchtext IS NULL;

DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
    CREATE INDEX IF NOT EXISTS idx_email_suchtext_trgm ON email USING gin (suchtext gin_trgm_ops);
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'E-Mail-Suche ohne Trigramm-Index (pg_trgm nicht verfuegbar: %). Die Suche funktioniert, ist aber langsamer.', SQLERRM;
END
$$;
