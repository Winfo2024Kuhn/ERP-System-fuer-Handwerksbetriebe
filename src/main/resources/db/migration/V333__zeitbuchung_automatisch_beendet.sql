-- Kennzeichnet Zeitbuchungen, die der ZeitbuchungAutoStopService automatisch
-- beendet hat, weil der Mitarbeiter nicht abgestochen hat.
--
-- Hintergrund: Der Auto-Stop schreibt die konfigurierte buchungEndeZeit (Default
-- 20:00) in die Buchung. Das ist eine geschaetzte Zeit, keine erfasste - sie muss
-- geprueft werden. Ohne Kennzeichen war sie von einer echten Stempelung nicht zu
-- unterscheiden und floss unbemerkt in den Monatssaldo.
--
-- Das Flag hat zwei Aufgaben:
--   1. Anzeige in der Benachrichtigungs-Glocke ("bitte pruefen").
--   2. Freigabe fuer den Nachtrag: Ein verspaetet eintreffender Feierabend-Stop
--      vom Handy darf eine so markierte Buchung noch auf die echte Zeit
--      korrigieren (siehe ZeiterfassungApiService#stopZeiterfassung).
--
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich.

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'zeitbuchung'
      AND column_name = 'automatisch_beendet');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE zeitbuchung ADD COLUMN automatisch_beendet BIT(1) NOT NULL DEFAULT b''0''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Backfill: Bestandsbuchungen, die vom Auto-Stop beendet und seitdem NICHT
-- korrigiert wurden, tragen weiterhin eine geschaetzte Endezeit. Erkennbar
-- daran, dass die aktuelle ende_zeit exakt der vom SYSTEM geschriebenen
-- Endezeit im Audit-Trail entspricht. Bereits per Hand korrigierte Buchungen
-- (abweichende ende_zeit) bleiben unmarkiert.
UPDATE zeitbuchung z
INNER JOIN zeitbuchung_audit a
        ON a.zeitbuchung_id = z.id
       AND a.geaendert_via = 'SYSTEM'
       AND a.ende_zeit = z.ende_zeit
SET z.automatisch_beendet = b'1'
WHERE z.automatisch_beendet = b'0';

-- Index fuer "offene Prueffaelle" in der Benachrichtigungs-Glocke.
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'zeitbuchung'
      AND index_name = 'idx_zeitbuchung_automatisch_beendet');
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_zeitbuchung_automatisch_beendet ON zeitbuchung (automatisch_beendet, start_zeit)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
