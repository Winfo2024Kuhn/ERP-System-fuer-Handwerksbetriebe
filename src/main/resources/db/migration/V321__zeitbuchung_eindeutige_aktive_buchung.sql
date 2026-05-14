-- ============================================================
-- V321: Genau EINE aktive Buchung pro Mitarbeiter (DB-Garantie)
-- ============================================================
-- Bisheriges Problem: Zwei parallele Start-Requests des gleichen Mitarbeiters
-- (z.B. Mobile-Sync + manueller Start, oder zwei Geräte) konnten BEIDE eine
-- neue Zeitbuchung mit ende_zeit IS NULL anlegen, weil die Existenzprüfung
-- nicht atomar war. Folge: "Geisterbuchungen" und korruptes Tagesjournal.
--
-- Lösung: Generated Column 'aktiver_mitarbeiter' enthält die mitarbeiter_id
-- nur solange ende_zeit IS NULL ist, sonst NULL. Ein UNIQUE INDEX darauf
-- erlaubt mehrere NULL-Werte (= viele abgeschlossene Buchungen), aber
-- maximal eine Zeile pro Mitarbeiter mit gesetztem Wert (= aktive Buchung).
--
-- Zusammen mit dem Pessimistic Lock auf der Mitarbeiter-Zeile in
-- ZeiterfassungApiService bildet das eine Belt-&-Suspenders-Garantie:
--  - Anwendungs-Layer: SELECT ... FOR UPDATE serialisiert pro Mitarbeiter.
--  - DB-Layer: Constraint verhindert auch bei App-Bugs/Doppel-Connections
--    das Anlegen einer zweiten aktiven Buchung (DataIntegrityViolation).
-- ============================================================

-- 1) Pre-Check: KEIN automatisches Cleanup vorhandener Mehrfach-Aktivbuchungen!
--    Begründung (GoBD): Eine ungefragte UPDATE auf Zeitbuchungen erzeugt ohne
--    Audit-Trail-Eintrag eine Lücke im Änderungsprotokoll. Da
--    'zeitbuchung_audit.geaendert_von_mitarbeiter_id' NOT NULL ist und es im
--    System keinen technischen System-Mitarbeiter gibt, kann die Migration
--    keinen gültigen Audit-Eintrag schreiben.
--
--    Sollte die Migration bei der Index-Anlage unter (2) wegen vorhandener
--    Duplikate fehlschlagen, müssen diese vorab manuell und auditiert
--    korrigiert werden (z.B. per Backoffice-Bearbeitung via
--    ZeitverwaltungController#aendereBuchung, das den Audit-Trail korrekt
--    befüllt). Diagnose-Query für den Operator:
--      SELECT mitarbeiter_id, COUNT(*)
--        FROM zeitbuchung
--       WHERE ende_zeit IS NULL AND mitarbeiter_id IS NOT NULL
--       GROUP BY mitarbeiter_id
--      HAVING COUNT(*) > 1;

-- 2) Generated Column: mitarbeiter_id nur solange Buchung aktiv ist.
--    Idempotenz: Spalte/Index nur anlegen, wenn noch nicht vorhanden.
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'zeitbuchung'
      AND COLUMN_NAME = 'aktiver_mitarbeiter'
);
SET @add_col_sql := IF(@col_exists = 0,
    'ALTER TABLE zeitbuchung ADD COLUMN aktiver_mitarbeiter BIGINT GENERATED ALWAYS AS (CASE WHEN ende_zeit IS NULL THEN mitarbeiter_id ELSE NULL END) VIRTUAL',
    'SELECT 1');
PREPARE stmt FROM @add_col_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) Unique Index: max. eine Zeile pro Mitarbeiter mit aktiver_mitarbeiter != NULL.
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'zeitbuchung'
      AND INDEX_NAME = 'uk_zeitbuchung_aktiv_pro_mitarbeiter'
);
SET @add_idx_sql := IF(@idx_exists = 0,
    'ALTER TABLE zeitbuchung ADD UNIQUE INDEX uk_zeitbuchung_aktiv_pro_mitarbeiter (aktiver_mitarbeiter)',
    'SELECT 1');
PREPARE stmt FROM @add_idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
